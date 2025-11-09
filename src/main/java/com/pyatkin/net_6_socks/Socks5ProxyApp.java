package com.pyatkin.net_6_socks;

import com.pyatkin.net_6_socks.conf.ConfigLoader;
import com.pyatkin.net_6_socks.conf.ProxyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application class for SOCKS5 proxy server.
 *
 * Usage:
 *   java -jar socks5-proxy.jar [options]
 *
 * Options:
 *   --listen-port=<port>           Port to listen on (default: 1080)
 *   --default-strategy=<strategy>  Default strategy: direct, redirect, segment (default: direct)
 *   --upstream-host=<host>         Upstream proxy host (default: 127.0.0.1)
 *   --upstream-port=<port>         Upstream proxy port (default: 9050)
 *
 * Examples:
 *   java -jar socks5-proxy.jar
 *   java -jar socks5-proxy.jar --default-strategy=segment
 *   java -jar socks5-proxy.jar --upstream-host=proxy.example.com --upstream-port=1080
 */
public class Socks5ProxyApp {
    private static final Logger log = LoggerFactory.getLogger(Socks5ProxyApp.class);

    public static void main(String[] args) {
        try {
            // Load base configuration from YAML
            ProxyConfig cfg = ConfigLoader.load();

            // Parse command-line arguments and override configuration
            parseArguments(args, cfg);

            // Validate final configuration
            cfg.validate();

            // Log final configuration
            logConfiguration(cfg);

            // Create and start server
            Socks5ProxyServer server = new Socks5ProxyServer(
                    cfg.server.listenPort,
                    cfg.upstream.host,
                    cfg.upstream.port,
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

        } catch (Exception e) {
            log.error("Failed to start application: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    /**
     * Parses command-line arguments and updates configuration.
     */
    private static void parseArguments(String[] args, ProxyConfig cfg) {
        for (String arg : args) {
            if (arg.startsWith("--")) {
                parseArgument(arg, cfg);
            } else if (arg.equals("-h") || arg.equals("--help")) {
                printUsage();
                System.exit(0);
            }
        }
    }

    private static void parseArgument(String arg, ProxyConfig cfg) {
        try {
            if (arg.startsWith("--default-strategy=")) {
                String value = extractValue(arg);
                if (!value.matches("direct|redirect|segment")) {
                    log.warn("Invalid default-strategy: {}. Must be: direct, redirect, or segment", value);
                    return;
                }
                cfg.strategy.defaultStrategy = value;
                log.info("CLI override: default-strategy={}", value);

            } else if (arg.startsWith("--upstream-host=")) {
                String value = extractValue(arg);
                if (value.isEmpty()) {
                    log.warn("Invalid upstream-host: cannot be empty");
                    return;
                }
                cfg.upstream.host = value;
                log.info("CLI override: upstream-host={}", value);

            } else if (arg.startsWith("--upstream-port=")) {
                int value = Integer.parseInt(extractValue(arg));
                if (value < 1 || value > 65535) {
                    log.warn("Invalid upstream-port: {}. Must be 1-65535", value);
                    return;
                }
                cfg.upstream.port = value;
                log.info("CLI override: upstream-port={}", value);

            } else if (arg.startsWith("--listen-port=")) {
                int value = Integer.parseInt(extractValue(arg));
                if (value < 1 || value > 65535) {
                    log.warn("Invalid listen-port: {}. Must be 1-65535", value);
                    return;
                }
                cfg.server.listenPort = value;
                log.info("CLI override: listen-port={}", value);

            } else {
                log.warn("Unknown argument: {}", arg);
            }

        } catch (NumberFormatException e) {
            log.warn("Invalid number format in argument: {}", arg);
        } catch (Exception e) {
            log.warn("Failed to parse argument {}: {}", arg, e.getMessage());
        }
    }

    private static String extractValue(String arg) {
        int idx = arg.indexOf('=');
        return idx > 0 ? arg.substring(idx + 1).trim() : "";
    }

    private static void logConfiguration(ProxyConfig cfg) {
        log.info("Final configuration:");
        log.info("  Server:");
        log.info("    - listenPort: {}", cfg.server.listenPort);
        log.info("  Upstream:");
        log.info("    - host: {}", cfg.upstream.host);
        log.info("    - port: {}", cfg.upstream.port);
        log.info("  Strategy:");
        log.info("    - defaultStrategy: {}", cfg.strategy.defaultStrategy);
        log.info("  Segmentation:");
        log.info("    - blockSize: {}", cfg.segment.blockSize);
        log.info("    - segmentSize: {}", cfg.segment.segmentSize);
        log.info("    - delayMs: {}", cfg.segment.delayMs);
        log.info("  Rules:");
        log.info("    - blacklist: {}", cfg.rules.blacklist);
        log.info("    - whitelist: {}", cfg.rules.whitelist);
        log.info("    - redirect: {}", cfg.rules.redirect);
        log.info("    - segment: {}", cfg.rules.segment);
    }

    private static void printUsage() {
        System.out.println("SOCKS5 Proxy Server with Rule-Based Traffic Management");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar socks5-proxy.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --listen-port=<port>           Port to listen on (default: 1080)");
        System.out.println("  --default-strategy=<strategy>  Default strategy: direct, redirect, segment");
        System.out.println("  --upstream-host=<host>         Upstream proxy host (default: 127.0.0.1)");
        System.out.println("  --upstream-port=<port>         Upstream proxy port (default: 9050)");
        System.out.println("  -h, --help                     Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar socks5-proxy.jar");
        System.out.println("  java -jar socks5-proxy.jar --default-strategy=segment");
        System.out.println("  java -jar socks5-proxy.jar --upstream-host=proxy.example.com --upstream-port=1080");
    }
}