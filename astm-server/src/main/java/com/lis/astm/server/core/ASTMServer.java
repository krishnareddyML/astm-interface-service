package com.lis.astm.server.core;

import com.lis.astm.server.config.AppConfig;
import com.lis.astm.server.driver.InstrumentDriver;
import com.lis.astm.server.messaging.ResultQueuePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main ASTM Server component that manages TCP listeners for multiple instruments.
 * Handles incoming connections and creates isolated connection handlers.
 */
@Component
public class ASTMServer {

    private static final Logger logger = LoggerFactory.getLogger(ASTMServer.class);

    // Network defaults (can be moved to AppConfig later)
    private static final int ACCEPT_TIMEOUT_MS = 1_000;     // accept() wake interval
    //private static final int READ_TIMEOUT_MS   = 30_000;    // per-connection read() timeout
    private static final int READ_TIMEOUT_MS   = 360_000; // increasing 6 min, that said greater that Keep Live ping interval 5 min
    private static final int BIND_BACKLOG      = 128;       // pending connection queue
    private static final int SCHEDULER_THREADS = 4;         // keep-alive / timers
    private static final int AWAIT_SEC         = 30;        // shutdown wait

    @Autowired private AppConfig appConfig;
    @Autowired private ResultQueuePublisher resultQueuePublisher;

    // Executors
    private ExecutorService serverExecutor;                 // per-instrument listeners
    private ExecutorService connectionExecutor;             // per-connection handlers
    private ScheduledExecutorService keepAliveScheduler;    // periodic jobs/keep-alives

    // State
    private final ConcurrentHashMap<String, ServerSocket> serverSockets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<InstrumentConnectionHandler>> activeConnections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Future<?>> serverTasks = new ConcurrentHashMap<>();

    private final AtomicInteger serverThreadIdx = new AtomicInteger();
    private final AtomicInteger connThreadIdx   = new AtomicInteger();
    private final AtomicInteger schedThreadIdx  = new AtomicInteger();

    private volatile boolean running = false;

    @PostConstruct
    public void startServer() {
        if (running) {
            logger.warn("ASTM Interface Server is already running; start skipped.");
            return;
        }
        logger.info("Starting ASTM Interface Server...");

        final List<AppConfig.InstrumentConfig> instruments =
                appConfig.getInstruments() != null ? appConfig.getInstruments() : java.util.Collections.emptyList();

        if (instruments.isEmpty()) {
            logger.warn("No instruments configured. Server will start but no listeners will be created.");
            // We still initialize executors to allow hot-reload adding instruments later if applicable.
        }

        // ---- Executors: bounded, non-daemon, with backpressure ----
        final int instrumentCount = Math.max(1, instruments.size());
        final int serverCore = Math.min(2, instrumentCount);
        final int serverMax  = Math.max(2, instrumentCount); // 1 listener per instrument is typical
        final int serverQueue = Math.max(1, instrumentCount); // backpressure if many start at once

        serverExecutor = new ThreadPoolExecutor(
                serverCore, serverMax, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(serverQueue),
                r -> namedThread(r, "ASTM-Server-", serverThreadIdx.getAndIncrement()),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        connectionExecutor = new ThreadPoolExecutor(
                8, 64, 120, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1024),
                r -> namedThread(r, "ASTM-Conn-", connThreadIdx.getAndIncrement()),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        keepAliveScheduler = Executors.newScheduledThreadPool(
                SCHEDULER_THREADS,
                r -> namedThread(r, "ASTM-KeepAlive-", schedThreadIdx.getAndIncrement())
        );

        running = true;

        // ---- Start listeners per enabled instrument ----
        for (AppConfig.InstrumentConfig cfg : instruments) {
            if (cfg.isEnabled()) {
                startInstrumentListener(cfg);
            } else {
                logger.info("Instrument {} is disabled, skipping", cfg.getName());
            }
        }

        logger.info("ASTM Interface Server started with {} instrument listener(s).", serverSockets.size());
    }

    @PreDestroy
    public void stopServer() {
        if (!running) {
            logger.info("ASTM Interface Server already stopped.");
            return;
        }
        logger.info("Stopping ASTM Interface Server...");
        running = false;

        // Cancel listener tasks (sets interrupt flag)
        for (Map.Entry<String, Future<?>> e : serverTasks.entrySet()) {
            try {
                e.getValue().cancel(true);
            } catch (Exception ex) {
                logger.warn("Error cancelling listener task for {}: {}", e.getKey(), ex.toString(), ex);
            }
        }
        serverTasks.clear();

        // Close server sockets to unblock accept()
        closeAllServerSockets();

        // Ask all active handlers to stop
        stopAllActiveConnections();

        // Shutdown executors and await termination
        shutdownAndAwait(serverExecutor, "serverExecutor");
        shutdownAndAwait(connectionExecutor, "connectionExecutor");
        shutdownAndAwait(keepAliveScheduler, "keepAliveScheduler");

        logger.info("ASTM Interface Server stopped.");
    }

    // ---- Listener setup ----
    private void startInstrumentListener(AppConfig.InstrumentConfig config) {
        final String name = config.getName();
        try {
            ServerSocket serverSocket = new ServerSocket();  // unbound initially
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(config.getPort()), BIND_BACKLOG);
            serverSocket.setSoTimeout(ACCEPT_TIMEOUT_MS);

            serverSockets.put(name, serverSocket);
            activeConnections.put(name, new CopyOnWriteArrayList<>());

            logger.info("Started TCP listener for {} on port {}", name, config.getPort());

            Future<?> task = serverExecutor.submit(() -> {
                Thread.currentThread().setName("ASTM-Server-" + name);
                runInstrumentListener(config, serverSocket);
            });
            serverTasks.put(name, task);

        } catch (IOException e) {
            logger.error("Failed to start listener for {} on port {}",
                    name, config.getPort(), e);
        }
    }

