package com.pyatkin.net_6_socks.rules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


public class RuleManager {
    private static final Logger log = LoggerFactory.getLogger(RuleManager.class);

    private final Map<String, List<Pattern>> rules = new HashMap<>();
    private final Map<String, Integer> ruleHitCount = new HashMap<>();

    public RuleManager(String blacklist, String whitelist, String redirect, String segment) {
        log.info("Initializing RuleManager");
        rules.put("blacklist", loadRules(blacklist, "blacklist"));
        rules.put("whitelist", loadRules(whitelist, "whitelist"));
        rules.put("redirect", loadRules(redirect, "redirect"));
        rules.put("segment", loadRules(segment, "segment"));

        // Initialize hit counters
        rules.keySet().forEach(key -> ruleHitCount.put(key, 0));

        logStatistics();
    }

    private List<Pattern> loadRules(String filePath, String listName) {
        List<Pattern> patterns = new ArrayList<>();

        if (filePath == null || filePath.trim().isEmpty()) {
            log.debug("No file specified for {}, using empty rule list", listName);
            return patterns;
        }

        // Try to load from filesystem first
        File file = new File(filePath);
        if (file.exists() && file.isFile()) {
            patterns = loadFromFile(file, listName);
            if (!patterns.isEmpty()) {
                return patterns;
            }
        }

        // Try to load from classpath
        patterns = loadFromClasspath(filePath, listName);

        return patterns;
    }

    private List<Pattern> loadFromFile(File file, String listName) {
        List<Pattern> patterns = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            int lineNumber = 0;
            String line;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                Pattern pattern = parseLine(line, lineNumber, listName);
                if (pattern != null) {
                    patterns.add(pattern);
                }
            }

            log.info("Loaded {} patterns from file: {}", patterns.size(), file.getAbsolutePath());

        } catch (IOException e) {
            log.error("Failed to read rule file {}: {}", file.getAbsolutePath(), e.getMessage());
        }

        return patterns;
    }

    private List<Pattern> loadFromClasspath(String resourcePath, String listName) {
        List<Pattern> patterns = new ArrayList<>();

        try (InputStream is = RuleManager.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.warn("Rule resource {} not found in classpath, using empty list", resourcePath);
                return patterns;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                int lineNumber = 0;
                String line;

                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    Pattern pattern = parseLine(line, lineNumber, listName);
                    if (pattern != null) {
                        patterns.add(pattern);
                    }
                }

                log.info("Loaded {} patterns from classpath resource: {}", patterns.size(), resourcePath);
            }

        } catch (IOException e) {
            log.error("Failed to read classpath resource {}: {}", resourcePath, e.getMessage());
        }

        return patterns;
    }

    private Pattern parseLine(String line, int lineNumber, String listName) {
        line = line.trim();

        // Skip empty lines and comments
        if (line.isEmpty() || line.startsWith("#")) {
            return null;
        }

        try {
            return Pattern.compile(line, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            log.warn("Invalid regex pattern in {} at line {}: '{}' - {}",
                    listName, lineNumber, line, e.getMessage());
            return null;
        }
    }

    public boolean matches(String listName, String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }

        List<Pattern> patterns = rules.getOrDefault(listName, Collections.emptyList());

        for (Pattern pattern : patterns) {
            if (pattern.matcher(host).find()) {
                ruleHitCount.merge(listName, 1, Integer::sum);
                log.debug("Host '{}' matched {} rule: {}", host, listName, pattern.pattern());
                return true;
            }
        }

        return false;
    }

    public String firstMatch(String host) {
        if (host == null || host.isEmpty()) {
            return null;
        }

        // Priority order is important
        if (matches("blacklist", host)) return "blacklist";
        if (matches("whitelist", host)) return "whitelist";
        if (matches("redirect", host)) return "redirect";
        if (matches("segment", host)) return "segment";

        return null;
    }

    private void logStatistics() {
        log.info("=== Rule Statistics ===");
        rules.forEach((name, patterns) ->
                log.info("  {}: {} rules loaded", name, patterns.size())
        );
        log.info("======================");
    }

    public void logHitStatistics() {
        log.info("=== Rule Hit Statistics ===");
        ruleHitCount.forEach((name, count) ->
                log.info("  {}: {} hits", name, count)
        );
        log.info("===========================");
    }
}