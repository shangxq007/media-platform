package com.example.platform.storage.delivery.diagnostics;

import com.example.platform.storage.delivery.contract.StorageDeliveryProfileId;
import java.time.Instant;
import java.util.List;

public record StorageDeliveryProfileDiagnosticsResponse(
    String diagnosticsMode,
    boolean runtimeSwitchingImplemented,
    boolean artifactAccessUsesRegistry,
    boolean providerSelectionUsesRegistry,
    boolean remoteCallsPerformed,
    StorageDeliveryProfileId defaultProfileId,
    int profileCount,
    List<StorageDeliveryProfileId> profileIds,
    List<StorageDeliveryProfileId> enabledProfileIds,
    List<StorageDeliveryProfileId> runtimeSelectableProfileIds,
    List<StorageDeliveryProfileDiagnosticsItem> profiles,
    StorageDeliveryProfileValidationDiagnostics validation,
    Instant generatedAt
) {}
