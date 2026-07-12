package com.example.platform.storage.delivery.diagnostics;

import com.example.platform.storage.delivery.contract.*;

public record StorageDeliveryProfileDiagnosticsItem(
    StorageDeliveryProfileId profileId,
    StorageDeliveryProfileStatus status,
    StorageAccessMode accessMode,
    StorageBackendType backendType,
    StorageProviderType providerType,
    boolean enabled,
    boolean runtimeSelectable,
    boolean userFacingAllowed,
    StorageDeliveryProfileCapabilityDiagnostics capabilities,
    StorageDeliveryProfileSecurityDiagnostics securityPolicy,
    String validationStatus
) {}
