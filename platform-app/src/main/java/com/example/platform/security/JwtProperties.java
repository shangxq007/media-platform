package com.example.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String secretKey, long expirationMs) {
    public static final String INSECURE_DEV_DEFAULT =
            "dev-only-insecure-key-replace-in-production-min-256-bits!!";

    public JwtProperties {
        if (expirationMs <= 0) {
            expirationMs = 86_400_000;
        }
    }

    public boolean usesInsecureDefault() {
        return secretKey == null || secretKey.isBlank() || INSECURE_DEV_DEFAULT.equals(secretKey);
    }

    /** Dev-only fallback when {@code APP_JWT_SECRET} is unset; never use in production. */
    public String resolvedSecretKey() {
        if (secretKey != null && !secretKey.isBlank()) {
            return secretKey;
        }
        return INSECURE_DEV_DEFAULT;
    }
}
