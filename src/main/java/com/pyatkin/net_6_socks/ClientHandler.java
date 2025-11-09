package com.pyatkin.net_6_socks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Socket;

public class ClientHandler implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private final Socket client;
    private final RuleManager rules;
    private final TrafficSegmenter segmenter;
    private final String defaultStrategy;
    private final String upstreamHost;
    private final int upstreamPort;

    public ClientHandler(Socket client, RuleManager rules, TrafficSegmenter segmenter, String defaultStrategy, String upstreamHost, int upstreamPort) {
        this.client = client;
        this.rules = rules;
        this.segmenter = segmenter;
        this.defaultStrategy = defaultStrategy;
        this.upstreamHost = upstreamHost;
        this.upstreamPort = upstreamPort;
    }

    @Override
    public void run() {
        try {
            Socks5Session session = new Socks5Session(client, rules, segmenter, defaultStrategy, upstreamHost, upstreamPort);
            session.handle();
        } catch (Throwable t) {
            log.error("Error in client handler: {}", t.getMessage(), t);
            try { client.close(); } catch (Exception ignored) {}
        }
    }
}