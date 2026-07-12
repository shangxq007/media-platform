package com.example.platform.storage.delivery.diagnostics;

import com.example.platform.storage.delivery.contract.*;
import com.example.platform.storage.delivery.registry.StorageDeliveryProfileRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Internal read-only diagnostics for Storage Delivery Profile Registry.
 * No remote calls. No provider selection. No persistence.
 */
@Service
public class StorageDeliveryProfileDiagnosticsService {

    private final StorageDeliveryProfileRegistry registry;

    public StorageDeliveryProfileDiagnosticsService(StorageDeliveryProfileRegistry registry) {
        this.registry = registry;
    }

    public StorageDeliveryProfileDiagnosticsResponse getDiagnostics() {
        var snapshot = registry.snapshot();
        var validation = registry.validationResult();

        List<StorageDeliveryProfileDiagnosticsItem> profileItems = new ArrayList<>();
        for (var profile : registry.profiles()) {
            profileItems.add(toDiagnosticsItem(profile, validation));
        }

        return new StorageDeliveryProfileDiagnosticsResponse(
            "READ_ONLY", false, false, false, false,
            snapshot.defaultProfileId(),
            snapshot.profileCount(),
            snapshot.profileIds(),
            snapshot.enabledProfileIds(),
            snapshot.runtimeSelectableProfileIds(),
            profileItems,
            toValidationDiagnostics(validation),
            Instant.now()
        );
    }

    public Optional<StorageDeliveryProfileDiagnosticsItem> getProfileDiagnostics(StorageDeliveryProfileId id) {
        return registry.findById(id).map(profile -> toDiagnosticsItem(profile, registry.validationResult()));
    }

    public StorageDeliveryProfileValidationDiagnostics getValidationDiagnostics() {
        return toValidationDiagnostics(registry.validationResult());
    }

    private StorageDeliveryProfileDiagnosticsItem toDiagnosticsItem(StorageDeliveryProfile profile,
                                                                      StorageDeliveryProfileValidationResult validation) {
        var caps = profile.capabilities();
        var sec = profile.securityPolicy();

        return new StorageDeliveryProfileDiagnosticsItem(
            profile.id(),
            profile.status(),
            profile.accessMode(),
            profile.backend(),
            profile.provider(),
            profile.enabled(),
            profile.runtimeSelectable(),
            sec.userFacing(),
            new StorageDeliveryProfileCapabilityDiagnostics(
                caps.presignRead(), caps.internalStream(), caps.externalBucket(),
                caps.exportBundle(), caps.readArtifact(), caps.writeArtifact(), caps.deleteArtifact()
            ),
            new StorageDeliveryProfileSecurityDiagnostics(
                !sec.persistSignedUrl(), sec.persistSignedUrl(),
                sec.exposeBucket(), sec.exposeObjectKey(), sec.exposeStorageReference(),
                sec.exposeLocalPath(), sec.requireTenantProjectScope(), sec.userFacing()
            ),
            validation.valid() ? "VALID" : "INVALID"
        );
    }

    private StorageDeliveryProfileValidationDiagnostics toValidationDiagnostics(StorageDeliveryProfileValidationResult validation) {
        List<String> errorCodes = validation.errors().stream().map(StorageDeliveryProfileValidationIssue::code).toList();
        List<String> warningCodes = validation.warnings().stream().map(StorageDeliveryProfileValidationIssue::code).toList();

        return new StorageDeliveryProfileValidationDiagnostics(
            validation.valid(),
            validation.errors().size(),
            validation.warnings().size(),
            errorCodes,
            warningCodes
        );
    }
}