    // ---- Accept loop per instrument ----
    private void runInstrumentListener(AppConfig.InstrumentConfig config, ServerSocket serverSocket) {
        final String instrument = config.getName();
        logger.info("Instrument listener running for {} on port {}", instrument, config.getPort());

        while (running && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept(); // blocks up to ACCEPT_TIMEOUT_MS
                if (clientSocket == null) continue;

                // Basic socket tuning (can be moved to handler ctor if preferred)
                try {
                    clientSocket.setTcpNoDelay(true);
                    clientSocket.setKeepAlive(true);
                    clientSocket.setSoTimeout(READ_TIMEOUT_MS);
                } catch (Exception sox) {
                    logger.warn("Unable to set socket options for {}: {}", instrument, sox.toString(), sox);
                }

                logger.info("Accepted connection from {} for instrument {}",
                        clientSocket.getRemoteSocketAddress(), instrument);

                // Enforce per-instrument connection limit
                List<InstrumentConnectionHandler> connections =
                        activeConnections.computeIfAbsent(instrument, k -> new CopyOnWriteArrayList<>());

                if (connections.size() >= config.getMaxConnections()) {
                    logger.warn("Max connections ({}) reached for {}, rejecting connection",
                            config.getMaxConnections(), instrument);
                    safeClose(clientSocket);
                    continue;
                }

                // Create instrument driver
                InstrumentDriver driver = createInstrumentDriver(config);
                if (driver == null) {
                    logger.error("Failed to create driver for instrument {}, rejecting connection", instrument);
                    safeClose(clientSocket);
                    continue;
                }

                // Build handler
                InstrumentConnectionHandler handler = new InstrumentConnectionHandler(
                        clientSocket,
                        driver,
                        instrument,
                        resultQueuePublisher,
                        config.getKeepAliveIntervalMinutes(),
                        keepAliveScheduler
                );

                // Track and execute
                connections.add(handler);
                connectionExecutor.submit(() -> {
                    final String prevName = Thread.currentThread().getName();
                    Thread.currentThread().setName("ASTM-Conn-" + instrument + "-" + connThreadIdx.getAndIncrement());
                    try {
                        handler.run();
                    } catch (Throwable t) {
                        logger.error("Handler crashed for {}: {}", instrument, t.toString(), t);
                    } finally {
                        connections.remove(handler);
                        Thread.currentThread().setName(prevName);
                        logger.info("Connection handler closed for {} (active: {})", instrument, connections.size());
                    }
                });

                logger.info("Created connection handler for {} (active: {})", instrument, connections.size());

            } catch (SocketTimeoutException ste) {
                // Accept timed out; loop checks `running` again
            } catch (IOException ioe) {
                if (running) {
                    logger.error("I/O error accepting connection for {}: {}", instrument, ioe.toString(), ioe);
                }
                break; // exit loop if socket likely closed/fatal
            } catch (Throwable t) {
                logger.error("Unexpected error in listener for {}: {}", instrument, t.toString(), t);
            }
        }

