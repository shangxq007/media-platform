package com.example.platform.storage.delivery.contract;

public record StorageDeliveryProfileValidationIssue(
    StorageDeliveryProfileId profileId, String code, String message,
    StorageDeliveryProfileValidationSeverity severity
) {}
