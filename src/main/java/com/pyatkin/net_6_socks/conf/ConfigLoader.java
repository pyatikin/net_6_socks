package com.pyatkin.net_6_socks.conf;

import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;

public class ConfigLoader {
    public static ProxyConfig load() {
        Yaml yaml = new Yaml();
        try (InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream("application.yml")) {
            if (is == null) {
                throw new RuntimeException("application.yml not found in classpath resources");
            }
            ProxyConfig cfg = yaml.loadAs(is, ProxyConfig.class);
            if (cfg == null) throw new RuntimeException("Failed to parse application.yml");
            return cfg;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load configuration: " + e.getMessage(), e);
        }
    }
}