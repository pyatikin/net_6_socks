package com.pyatkin.net_6_socks.conf;

public class ProxyConfig {
    public Server server = new Server();
    public Upstream upstream = new Upstream();
    public Segment segment = new Segment();
    public Strategy strategy = new Strategy();
    public Rules rules = new Rules();

    public static class Server {
        public int listenPort = 1080;
    }

    public static class Upstream {
        public String host = "127.0.0.1";
        public int port = 9050;
    }

    public static class Segment {
        public int blockSize = 200;
        public int segmentSize = 50;
        public int delayMs = 30;
    }

    public static class Strategy {
        public String defaultStrategy = "direct";
    }

    public static class Rules {
        public String blacklist = "blacklist.txt";
        public String whitelist = "whitelist.txt";
        public String redirect = "redirect.txt";
        public String segment = "segment.txt";
    }
}
