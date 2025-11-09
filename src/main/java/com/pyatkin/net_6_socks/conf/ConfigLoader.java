package com.pyatkin.net_6_socks.conf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;

public class ConfigLoader {
    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);
    private static final String CONFIG_FILE = "application.yml";

    public static ProxyConfig load() {
        log.info("Loading configuration from {}", CONFIG_FILE);

        Yaml yaml = new Yaml();
        try (InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                throw new RuntimeException(CONFIG_FILE + " not found in classpath resources");
            }

            ProxyConfig cfg = yaml.loadAs(is, ProxyConfig.class);
            if (cfg == null) {
                throw new RuntimeException("Failed to parse " + CONFIG_FILE + " - result is null");
            }

            cfg.validate();
            log.info("Configuration loaded successfully");
            return cfg;

        } catch (IllegalArgumentException e) {
            log.error("Configuration validation failed: {}", e.getMessage());
            throw new RuntimeException("Invalid configuration: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to load configuration: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to load configuration from " + CONFIG_FILE, e);
        }
    }
}