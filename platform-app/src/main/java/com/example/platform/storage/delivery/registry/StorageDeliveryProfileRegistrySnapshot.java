package com.example.platform.storage.delivery.registry;

import com.example.platform.storage.delivery.contract.*;
import java.util.List;

public record StorageDeliveryProfileRegistrySnapshot(
    StorageDeliveryProfileId defaultProfileId,
    int profileCount,
    List<StorageDeliveryProfileId> profileIds,
    List<StorageDeliveryProfileId> runtimeSelectableProfileIds,
    List<StorageDeliveryProfileId> enabledProfileIds,
    StorageDeliveryProfileValidationResult validationResult
) {}
