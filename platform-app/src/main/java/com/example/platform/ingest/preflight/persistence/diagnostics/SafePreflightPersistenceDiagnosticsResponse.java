package com.example.platform.ingest.preflight.persistence.diagnostics;

import com.example.platform.ingest.preflight.persistence.contract.SafePreflightPersistenceAccessScope;
import com.example.platform.ingest.preflight.persistence.contract.SafePreflightPersistenceMode;
import java.time.Instant;

public record SafePreflightPersistenceDiagnosticsResponse(
    String diagnosticsMode,
    SafePreflightPersistenceMode persistenceMode,
    SafePreflightPersistenceAccessScope accessScope,
    int retentionDays,
    boolean failOpen,
    boolean publicResponseEnabled,
    boolean allowRawMetadata,
    boolean allowLocalPath,
    boolean allowStorageInternals,
    boolean allowSignedUrl,
    boolean allowCredentials,
    boolean runtimePersistenceImplemented,
    boolean uploadHookIntegrated,
    String validationStatus,
    Instant generatedAt
) {}
