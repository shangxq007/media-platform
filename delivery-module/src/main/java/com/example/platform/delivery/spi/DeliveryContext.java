package com.example.platform.delivery.spi;

import java.io.InputStream;
import java.util.Map;

public record DeliveryContext(
        String deliveryJobId,
        String tenantId,
        String projectId,
        String renderJobId,
        String sourceUri,
        String sourceFileName,
        String contentType,
        long contentLength,
        InputStream sourceStream,
        String remotePath,
        String protocol,
        Map<String, Object> destinationConfig,
        Map<String, String> credentials) {}
