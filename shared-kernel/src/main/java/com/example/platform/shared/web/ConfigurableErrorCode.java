package com.example.platform.shared.web;

import java.util.Map;

/**
 * Localized error code value.
 */
public record ConfigurableErrorCode(
        String code,
        int numericCode,
        Map<String, String> messages,
        String module,
        int status
) implements ErrorCode {

    @Override
    public String code() { return code; }

    @Override
    public String title() { return message("en"); }

    @Override
    public int status() { return status; }

    public String message(String locale) {
        if (messages == null) return code;
        return messages.getOrDefault(locale, messages.getOrDefault("en", code));
    }

    public String message() { return message("en"); }
}
