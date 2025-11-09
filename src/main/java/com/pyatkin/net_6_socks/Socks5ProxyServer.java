package com.pyatkin.net_6_socks;

import com.pyatkin.net_6_socks.handler.ClientHandler;
import com.pyatkin.net_6_socks.rules.RuleManager;
import com.pyatkin.net_6_socks.traffic.TrafficSegmenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SOCKS5 proxy server with rule-based traffic management.
 */
public class Socks5ProxyServer {
    private static final Logger log = LoggerFactory.getLogger(Socks5ProxyServer.class);

    private final int listenPort;
    private final String upstreamHost;
    private final int upstreamPort;
    private final String defaultStrategy;
    private final String blacklistFile;
    private final String whitelistFile;
    private final String redirectFile;
    private final String segmentFile;
    private final int segmentBlockSize;
    private final int segmentSize;
    private final int segmentDelay;

    private final ExecutorService clientPool;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong acceptedConnections = new AtomicLong(0);
    private ServerSocket serverSocket;

    public Socks5ProxyServer(int listenPort,
                             String upstreamHost,
                             int upstreamPort,
                             String defaultStrategy,
                             String blacklistFile,
                             String whitelistFile,
                             String redirectFile,
                             String segmentFile,
                             int segmentBlockSize,
                             int segmentSize,
                             int segmentDelay) {
        this.listenPort = listenPort;
        this.upstreamHost = upstreamHost;
        this.upstreamPort = upstreamPort;
        this.defaultStrategy = defaultStrategy == null ? "direct" : defaultStrategy.toLowerCase();
        this.blacklistFile = blacklistFile;
        this.whitelistFile = whitelistFile;
        this.redirectFile = redirectFile;
        this.segmentFile = segmentFile;
        this.segmentBlockSize = segmentBlockSize;
        this.segmentSize = segmentSize;
        this.segmentDelay = segmentDelay;

        this.clientPool = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setName("ClientHandler-" + acceptedConnections.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        });
    }

    /**
     * Starts the proxy server.
     */
    public void start() {
        if (running.getAndSet(true)) {
            log.warn("Server is already running");
            return;
        }

        log.info("=".repeat(60));
        log.info("Starting SOCKS5 Proxy Server");
        log.info("  Listen Port: {}", listenPort);
        log.info("  Default Strategy: {}", defaultStrategy);
        log.info("  Upstream: {}:{}", upstreamHost, upstreamPort);
        log.info("  Segmentation: blockSize={}, segmentSize={}, delayMs={}",
                segmentBlockSize, segmentSize, segmentDelay);
        log.info("=".repeat(60));

        // Initialize rule manager
        RuleManager ruleManager = new RuleManager(
                blacklistFile,
                whitelistFile,
                redirectFile,
                segmentFile
        );

        // Initialize traffic segmenter
        TrafficSegmenter segmenter = new TrafficSegmenter(
                segmentBlockSize,
                segmentSize,
                segmentDelay
        );

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received");
            shutdown();
            ruleManager.logHitStatistics();
        }, "ShutdownHook"));

        try {
            serverSocket = new ServerSocket(listenPort);
            serverSocket.setReuseAddress(true);
            log.info("Server listening on port {}", listenPort);

            while (running.get()) {
                try {
                    Socket client = serverSocket.accept();
                    long connNumber = acceptedConnections.incrementAndGet();

                    log.info("Accepted connection #{} from {}",
                            connNumber, client.getRemoteSocketAddress());

                    ClientHandler handler = new ClientHandler(
                            client,
                            ruleManager,
                            segmenter,
                            defaultStrategy,
                            upstreamHost,
                            upstreamPort
                    );

                    clientPool.submit(handler);

                } catch (SocketException e) {
                    if (running.get()) {
                        log.error("Socket error: {}", e.getMessage());
                    } else {
                        log.debug("Server socket closed during shutdown");
                    }
                    break;
                } catch (IOException e) {
                    if (running.get()) {
                        log.error("Error accepting connection: {}", e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            log.error("Failed to start server on port {}: {}", listenPort, e.getMessage(), e);
        } finally {
            shutdown();
            log.info("Server stopped. Total connections accepted: {}", acceptedConnections.get());
        }
    }

    /**
     * Stops the proxy server gracefully.
     */
    public void shutdown() {
        if (!running.getAndSet(false)) {
            return;
        }

        log.info("Shutting down server...");

        // Close server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                log.info("Server socket closed");
            } catch (IOException e) {
                log.error("Error closing server socket: {}", e.getMessage());
            }
        }

        // Shutdown thread pool
        clientPool.shutdown();
        try {
            log.info("Waiting for active connections to complete (timeout: 30s)...");
            if (!clientPool.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Timeout waiting for connections - forcing shutdown");
                clientPool.shutdownNow();

                if (!clientPool.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.error("Thread pool did not terminate");
                }
            } else {
                log.info("All connections completed gracefully");
            }
        } catch (InterruptedException e) {
            log.warn("Shutdown interrupted - forcing shutdown");
            clientPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("Server shutdown complete");
    }

    /**
     * Checks if server is running.
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Returns the number of accepted connections.
     */
    public long getAcceptedConnections() {
        return acceptedConnections.get();
    }
}