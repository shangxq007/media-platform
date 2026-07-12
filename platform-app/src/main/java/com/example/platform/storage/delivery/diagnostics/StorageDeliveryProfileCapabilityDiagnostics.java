package com.example.platform.storage.delivery.diagnostics;

public record StorageDeliveryProfileCapabilityDiagnostics(
    boolean supportsSignedUrl,
    boolean supportsInternalStream,
    boolean supportsExternalBucket,
    boolean supportsExportBundle,
    boolean supportsRead,
    boolean supportsWrite,
    boolean supportsDelete
) {}
