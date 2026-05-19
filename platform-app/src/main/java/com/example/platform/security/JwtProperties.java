package com.example.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String secretKey, long expirationMs) {
    public JwtProperties {
        if (secretKey == null || secretKey.isBlank()) {
            secretKey = "dev-only-insecure-key-replace-in-production-min-256-bits!!";
        }
        if (expirationMs <= 0) {
            expirationMs = 86_400_000;
        }
    }
}
