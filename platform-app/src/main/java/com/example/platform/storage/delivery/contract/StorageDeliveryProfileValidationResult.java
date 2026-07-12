package com.example.platform.storage.delivery.contract;

import java.util.List;

public record StorageDeliveryProfileValidationResult(
    boolean valid, StorageDeliveryProfileId profileId,
    List<StorageDeliveryProfileValidationIssue> errors,
    List<StorageDeliveryProfileValidationIssue> warnings
) {
    public static StorageDeliveryProfileValidationResult valid(StorageDeliveryProfileId profileId) {
        return new StorageDeliveryProfileValidationResult(true, profileId, List.of(), List.of());
    }
    public static StorageDeliveryProfileValidationResult invalid(StorageDeliveryProfileId profileId, List<StorageDeliveryProfileValidationIssue> errors) {
        return new StorageDeliveryProfileValidationResult(false, profileId, errors, List.of());
    }
    public boolean hasErrors() { return !errors.isEmpty(); }
    public boolean hasWarnings() { return !warnings.isEmpty(); }
}