        logger.info("Instrument listener stopped for {}", instrument);
    }

    // ---- Driver creation (reflection) ----
    private InstrumentDriver createInstrumentDriver(AppConfig.InstrumentConfig config) {
        try {
            logger.debug("Creating instrument driver: {}", config.getDriverClassName());
            Class<?> driverClass = Class.forName(config.getDriverClassName());
            return (InstrumentDriver) driverClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            logger.error("Failed to create instrument driver {}",
                    config.getDriverClassName(), e);
            return null;
        }
    }

    // ---- Status APIs ----
    public String getServerStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("ASTM Server Status:\n");
        sb.append("Running: ").append(running).append('\n');
        sb.append("Active Listeners: ").append(serverSockets.size()).append('\n');
        for (String instrument : activeConnections.keySet()) {
            List<InstrumentConnectionHandler> conns = activeConnections.getOrDefault(instrument, java.util.Collections.emptyList());
            sb.append("Instrument ").append(instrument).append(": ")
              .append(conns.size()).append(" active connections\n");
        }
        return sb.toString();
    }

    public List<String> getConnectionDetails() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, List<InstrumentConnectionHandler>> e : activeConnections.entrySet()) {
            for (InstrumentConnectionHandler h : e.getValue()) {
                out.add(h.getConnectionStats());
            }
        }
        return out;
    }

    public InstrumentConnectionHandler getConnectionHandler(String instrumentName) {
        List<InstrumentConnectionHandler> conns = activeConnections.get(instrumentName);
        if (conns != null) {
            for (InstrumentConnectionHandler h : conns) {
                if (h.isConnected()) return h;
            }
        }
        return null;
    }

    public boolean isRunning() {
        return running;
    }

    public int getTotalActiveConnections() {
        return activeConnections.values().stream().mapToInt(List::size).sum();
    }

    // ---- Helpers ----
    private Thread namedThread(Runnable r, String prefix, int idx) {
        Thread t = new Thread(r, prefix + idx);
        t.setDaemon(false);
        t.setUncaughtExceptionHandler((th, ex) ->
                logger.error("Uncaught in {}: {}", th.getName(), ex.toString(), ex));
        return t;
    }

    private void closeAllServerSockets() {
        for (Map.Entry<String, ServerSocket> e : serverSockets.entrySet()) {
            try {
                e.getValue().close();
            } catch (IOException ex) {
                logger.warn("Error closing server socket for {}: {}", e.getKey(), ex.toString(), ex);
            }
        }
        serverSockets.clear();
    }

    private void stopAllActiveConnections() {
        for (Map.Entry<String, List<InstrumentConnectionHandler>> e : activeConnections.entrySet()) {
            for (InstrumentConnectionHandler handler : e.getValue()) {
                try {
                    handler.stop();
                } catch (Throwable t) {
                    logger.warn("Error stopping handler for {}: {}", e.getKey(), t.toString(), t);
                }
            }
        }
        activeConnections.clear();
    }

    private void shutdownAndAwait(ExecutorService es, String name) {
        if (es == null) return;
        es.shutdown();
        try {
            if (!es.awaitTermination(AWAIT_SEC, TimeUnit.SECONDS)) {
                logger.warn("{} did not terminate in {}s; forcing shutdownNow()", name, AWAIT_SEC);
                es.shutdownNow();
                if (!es.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.warn("{} still not terminated after shutdownNow()", name);
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            es.shutdownNow();
        }
    }

    private void safeClose(Socket s) {
        try {
            s.close();
        } catch (IOException ignore) {
        }
    }
}
