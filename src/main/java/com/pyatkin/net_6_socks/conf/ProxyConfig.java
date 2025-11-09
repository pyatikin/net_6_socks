package com.pyatkin.net_6_socks.conf;

public class ProxyConfig {
    public Server server = new Server();
    public Upstream upstream = new Upstream();
    public Segment segment = new Segment();
    public Strategy strategy = new Strategy();
    public Rules rules = new Rules();

    public static class Server {
        public int listenPort = 1080;

        public void validate() {
            if (listenPort < 1 || listenPort > 65535) {
                throw new IllegalArgumentException("Invalid listenPort: " + listenPort);
            }
        }
    }

    public static class Upstream {
        public String host = "127.0.0.1";
        public int port = 9050;

        public void validate() {
            if (host == null || host.trim().isEmpty()) {
                throw new IllegalArgumentException("Upstream host cannot be empty");
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Invalid upstream port: " + port);
            }
        }
    }

    public static class Segment {
        public int blockSize = 200;
        public int segmentSize = 50;
        public int delayMs = 30;

        public void validate() {
            if (blockSize <= 0) {
                throw new IllegalArgumentException("Invalid blockSize: " + blockSize);
            }
            if (segmentSize <= 0 || segmentSize > blockSize) {
                throw new IllegalArgumentException("Invalid segmentSize: " + segmentSize);
            }
            if (delayMs < 0) {
                throw new IllegalArgumentException("Invalid delayMs: " + delayMs);
            }
        }
    }

    public static class Strategy {
        public String defaultStrategy = "direct";

        public void validate() {
            if (!defaultStrategy.matches("direct|redirect|segment")) {
                throw new IllegalArgumentException("Invalid defaultStrategy: " + defaultStrategy +
                        ". Must be one of: direct, redirect, segment");
            }
        }
    }

    public static class Rules {
        public String blacklist = "blacklist.txt";
        public String whitelist = "whitelist.txt";
        public String redirect = "redirect.txt";
        public String segment = "segment.txt";
    }

    public void validate() {
        server.validate();
        upstream.validate();
        segment.validate();
        strategy.validate();
    }
}