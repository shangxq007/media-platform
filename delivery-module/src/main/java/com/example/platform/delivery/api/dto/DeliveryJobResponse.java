package com.example.platform.delivery.api.dto;

public record DeliveryJobResponse(
        String id,
        String renderJobId,
        String destinationId,
        String status,
        String sourceUri,
        String remoteUri,
        Long bytesTransferred,
        String errorMessage) {}
