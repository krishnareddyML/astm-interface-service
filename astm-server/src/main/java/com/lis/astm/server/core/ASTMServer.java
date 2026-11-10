package com.lis.astm.server.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.astm.server.config.AppConfig;
import com.lis.astm.server.driver.InstrumentDriver;
import com.lis.astm.server.messaging.ResultQueuePublisher;
import com.lis.astm.server.service.ServerMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * TCP server that owns one listener per configured instrument.
 *
 * <p>Policy: one instrument equals one TCP port equals one active ASTM session. When a new
 * connection arrives for an instrument that already has a session, the old session is closed and
 * replaced. Analyzers reconnect after a reboot or a network drop without closing the old socket,
 * and refusing them would leave the interface dead until the service was restarted.
 */
@Slf4j
@Component
public class ASTMServer {

    private final AppConfig appConfig;
    private final ResultQueuePublisher resultQueuePublisher;
    private final ServerMessageService serverMessageService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<InstrumentConnectionHandler.OrderOutcomeListener> orderOutcomeListenerProvider;

    private ExecutorService connectionExecutor;
    private final Map<String, ServerSocket> serverSockets = new ConcurrentHashMap<>();
    private final Map<String, Thread> listenerThreads = new ConcurrentHashMap<>();
    private final Map<String, InstrumentConnectionHandler> activeConnections = new ConcurrentHashMap<>();
    private volatile boolean running = false;

    public ASTMServer(AppConfig appConfig, ResultQueuePublisher resultQueuePublisher,
                      ServerMessageService serverMessageService, ObjectMapper objectMapper,
                      ObjectProvider<InstrumentConnectionHandler.OrderOutcomeListener> orderOutcomeListenerProvider) {
        this.appConfig = appConfig;
        this.resultQueuePublisher = resultQueuePublisher;
        this.serverMessageService = serverMessageService;
        this.objectMapper = objectMapper;
        this.orderOutcomeListenerProvider = orderOutcomeListenerProvider;
    }

    @PostConstruct
    public void startServer() {
        log.info("Starting ASTM Interface Server...");
        connectionExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setName("ASTM-Conn-" + t.getId());
            t.setDaemon(false);
            return t;
        });
        running = true;

        if (appConfig.getInstruments() == null || appConfig.getInstruments().isEmpty()) {
            log.warn("No instruments configured under 'lis.instruments'. Nothing to listen on.");
            return;
        }
        for (AppConfig.InstrumentConfig cfg : appConfig.getInstruments()) {
            if (!cfg.isEnabled()) {
                log.info("Instrument '{}' is disabled; skipping listener.", cfg.getName());
                continue;
            }
            if (cfg.getDriverClassName() == null || cfg.getDriverClassName().trim().isEmpty()) {
                log.error("Instrument '{}' has no driverClassName configured; listener on port {} NOT started.", cfg.getName(), cfg.getPort());
                continue;
            }
            startInstrumentListener(cfg);
        }
    }

    @PreDestroy
    public void stopServer() {
        log.info("Stopping ASTM Interface Server...");
        running = false;
        listenerThreads.values().forEach(Thread::interrupt);
        serverSockets.values().forEach(this::safeClose);
        activeConnections.values().forEach(InstrumentConnectionHandler::stop);

        if (connectionExecutor != null) {
            connectionExecutor.shutdown();
            try {
                if (!connectionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    connectionExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                connectionExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("ASTM Server stopped.");
    }

    private void startInstrumentListener(AppConfig.InstrumentConfig config) {
        final String instrumentName = config.getName();
        Thread listenerThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(config.getPort())) {
                serverSockets.put(instrumentName, serverSocket);
                serverSocket.setSoTimeout(2000);
                log.info("Listener started for '{}' on port {} using driver {}", instrumentName, config.getPort(), config.getDriverClassName());

                while (running && !Thread.currentThread().isInterrupted()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        handleNewConnection(clientSocket, config);
                    } catch (SocketTimeoutException e) {
                        // expected: lets the loop re-check the running flag
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

        InstrumentConnectionHandler existingHandler = activeConnections.remove(instrumentName);
        if (existingHandler != null) {
            log.warn("Instrument '{}' already had a session from {} (idle {} s). Replacing it with the new connection from {}.",
                    instrumentName, existingHandler.getRemoteAddress(), existingHandler.getIdleTime().getSeconds(),
                    clientSocket.getRemoteSocketAddress());
            existingHandler.stop();
        }

        try {
            clientSocket.setKeepAlive(true);
            clientSocket.setTcpNoDelay(true);
            clientSocket.setSoTimeout(InstrumentConnectionHandler.READ_POLL_TIMEOUT_MS);

            InstrumentDriver driver = createDriver(config);
            InstrumentConnectionHandler handler = new InstrumentConnectionHandler(
                    clientSocket, driver, instrumentName, resultQueuePublisher, serverMessageService, objectMapper,
                    orderOutcomeListenerProvider.getIfAvailable());

            activeConnections.put(instrumentName, handler);
            connectionExecutor.submit(() -> {
                try {
                    handler.run();
                } finally {
                    activeConnections.remove(instrumentName, handler);
                }
            });
        } catch (Exception e) {
            log.error("Failed to create and start connection handler for '{}': {}", instrumentName, e.getMessage(), e);
            safeClose(clientSocket);
        }
    }

    private InstrumentDriver createDriver(AppConfig.InstrumentConfig config) throws ReflectiveOperationException {
        String className = config.getDriverClassName();
        if (className == null || className.trim().isEmpty()) {
            throw new IllegalStateException("No driverClassName configured for instrument '" + config.getName() + "'");
        }
        return (InstrumentDriver) Class.forName(className.trim()).getDeclaredConstructor().newInstance();
    }

    /**
     * @return the active, connected handler for the instrument, or null when no session is active.
     */
    public InstrumentConnectionHandler getConnectionHandler(String instrumentName) {
        InstrumentConnectionHandler handler = activeConnections.get(instrumentName);
        if (handler != null && handler.isConnected()) {
            return handler;
        } else if (handler != null) {
            activeConnections.remove(instrumentName, handler);
        }
        return null;
    }

    /** Read-only view of the active sessions, for monitoring. */
    public Map<String, InstrumentConnectionHandler> getActiveConnections() {
        return Collections.unmodifiableMap(activeConnections);
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
