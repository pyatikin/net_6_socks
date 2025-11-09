package com.pyatkin.net_6_socks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Socks5ProxyServer {
    private static final Logger log = LoggerFactory.getLogger(Socks5ProxyServer.class);

    private final int listenPort;
    private final String upstreamHost;
    private final int upstreamPort;
    private final String defaultStrategy;
    private final String blacklistFile, whitelistFile, redirectFile, segmentFile;
    private final int segmentBlockSize, segmentSize, segmentDelay;

    private final ExecutorService clientPool = Executors.newCachedThreadPool();

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
    }

    public void start() {
        log.info("Starting server on port {} (defaultStrategy={})", listenPort, defaultStrategy);
        RuleManager ruleManager = new RuleManager(blacklistFile, whitelistFile, redirectFile, segmentFile);
        TrafficSegmenter segmenter = new TrafficSegmenter(segmentBlockSize, segmentSize, segmentDelay);

        try (ServerSocket ss = new ServerSocket(listenPort)) {
            while (true) {
                Socket client = ss.accept();
                log.info("Accepted client {}", client.getRemoteSocketAddress());
                clientPool.submit(new ClientHandler(client, ruleManager, segmenter, defaultStrategy, upstreamHost, upstreamPort));
            }
        } catch (Exception e) {
            log.error("Server failure: {}", e.getMessage(), e);
        } finally {
            clientPool.shutdown();
        }
    }
}