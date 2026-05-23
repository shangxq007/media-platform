package com.example.platform.delivery.domain;

public enum DeliveryProtocol {
    S3_MIRROR,
    SFTP,
    WEBDAV,
    SMB,
    HTTPS_PUT;

    public static DeliveryProtocol fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("protocol is required");
        }
        return DeliveryProtocol.valueOf(value.trim().toUpperCase());
    }
}
