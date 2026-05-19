package com.example.platform.shared.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for configurable error codes loaded from error-codes.json.
 */
@Component
public class ErrorCodeRegistry {
    private static final Logger log = LoggerFactory.getLogger(ErrorCodeRegistry.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, ConfigurableErrorCode> errorCodes = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadErrorCodes() {
        try {
            InputStream is = new ClassPathResource("error-codes.json").getInputStream();
            Map<String, Object> root = MAPPER.readValue(is, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> errors = (Map<String, Map<String, Object>>) root.get("errors");

            if (errors != null) {
                for (Map.Entry<String, Map<String, Object>> entry : errors.entrySet()) {
                    String code = entry.getKey();
                    Map<String, Object> data = entry.getValue();

                    int numericCode = data.containsKey("numericCode") ? ((Number) data.get("numericCode")).intValue() : 0;
                    String module = (String) data.getOrDefault("module", "unknown");
                    int status = data.containsKey("status") ? ((Number) data.get("status")).intValue() : 500;

                    @SuppressWarnings("unchecked")
                    Map<String, String> messages = (Map<String, String>) data.getOrDefault("messages", Map.of());

                    errorCodes.put(code, new ConfigurableErrorCode(code, numericCode, messages, module, status));
                }
            }
            log.info("Loaded {} error codes from error-codes.json", errorCodes.size());
        } catch (Exception e) {
            log.error("Failed to load error-codes.json", e);
        }
    }

    public Optional<ConfigurableErrorCode> getErrorCode(String code) {
        return Optional.ofNullable(errorCodes.get(code));
    }

    public ConfigurableErrorCode getRequiredErrorCode(String code) {
        return getErrorCode(code).orElseThrow(() ->
                new IllegalStateException("Unknown error code: " + code));
    }

    public Map<String, ConfigurableErrorCode> getAllErrorCodes() {
        return Map.copyOf(errorCodes);
    }
}
