package com.pyatkin.net_6_socks;

import com.pyatkin.net_6_socks.conf.ConfigLoader;
import com.pyatkin.net_6_socks.conf.ProxyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Socks5ProxyApp {
    private static final Logger log = LoggerFactory.getLogger(Socks5ProxyApp.class);

    public static void main(String[] args) {
        ProxyConfig cfg = ConfigLoader.load();

        // Allow CLI overrides: --default-strategy=..., --upstream-host=..., --upstream-port=...
        for (String arg : args) {
            if (arg.startsWith("--default-strategy=")) {
                cfg.strategy.defaultStrategy = arg.substring("--default-strategy=".length()).trim();
            } else if (arg.startsWith("--upstream-host=")) {
                cfg.upstream.host = arg.substring("--upstream-host=".length()).trim();
            } else if (arg.startsWith("--upstream-port=")) {
                try { cfg.upstream.port = Integer.parseInt(arg.substring("--upstream-port=".length()).trim()); }
                catch (NumberFormatException ignored) {}
            } else if (arg.startsWith("--listen-port=")) {
                try { cfg.server.listenPort = Integer.parseInt(arg.substring("--listen-port=".length()).trim()); }
                catch (NumberFormatException ignored) {}
            }
        }

        log.info("Starting Socks5Proxy on port={}, defaultStrategy={}, upstream={}:{}",
                cfg.server.listenPort, cfg.strategy.defaultStrategy, cfg.upstream.host, cfg.upstream.port);
        Socks5ProxyServer server = new Socks5ProxyServer(
                cfg.server.listenPort,
                cfg.upstream.host, cfg.upstream.port,
                cfg.strategy.defaultStrategy,
                cfg.rules.blacklist,
                cfg.rules.whitelist,
                cfg.rules.redirect,
                cfg.rules.segment,
                cfg.segment.blockSize,
                cfg.segment.segmentSize,
                cfg.segment.delayMs
        );
        server.start();
    }
}