package com.lis.astm.server.core;

import com.lis.astm.server.config.AppConfig;
import com.lis.astm.server.driver.InstrumentDriver;
import com.lis.astm.server.messaging.ResultQueuePublisher;
import com.lis.astm.server.service.ServerMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.*;

/**
 * A robust ASTM Server that manages instrument connections according to best practices.
 *
 * Real-world policy:
 * - One instrument == one TCP port == one ACTIVE ASTM session at a time.
 * - Additional inbound connections while a session is active are refused.
 *
 * Thread model:
 * - One listener thread per instrument (blocks on serverSocket.accept()).
 * - One handler task per ACTIVE connection (runs in an executor service).
 */
@Slf4j
@Component
public class ASTMServer {

    @Autowired private AppConfig appConfig;
    @Autowired private ResultQueuePublisher resultQueuePublisher;
    @Autowired private ServerMessageService serverMessageService;

    private ExecutorService connectionExecutor;
    private final Map<String, ServerSocket> serverSockets = new ConcurrentHashMap<>();
    private final Map<String, Thread> listenerThreads = new ConcurrentHashMap<>();
    private final Map<String, InstrumentConnectionHandler> activeConnections = new ConcurrentHashMap<>();
    private volatile boolean running = false;

    @PostConstruct
    public void startServer() {
        log.info("Starting ASTM Interface Server...");
        // A cached thread pool is efficient for this use case. It creates threads as needed
        // and reuses them, which is perfect for managing multiple, independent connections.
        connectionExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setName("ASTM-Conn-" + t.getId());
            t.setDaemon(false);
            return t;
        });
        running = true;

        for (AppConfig.InstrumentConfig cfg : appConfig.getInstruments()) {
            if (cfg.isEnabled()) {
                startInstrumentListener(cfg);
            }
        }
    }

    @PreDestroy
    public void stopServer() {
        log.info("Stopping ASTM Interface Server...");
        running = false;

        // 1. Interrupt listener threads.
        listenerThreads.values().forEach(Thread::interrupt);

        // 2. Close server sockets to unblock the accept() calls.
        serverSockets.values().forEach(this::safeClose);

        // 3. Stop all active connection handlers.
        activeConnections.values().forEach(InstrumentConnectionHandler::stop);

        // 4. Shut down the executor service.
        connectionExecutor.shutdown();
        try {
            if (!connectionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                connectionExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            connectionExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("ASTM Server stopped.");
    }

    private void startInstrumentListener(AppConfig.InstrumentConfig config) {
        final String instrumentName = config.getName();
        Thread listenerThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(config.getPort())) {
                serverSockets.put(instrumentName, serverSocket);
                // Set a timeout on accept() so the loop can periodically check the 'running' flag.
                serverSocket.setSoTimeout(2000); 
                log.info("✅ Listener started for '{}' on port {}", instrumentName, config.getPort());

                while (running && !Thread.currentThread().isInterrupted()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        handleNewConnection(clientSocket, config);
                    } catch (SocketTimeoutException e) {
                        // This is expected and allows the loop to continue checking the running flag.
                    } catch (IOException e) {
                        if (running) log.error("I/O error on listener for '{}': {}", instrumentName, e.getMessage());
                    }
                }
            } catch (Exception e) {
                if (running) log.error("Listener for '{}' failed and has stopped.", instrumentName, e);
            } finally {
                log.info("Listener for '{}' has terminated.", instrumentName);
            }
        });

        listenerThread.setName("ASTM-Listener-" + instrumentName);
        listenerThreads.put(instrumentName, listenerThread);
        listenerThread.start();
    }

    private void handleNewConnection(Socket clientSocket, AppConfig.InstrumentConfig config) {
        final String instrumentName = config.getName();
        log.info("Accepted connection from {} for '{}'", clientSocket.getRemoteSocketAddress(), instrumentName);

        // Enforce the "one active session per instrument" rule.
        InstrumentConnectionHandler existingHandler = activeConnections.get(instrumentName);
        if (existingHandler != null && existingHandler.isConnected()) {
            log.warn("Instrument '{}' is already busy with an active session. Refusing new connection from {}.",
                     instrumentName, clientSocket.getRemoteSocketAddress());
            safeClose(clientSocket);
            return;
        }

        try {
            // Set the read timeout for the connection handler's I/O operations.
            int timeoutMs = config.getConnectionTimeoutSeconds() * 1000;
            clientSocket.setSoTimeout(timeoutMs);

            // Create a fresh driver instance for each new session.
            InstrumentDriver driver = (InstrumentDriver) Class.forName(config.getDriverClassName()).getDeclaredConstructor().newInstance();
            
            InstrumentConnectionHandler handler = new InstrumentConnectionHandler(
                clientSocket, driver, instrumentName, resultQueuePublisher, serverMessageService);

            // Submit the handler to the executor and store a reference to it.
            connectionExecutor.submit(handler);
            activeConnections.put(instrumentName, handler);

        } catch (Exception e) {
            log.error("Failed to create and start connection handler for '{}': {}", instrumentName, e.getMessage(), e);
            safeClose(clientSocket);
        }
    }

    /**
     * Retrieves the active connection handler for a specific instrument.
     * This allows other services (like the Order service) to interact with the instrument.
     * @param instrumentName The name of the instrument.
     * @return The active handler, or null if no session is active.
     */
    public InstrumentConnectionHandler getConnectionHandler(String instrumentName) {
        InstrumentConnectionHandler handler = activeConnections.get(instrumentName);
        // Ensure the handler is still valid and connected before returning it.
        if (handler != null && handler.isConnected()) {
            return handler;
        } else if (handler != null) {
            // If the handler is not connected, it's a stale entry. Remove it.
            activeConnections.remove(instrumentName, handler);
        }
        return null;
    }

    private void safeClose(Closeable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (IOException e) {
                log.warn("Error closing resource: {}", e.getMessage());
            }
        }
    }
}