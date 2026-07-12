package com.example.platform.storage.delivery.registry;

import com.example.platform.storage.delivery.contract.*;
import com.example.platform.storage.delivery.validation.StorageDeliveryProfileValidator;
import java.util.*;
import java.util.stream.Collectors;

public class StorageDeliveryProfileRegistry {

    private final Map<StorageDeliveryProfileId, StorageDeliveryProfile> profiles;
    private final StorageDeliveryProfileId defaultProfileId;
    private final StorageDeliveryProfileValidationResult validationResult;

    private StorageDeliveryProfileRegistry(Map<StorageDeliveryProfileId, StorageDeliveryProfile> profiles,
                                           StorageDeliveryProfileId defaultProfileId) {
        this.profiles = Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
        this.defaultProfileId = defaultProfileId;
        this.validationResult = new StorageDeliveryProfileValidator().validateAll(profiles.values());
    }

    public static StorageDeliveryProfileRegistry fromCanonicalCatalog() {
        return fromCanonicalCatalog(StorageDeliveryProfileId.PREVIEW_R2_SIGNED_URL);
    }

    public static StorageDeliveryProfileRegistry fromCanonicalCatalog(StorageDeliveryProfileId defaultProfileId) {
        Map<StorageDeliveryProfileId, StorageDeliveryProfile> profiles = new LinkedHashMap<>();
        for (var profile : StorageDeliveryProfileCatalog.canonicalProfiles()) {
            profiles.put(profile.id(), profile);
        }
        return new StorageDeliveryProfileRegistry(profiles, defaultProfileId);
    }

    public StorageDeliveryProfileId defaultProfileId() { return defaultProfileId; }
    public Optional<StorageDeliveryProfile> defaultProfile() { return findById(defaultProfileId); }
    public Optional<StorageDeliveryProfile> findById(StorageDeliveryProfileId id) { return Optional.ofNullable(profiles.get(id)); }
    public boolean contains(StorageDeliveryProfileId id) { return profiles.containsKey(id); }
    public List<StorageDeliveryProfile> profiles() { return Collections.unmodifiableList(new ArrayList<>(profiles.values())); }
    public int profileCount() { return profiles.size(); }
    public StorageDeliveryProfileValidationResult validationResult() { return validationResult; }

    public StorageDeliveryProfileRegistrySnapshot snapshot() {
        List<StorageDeliveryProfileId> runtimeSelectable = profiles.values().stream()
            .filter(StorageDeliveryProfile::runtimeSelectable).map(StorageDeliveryProfile::id).toList();
        List<StorageDeliveryProfileId> enabled = profiles.values().stream()
            .filter(StorageDeliveryProfile::enabled).map(StorageDeliveryProfile::id).toList();
        return new StorageDeliveryProfileRegistrySnapshot(
            defaultProfileId, profiles.size(), new ArrayList<>(profiles.keySet()),
            runtimeSelectable, enabled, validationResult);
    }
}
