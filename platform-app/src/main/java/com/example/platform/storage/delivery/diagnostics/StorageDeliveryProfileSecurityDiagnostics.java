package com.example.platform.storage.delivery.diagnostics;

public record StorageDeliveryProfileSecurityDiagnostics(
    boolean signedUrlGeneratedOnDemand,
    boolean signedUrlPersisted,
    boolean exposesBucket,
    boolean exposesObjectKey,
    boolean exposesStorageReferenceId,
    boolean exposesLocalPath,
    boolean requiresAuthorization,
    boolean userFacingAccessAllowed
) {}
