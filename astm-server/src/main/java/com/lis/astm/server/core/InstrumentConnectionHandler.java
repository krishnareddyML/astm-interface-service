package com.lis.astm.server.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.astm.model.AstmMessage;
import com.lis.astm.server.driver.InstrumentDriver;
import com.lis.astm.server.messaging.ResultQueuePublisher;
import com.lis.astm.server.model.OrderMessage;
import com.lis.astm.server.model.ServerMessage;
import com.lis.astm.server.protocol.ASTMProtocolStateMachine;
import com.lis.astm.server.service.ServerMessageService;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.springframework.beans.factory.annotation.Value;

/**
 * A modern, elegant, and non-blocking handler for a single instrument connection.
 * This class runs a single-threaded event loop to manage all I/O,
 * eliminating the deadlocks and race conditions of the previous design.
 */
@Slf4j
public class InstrumentConnectionHandler implements Runnable {

    private final Socket socket;
    private final InstrumentDriver driver;
    private final String instrumentName;
    private final ResultQueuePublisher resultPublisher;
    private final ServerMessageService serverMessageService;
    //private final OrderMessageService orderMessageService;
    private final ASTMProtocolStateMachine protocolStateMachine;
    //private final BlockingQueue<AstmMessage> outgoingMessageQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<OrderMessage> outgoingMessageQueue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;
    private final ObjectMapper objectMapper;

    @Value("${lis.messaging.retry.connection-delay-minutes:5}")
    private long connectionRetryDelayMinutes;

    public InstrumentConnectionHandler(Socket socket, InstrumentDriver driver, String instrumentName,
                                     ResultQueuePublisher resultPublisher, ServerMessageService serverMessageService,
                                     ObjectMapper objectMapper
                                    // OrderMessageService orderMessageService
                                     ) throws IOException {
        this.socket = socket;
        this.driver = driver;
        this.instrumentName = instrumentName;
        this.resultPublisher = resultPublisher;
        this.serverMessageService = serverMessageService;
        this.objectMapper = objectMapper;
       // this.orderMessageService = orderMessageService;
        this.protocolStateMachine = new ASTMProtocolStateMachine(socket, instrumentName);
    }
    
    // public void queueMessageForSending(AstmMessage message) {
    //     log.info("Queued outgoing message for instrument {} (queue size: {})", 
    //              instrumentName, outgoingMessageQueue.size() + 1);
    //     outgoingMessageQueue.offer(message);
    // }

     /**
     * The new NON-BLOCKING handoff method called by the OrderProcessingService.
     */
    public void queueOrderForSending(OrderMessage orderMessage) {
        log.info("Queued outgoing message for instrument {} (queue size: {})", instrumentName, outgoingMessageQueue.size() + 1);
        outgoingMessageQueue.offer(orderMessage);
    }

    @Override
    public void run() {
        log.info("Connection handler started for {} from {}", instrumentName, getRemoteAddress());
        try {
            while (running && isConnected()) {
                if (!outgoingMessageQueue.isEmpty() && protocolStateMachine.getCurrentState() == ASTMProtocolStateMachine.State.IDLE) {
                    OrderMessage orderMessage = outgoingMessageQueue.poll();
                    if (orderMessage != null) {
                        AstmMessage astmMessage = objectMapper.readValue(orderMessage.getMessageContent(), AstmMessage.class);
                        boolean success  = sendOrder(astmMessage);
                        if (success) {
                          //  orderMessageService.markAsSuccess(orderMessage.getId());
                        } else {
                            // The message send failed, it will be retried by the scheduler
                            //orderMessageService.scheduleRetry(orderMessage, connectionRetryDelayMinutes,"Send failed");
                        }
                    }
                }

                handleIncomingData();

                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Connection handler for {} was interrupted.", instrumentName);
        } catch (Exception e) {
            if (running) {
                log.error("Connection handler for {} ended with an error: {}", instrumentName, e.getMessage());
            }
        } finally {
            cleanup();
        }
    }

    // private void sendOrder(AstmMessage message) {
    //     log.debug("Dequeued order for instrument {}. Attempting to send.", instrumentName);
    //     try {
    //         String rawMessage = driver.build(message);
    //         if (protocolStateMachine.sendMessage(rawMessage)) {
    //             log.info("Successfully sent order to {}", instrumentName);
    //         } else {
    //             log.error("Failed to send order to {}. It will be retried by the service.", instrumentName);
    //         }
    //     } catch (Exception e) {
    //         log.error("Exception while sending message to {}: {}", instrumentName, e, e);
    //     }
    // }

    public boolean sendOrder(AstmMessage message) {
    log.debug("Attempting to send order directly to instrument {}.", instrumentName);
    try {
        String rawMessage = driver.build(message);
        if (protocolStateMachine.sendMessage(rawMessage)) {
            log.info("Successfully sent order to {}", instrumentName);
            return true;
        } else {
            log.error("Failed to send order to {}. State machine rejected send.", instrumentName);
            return false;
        }
    } catch (Exception e) {
        log.error("Exception while sending message to {}: {}", instrumentName, e.getMessage(), e);
        return false;
    }
}

    private void handleIncomingData() {
        try {
            String rawMessage = protocolStateMachine.receiveMessage();

            if (rawMessage != null && !rawMessage.isEmpty()) {
                //log.info("Received message of {} chars from {}", rawMessage.length(), instrumentName);
                String messageType = serverMessageService.determineMessageType(rawMessage);
                log.info("Detected Message Type: {}", messageType);
                if ("KEEP_ALIVE".equals(messageType)) {
                    log.info("Keep-alive received from {}. Connection is active.", instrumentName);
                    return;
                }

                ServerMessage savedMessage = serverMessageService.saveIncomingMessage(rawMessage, instrumentName, getRemoteAddress(), messageType);
                AstmMessage parsedMessage = driver.parse(rawMessage);
                
                if (parsedMessage != null && (parsedMessage.hasResults() || parsedMessage.hasQueries())) {
                    Long messageId=0L;
                    try{

                        if(savedMessage!=null){
                        messageId = savedMessage.getId();
                        String parsedJsonMessage = this.objectMapper.writeValueAsString(parsedMessage);
                        serverMessageService.markAsProcessed(messageId, parsedJsonMessage);
                        }

                    }catch(Exception e){
                        log.debug("Error while marking server message with Id: {} as processed in DB: {}",savedMessage.getId(), e.getMessage());
                        return;
                    }
                    
                    // Publishing to Server Message the Queue
                    resultPublisher.publishResult(messageId, parsedMessage);
                    
                }
            }
        } catch (SocketTimeoutException e) {
            log.debug("Socket timeout on {}. No incoming data. This is normal.", instrumentName);
        } catch (IOException e) {
            log.error("I/O error on connection for {}: {}. Closing connection.", instrumentName, e.getMessage());
            stop();
        } catch (Exception e) {
            log.error("Error processing incoming message from {}.", instrumentName, e);
        }
    }
    
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public boolean isBusy() {
        return protocolStateMachine.getCurrentState() != ASTMProtocolStateMachine.State.IDLE;
    }
    
    public void stop() {
        this.running = false;
    }

    private String getRemoteAddress() {
        return socket.getRemoteSocketAddress().toString();
    }

    private void cleanup() {
        running = false;
        log.info("Cleaning up and closing connection for {}.", instrumentName);
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            log.warn("Error while closing socket for {}: {}", instrumentName, e.getMessage());
        }
    }
}