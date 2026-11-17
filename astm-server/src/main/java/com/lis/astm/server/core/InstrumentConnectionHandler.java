package com.lis.astm.server.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.astm.model.AstmMessage;
import com.lis.astm.server.driver.InstrumentDriver;
import com.lis.astm.server.messaging.ResultQueuePublisher;
import com.lis.astm.server.model.OrderMessage;
import com.lis.astm.server.model.ServerMessage;
import com.lis.astm.server.protocol.ASTMProtocolStateMachine;
import com.lis.astm.server.protocol.ASTMProtocolStateMachine.SendResult;
import com.lis.astm.server.service.ServerMessageService;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Handles a single instrument TCP session with a single-threaded event loop.
 *
 * <p>Each loop iteration first drains one queued outbound order (if the line is idle and no
 * contention back-off is active), then waits a short time for inbound data. The socket read
 * timeout is kept short ({@link #READ_POLL_TIMEOUT_MS}) so outbound orders are never delayed
 * by more than that while the line is idle.
 */
@Slf4j
public class InstrumentConnectionHandler implements Runnable {

    /** Socket read timeout while idle. Bounds the latency of outbound orders. */
    public static final int READ_POLL_TIMEOUT_MS = 500;

    /**
     * Callbacks for the persistence layer. Kept as an interface so the handler does not depend on
     * Spring beans and can be unit tested.
     */
    public interface OrderOutcomeListener {
        void onSent(OrderMessage order);
        void onFailed(OrderMessage order, String reason);
    }

    private final Socket socket;
    private final InstrumentDriver driver;
    private final String instrumentName;
    private final ResultQueuePublisher resultPublisher;
    private final ServerMessageService serverMessageService;
    private final ObjectMapper objectMapper;
    private final OrderOutcomeListener orderOutcomeListener;
    private final ASTMProtocolStateMachine protocolStateMachine;
    private final BlockingDeque<OrderMessage> outgoingMessageQueue = new LinkedBlockingDeque<>();
    private final Instant connectedAt = Instant.now();

    private volatile boolean running = true;
    private volatile Instant sendBlockedUntil = Instant.EPOCH;
    private volatile Instant lastActivity = Instant.now();

    public InstrumentConnectionHandler(Socket socket, InstrumentDriver driver, String instrumentName,
                                       ResultQueuePublisher resultPublisher, ServerMessageService serverMessageService,
                                       ObjectMapper objectMapper, OrderOutcomeListener orderOutcomeListener) throws IOException {
        this.socket = socket;
        this.driver = driver;
        this.instrumentName = instrumentName;
        this.resultPublisher = resultPublisher;
        this.serverMessageService = serverMessageService;
        this.objectMapper = objectMapper;
        this.orderOutcomeListener = orderOutcomeListener;
        this.protocolStateMachine = new ASTMProtocolStateMachine(socket, instrumentName);
    }

    /** Non-blocking hand-off: the event loop sends the order when the line is free. */
    public void queueOrderForSending(OrderMessage orderMessage) {
        outgoingMessageQueue.offer(orderMessage);
        log.info("[{}] Queued outgoing order {} (queue size: {})", instrumentName, orderMessage.getId(), outgoingMessageQueue.size());
    }

    @Override
    public void run() {
        log.info("[{}] Connection handler started for {}", instrumentName, getRemoteAddress());
        try {
            while (running && isConnected()) {
                sendNextQueuedOrder();
                handleIncomingData();
            }
        } catch (Exception e) {
            if (running) {
                log.error("[{}] Connection handler ended with an error: {}", instrumentName, e.getMessage(), e);
            }
        } finally {
            cleanup();
        }
    }

    private void sendNextQueuedOrder() throws IOException {
        if (outgoingMessageQueue.isEmpty()) return;
        if (protocolStateMachine.getCurrentState() != ASTMProtocolStateMachine.State.IDLE) return;
        if (Instant.now().isBefore(sendBlockedUntil)) return;
        // Give priority to an instrument that is already talking to us.
        if (protocolStateMachine.hasPendingInput()) return;

        OrderMessage orderMessage = outgoingMessageQueue.poll();
        if (orderMessage == null) return;

        SendResult result;
        try {
            AstmMessage astmMessage = objectMapper.readValue(orderMessage.getMessageContent(), AstmMessage.class);
            result = sendOrder(astmMessage);
        } catch (IOException e) {
            // Socket level failure: the connection is unusable. Requeue so the retry service picks it up.
            notifyFailed(orderMessage, "I/O error while sending: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[{}] Order {} could not be built or sent: {}", instrumentName, orderMessage.getId(), e.getMessage(), e);
            notifyFailed(orderMessage, "Build/send exception: " + e.getMessage());
            return;
        }

        lastActivity = Instant.now();
        switch (result) {
            case SENT:
                if (orderOutcomeListener != null) orderOutcomeListener.onSent(orderMessage);
                break;
            case CONTENTION:
                // Host must yield and wait 20 s before trying ENQ again (XN 4.2.2 (2)). Keep the order at the head.
                sendBlockedUntil = Instant.now().plusMillis(ASTMProtocolStateMachine.CONTENTION_BACKOFF_MS);
                outgoingMessageQueue.offerFirst(orderMessage);
                log.info("[{}] Order {} deferred for {} ms after ENQ contention.", instrumentName, orderMessage.getId(),
                        ASTMProtocolStateMachine.CONTENTION_BACKOFF_MS);
                break;
            case BUSY:
                outgoingMessageQueue.offerFirst(orderMessage);
                break;
            case FAILED:
            default:
                notifyFailed(orderMessage, "Instrument did not acknowledge the order");
                break;
        }
    }

    /**
     * Builds and sends one message on the line. Public so that tests and tooling can drive it.
     */
    public SendResult sendOrder(AstmMessage message) throws Exception {
        String rawMessage = driver.build(message);
        log.debug("[{}] Sending order:\n{}", instrumentName, ASTMProtocolStateMachine.toVisibleAscii(rawMessage));
        SendResult result = protocolStateMachine.sendMessage(rawMessage);
        if (result == SendResult.SENT) {
            log.info("[{}] Successfully sent order.", instrumentName);
        } else {
            log.warn("[{}] Order not sent: {}", instrumentName, result);
        }
        return result;
    }

    private void notifyFailed(OrderMessage orderMessage, String reason) {
        log.error("[{}] Order {} failed: {}", instrumentName, orderMessage.getId(), reason);
        if (orderOutcomeListener != null) orderOutcomeListener.onFailed(orderMessage, reason);
    }

    private void handleIncomingData() {
        try {
            String rawMessage = protocolStateMachine.receiveMessage();
            lastActivity = Instant.now();
            if (rawMessage == null || rawMessage.isEmpty()) return;

            String messageType = serverMessageService.determineMessageType(rawMessage);
            log.info("[{}] Received {} message ({} chars)", instrumentName, messageType, rawMessage.length());
            if ("KEEP_ALIVE".equals(messageType)) {
                return;
            }

            ServerMessage savedMessage = serverMessageService.saveIncomingMessage(rawMessage, instrumentName, getRemoteAddress(), messageType);
            AstmMessage parsedMessage = driver.parse(rawMessage);
            if (parsedMessage == null) return;
            parsedMessage.setInstrumentName(instrumentName);

            if (!(parsedMessage.hasResults() || parsedMessage.hasQueries())) {
                log.info("[{}] Message contains neither results nor queries; stored only.", instrumentName);
                return;
            }

            Long messageId = savedMessage != null ? savedMessage.getId() : 0L;
            try {
                if (savedMessage != null) {
                    serverMessageService.markAsProcessed(messageId, objectMapper.writeValueAsString(parsedMessage));
                }
            } catch (Exception e) {
                log.error("[{}] Could not mark server message {} as processed: {}", instrumentName, messageId, e.getMessage());
                return;
            }

            resultPublisher.publishResult(messageId, parsedMessage);
        } catch (SocketTimeoutException e) {
            // Idle poll elapsed: normal.
        } catch (IOException e) {
            if (running) {
                log.error("[{}] I/O error: {}. Closing connection.", instrumentName, e.getMessage());
            }
            stop();
        } catch (Exception e) {
            log.error("[{}] Error processing incoming message.", instrumentName, e);
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed() && running;
    }

    public boolean isBusy() {
        return protocolStateMachine.getCurrentState() != ASTMProtocolStateMachine.State.IDLE;
    }

    public int getQueuedOrderCount() {
        return outgoingMessageQueue.size();
    }

    public Duration getIdleTime() {
        return Duration.between(lastActivity, Instant.now());
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    /** Requests the loop to stop and closes the socket so a blocked read returns immediately. */
    public void stop() {
        this.running = false;
        closeSocket();
    }

    public String getRemoteAddress() {
        return socket.getRemoteSocketAddress() == null ? "unknown" : socket.getRemoteSocketAddress().toString();
    }

    private void cleanup() {
        running = false;
        closeSocket();
        // Anything still queued could not be delivered on this session; hand it back to the retry service.
        OrderMessage pending;
        while ((pending = outgoingMessageQueue.poll()) != null) {
            notifyFailed(pending, "Connection closed before the order could be sent");
        }
        log.info("[{}] Connection to {} closed.", instrumentName, getRemoteAddress());
    }

    private void closeSocket() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            log.warn("[{}] Error while closing socket: {}", instrumentName, e.getMessage());
        }
    }
}
