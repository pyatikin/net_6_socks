package com.pyatkin.net_6_socks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

public class RuleManager {
    private static final Logger log = LoggerFactory.getLogger(RuleManager.class);

    private final Map<String, List<Pattern>> rules = new HashMap<>();

    public RuleManager(String blacklist, String whitelist, String redirect, String segment) {
        rules.put("blacklist", load(blacklist));
        rules.put("whitelist", load(whitelist));
        rules.put("redirect", load(redirect));
        rules.put("segment", load(segment));
    }

    private List<Pattern> load(String filePath) {
        List<Pattern> list = new ArrayList<>();
        if (filePath == null) return list;

        // Try local file first, then classpath resource
        File f = new File(filePath);
        if (f.exists()) {
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    try { list.add(Pattern.compile(line, Pattern.CASE_INSENSITIVE)); }
                    catch (Exception ex) { log.warn("Invalid pattern in {}: {} ({})", filePath, line, ex.getMessage()); }
                }
                log.info("Loaded {} rules from file {}", list.size(), f.getAbsolutePath());
                return list;
            } catch (IOException e) {
                log.warn("Failed to load rules from {}: {}", f.getAbsolutePath(), e.getMessage());
            }
        }

        // try classpath
        try (InputStream is = RuleManager.class.getClassLoader().getResourceAsStream(filePath)) {
            if (is != null) {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(is))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        try { list.add(Pattern.compile(line, Pattern.CASE_INSENSITIVE)); }
                        catch (Exception ex) { log.warn("Invalid pattern in classpath {}: {} ({})", filePath, line, ex.getMessage()); }
                    }
                    log.info("Loaded {} rules from classpath resource {}", list.size(), filePath);
                }
            } else {
                log.info("Rule file/resource {} not found; treat as empty", filePath);
            }
        } catch (IOException e) {
            log.warn("Failed to read classpath rules {}: {}", filePath, e.getMessage());
        }
        return list;
    }

    public boolean matches(String listName, String host) {
        if (host == null) return false;
        for (Pattern p : rules.getOrDefault(listName, Collections.emptyList())) {
            if (p.matcher(host).find()) return true;
        }
        return false;
    }

    /**
     * Return the first matching list name by priority:
     * blacklist -> whitelist -> redirect -> segment
     * or null if none matched.
     */
    public String firstMatch(String host) {
        if (matches("blacklist", host)) return "blacklist";
        if (matches("whitelist", host)) return "whitelist";
        if (matches("redirect", host)) return "redirect";
        if (matches("segment", host)) return "segment";
        return null;
    }
}