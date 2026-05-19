package com.example.platform.secrets.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SecretService {

    private static final Logger log = LoggerFactory.getLogger(SecretService.class);
    private static final Pattern ENV_REF_PATTERN = Pattern.compile("^\\$\\{([^:]+):?([^}]*)}$");
    private final Map<String, String> resolvedCache = new ConcurrentHashMap<>();

    public String resolve(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }

        return resolvedCache.computeIfAbsent(ref, this::doResolve);
    }

    public String resolve(String ref, String defaultValue) {
        String value = resolve(ref);
        return value != null ? value : defaultValue;
    }

    private String doResolve(String ref) {
        if (ref == null) return null;

        // Check if it's an environment variable reference: ${VAR_NAME:default}
        Matcher matcher = ENV_REF_PATTERN.matcher(ref);
        if (matcher.matches()) {
            String varName = matcher.group(1);
            String defaultVal = matcher.group(2);
            String envValue = System.getenv(varName);
            if (envValue != null && !envValue.isBlank()) {
                log.debug("Resolved secret reference '{}' from environment variable", varName);
                return envValue;
            }
            if (defaultVal != null && !defaultVal.isBlank()) {
                log.debug("Using default value for secret reference '{}'", varName);
                return defaultVal;
            }
            log.warn("Could not resolve secret reference '{}'", ref);
            return null;
        }

        // Not a reference pattern — return as-is (for non-secret values)
        return ref;
    }

    public void clearCache() {
        resolvedCache.clear();
    }
}
