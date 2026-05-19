package com.example.platform.shared.web;

import java.util.Map;

/**
 * Platform exception with configurable error code and i18n support.
 */
public class PlatformException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Map<String, Object> details;
    private final String locale;

    public PlatformException(ErrorCode errorCode) {
        this(errorCode, null, null, "en");
    }

    public PlatformException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, null, "en");
    }

    public PlatformException(ErrorCode errorCode, String detail, Map<String, Object> details, String locale) {
        super(detail != null ? detail : errorCode.title());
        this.errorCode = errorCode;
        this.details = details;
        this.locale = locale;
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public Map<String, Object> getDetails() { return details; }
    public String getLocale() { return locale; }

    public String getLocalizedMessage() {
        if (errorCode instanceof ConfigurableErrorCode ce) {
            return ce.message(locale);
        }
        return errorCode.title();
    }
}
