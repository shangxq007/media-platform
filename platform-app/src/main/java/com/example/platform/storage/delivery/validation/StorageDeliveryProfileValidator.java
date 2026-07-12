package com.example.platform.storage.delivery.validation;

import com.example.platform.storage.delivery.contract.*;
import java.util.*;

/**
 * Local in-memory validator for StorageDeliveryProfile.
 * No remote calls. No config binding. No runtime switching.
 */
public class StorageDeliveryProfileValidator {

    public StorageDeliveryProfileValidationResult validate(StorageDeliveryProfile profile) {
        List<StorageDeliveryProfileValidationIssue> errors = new ArrayList<>();
        List<StorageDeliveryProfileValidationIssue> warnings = new ArrayList<>();

        if (profile.id() == null) {
            errors.add(issue(null, "PROFILE_ID_MISSING", "Profile ID is required"));
        }
        if (profile.status() == null) {
            errors.add(issue(profile.id(), "PROFILE_STATUS_MISSING", "Profile status is required"));
        }
        if (profile.provider() == null) {
            errors.add(issue(profile.id(), "PROVIDER_MISSING", "Provider type is required"));
        }
        if (profile.backend() == null) {
            errors.add(issue(profile.id(), "BACKEND_MISSING", "Backend type is required"));
        }
        if (profile.accessMode() == null) {
            errors.add(issue(profile.id(), "ACCESS_MODE_MISSING", "Access mode is required"));
        }
        if (profile.capabilities() == null) {
            errors.add(issue(profile.id(), "CAPABILITIES_MISSING", "Capabilities are required"));
        }
        if (profile.securityPolicy() == null) {
            errors.add(issue(profile.id(), "SECURITY_POLICY_MISSING", "Security policy is required"));
        }

        if (!errors.isEmpty()) {
            return StorageDeliveryProfileValidationResult.invalid(profile.id(), errors);
        }

        validateAccessModeCapabilities(profile, errors);
        validateLifecycle(profile, errors, warnings);
        validateSecurity(profile, errors);
        validatePreviewR2(profile, errors);
        validateLabDesignProfiles(profile, errors);

        return errors.isEmpty()
            ? StorageDeliveryProfileValidationResult.valid(profile.id())
            : StorageDeliveryProfileValidationResult.invalid(profile.id(), errors);
    }

    public StorageDeliveryProfileValidationResult validateAll(Collection<StorageDeliveryProfile> profiles) {
        List<StorageDeliveryProfileValidationIssue> errors = new ArrayList<>();

        Set<String> ids = new HashSet<>();
        boolean hasPreviewR2 = false;
        for (var profile : profiles) {
            if (profile.id() != null) {
                if (!ids.add(profile.id().value())) {
                    errors.add(issue(profile.id(), "DUPLICATE_PROFILE_ID", "Duplicate profile ID: " + profile.id().value()));
                }
                if (StorageDeliveryProfileId.PREVIEW_R2_SIGNED_URL.equals(profile.id())) {
                    hasPreviewR2 = true;
                }
            }
            var result = validate(profile);
            errors.addAll(result.errors());
        }

        if (!hasPreviewR2) {
            errors.add(issue(null, "DEFAULT_PROFILE_MUST_BE_PREVIEW_R2", "Catalog must contain preview-r2-signed-url profile"));
        }

        return errors.isEmpty()
            ? StorageDeliveryProfileValidationResult.valid(null)
            : StorageDeliveryProfileValidationResult.invalid(null, errors);
    }

    private void validateAccessModeCapabilities(StorageDeliveryProfile profile, List<StorageDeliveryProfileValidationIssue> errors) {
        var caps = profile.capabilities();
        var mode = profile.accessMode();

        if (mode == StorageAccessMode.SIGNED_URL && !caps.presignRead()) {
            errors.add(issue(profile.id(), "SIGNED_URL_REQUIRES_PRESIGN_READ", "SIGNED_URL profile must have presignRead=true"));
        }
        if (mode == StorageAccessMode.INTERNAL_STREAM && !caps.internalStream()) {
            errors.add(issue(profile.id(), "INTERNAL_STREAM_REQUIRES_INTERNAL_STREAM", "INTERNAL_STREAM profile must have internalStream=true"));
        }
        if (mode == StorageAccessMode.EXTERNAL_BUCKET && !caps.externalBucket()) {
            errors.add(issue(profile.id(), "EXTERNAL_BUCKET_REQUIRES_EXTERNAL_BUCKET", "EXTERNAL_BUCKET profile must have externalBucket=true"));
        }
        if (mode == StorageAccessMode.EXPORT_BUNDLE && !caps.exportBundle()) {
            errors.add(issue(profile.id(), "EXPORT_BUNDLE_REQUIRES_EXPORT_BUNDLE", "EXPORT_BUNDLE profile must have exportBundle=true"));
        }
    }

    private void validateLifecycle(StorageDeliveryProfile profile, List<StorageDeliveryProfileValidationIssue> errors, List<StorageDeliveryProfileValidationIssue> warnings) {
        var status = profile.status();

        if (profile.runtimeSelectable() && !profile.enabled()) {
            errors.add(issue(profile.id(), "RUNTIME_SELECTABLE_REQUIRES_ENABLED", "runtimeSelectable requires enabled=true"));
        }
        if (profile.runtimeSelectable() && status != StorageDeliveryProfileStatus.PREVIEW_VERIFIED && status != StorageDeliveryProfileStatus.VERIFIED) {
            errors.add(issue(profile.id(), "RUNTIME_SELECTABLE_STATUS_NOT_ALLOWED", "runtimeSelectable requires PREVIEW_VERIFIED or VERIFIED status"));
        }
        if (status == StorageDeliveryProfileStatus.LAB_ONLY && profile.runtimeSelectable()) {
            errors.add(issue(profile.id(), "LAB_PROFILE_CANNOT_BE_RUNTIME_SELECTABLE", "LAB_ONLY profile cannot be runtimeSelectable"));
        }
        if (status == StorageDeliveryProfileStatus.DESIGN_ONLY) {
            if (profile.runtimeSelectable()) {
                errors.add(issue(profile.id(), "DESIGN_PROFILE_CANNOT_BE_RUNTIME_SELECTABLE", "DESIGN_ONLY profile cannot be runtimeSelectable"));
            }
            if (profile.enabled()) {
                errors.add(issue(profile.id(), "DISABLED_PROFILE_CANNOT_BE_ENABLED", "DESIGN_ONLY profile cannot be enabled"));
            }
        }
        if (status == StorageDeliveryProfileStatus.DISABLED) {
            if (profile.enabled()) {
                errors.add(issue(profile.id(), "DISABLED_PROFILE_CANNOT_BE_ENABLED", "DISABLED profile cannot be enabled"));
            }
            if (profile.runtimeSelectable()) {
                errors.add(issue(profile.id(), "RUNTIME_SELECTABLE_STATUS_NOT_ALLOWED", "DISABLED profile cannot be runtimeSelectable"));
            }
        }
    }

    private void validateSecurity(StorageDeliveryProfile profile, List<StorageDeliveryProfileValidationIssue> errors) {
        var sec = profile.securityPolicy();
        var mode = profile.accessMode();

        if (mode == StorageAccessMode.SIGNED_URL) {
            if (sec.persistSignedUrl()) {
                errors.add(issue(profile.id(), "SIGNED_URL_MUST_NOT_BE_PERSISTED", "Signed URLs must not be persisted"));
            }
            if (sec.exposeBucket()) {
                errors.add(issue(profile.id(), "USER_FACING_MUST_NOT_EXPOSE_BUCKET", "Bucket must not be exposed"));
            }
            if (sec.exposeObjectKey()) {
                errors.add(issue(profile.id(), "USER_FACING_MUST_NOT_EXPOSE_OBJECT_KEY", "Object key must not be exposed"));
            }
            if (sec.exposeStorageReference()) {
                errors.add(issue(profile.id(), "USER_FACING_MUST_NOT_EXPOSE_STORAGE_REFERENCE", "StorageReference must not be exposed"));
            }
        }

        if (sec.userFacing()) {
            if (sec.exposeStorageReference()) {
                errors.add(issue(profile.id(), "USER_FACING_MUST_NOT_EXPOSE_STORAGE_REFERENCE", "User-facing profile must not expose StorageReference"));
            }
            if (sec.exposeBucket()) {
                errors.add(issue(profile.id(), "USER_FACING_MUST_NOT_EXPOSE_BUCKET", "User-facing profile must not expose bucket"));
            }
            if (sec.exposeObjectKey()) {
                errors.add(issue(profile.id(), "USER_FACING_MUST_NOT_EXPOSE_OBJECT_KEY", "User-facing profile must not expose object key"));
            }
            if (sec.exposeLocalPath()) {
                errors.add(issue(profile.id(), "USER_FACING_MUST_NOT_EXPOSE_LOCAL_PATH", "User-facing profile must not expose local path"));
            }
            if (sec.persistSignedUrl()) {
                errors.add(issue(profile.id(), "SIGNED_URL_MUST_NOT_BE_PERSISTED", "User-facing profile must not persist signed URLs"));
            }
            if (!sec.requireTenantProjectScope()) {
                errors.add(issue(profile.id(), "USER_FACING_REQUIRES_TENANT_PROJECT_SCOPE", "User-facing profile must require tenant/project scope"));
            }
        }

        if (mode == StorageAccessMode.LOCAL_PATH && sec.userFacing()) {
            errors.add(issue(profile.id(), "LOCAL_PATH_NOT_USER_FACING", "LOCAL_PATH profile must not be user-facing"));
        }
    }

    private void validatePreviewR2(StorageDeliveryProfile profile, List<StorageDeliveryProfileValidationIssue> errors) {
        if (!StorageDeliveryProfileId.PREVIEW_R2_SIGNED_URL.equals(profile.id())) return;

        if (profile.provider() != StorageProviderType.S3_COMPATIBLE) {
            errors.add(issue(profile.id(), "PREVIEW_R2_PROFILE_INVALID_PROVIDER", "Preview R2 profile must use S3_COMPATIBLE provider"));
        }
        if (profile.backend() != StorageBackendType.R2) {
            errors.add(issue(profile.id(), "PREVIEW_R2_PROFILE_INVALID_BACKEND", "Preview R2 profile must use R2 backend"));
        }
        if (profile.accessMode() != StorageAccessMode.SIGNED_URL) {
            errors.add(issue(profile.id(), "PREVIEW_R2_PROFILE_INVALID_ACCESS_MODE", "Preview R2 profile must use SIGNED_URL access mode"));
        }
        if (!profile.enabled()) {
            errors.add(issue(profile.id(), "PREVIEW_R2_PROFILE_MUST_BE_ENABLED", "Preview R2 profile must be enabled"));
        }
        if (!profile.runtimeSelectable()) {
            errors.add(issue(profile.id(), "PREVIEW_R2_PROFILE_MUST_BE_RUNTIME_SELECTABLE", "Preview R2 profile must be runtimeSelectable"));
        }
        if (!profile.capabilities().presignRead()) {
            errors.add(issue(profile.id(), "PREVIEW_R2_PROFILE_MUST_SUPPORT_PRESIGN", "Preview R2 profile must support presign"));
        }
    }

    private void validateLabDesignProfiles(StorageDeliveryProfile profile, List<StorageDeliveryProfileValidationIssue> errors) {
        var id = profile.id();
        if (id == null) return;

        if (StorageDeliveryProfileId.LAB_OPENDAL_FS_INTERNAL.equals(id)) {
            if (profile.enabled()) {
                errors.add(issue(id, "LAB_PROFILE_CANNOT_BE_RUNTIME_SELECTABLE", "OpenDAL lab profile must be disabled"));
            }
            if (profile.runtimeSelectable()) {
                errors.add(issue(id, "LAB_PROFILE_CANNOT_BE_RUNTIME_SELECTABLE", "OpenDAL lab profile cannot be runtimeSelectable"));
            }
        }

        if (StorageDeliveryProfileId.LAB_RUSTFS_S3_SIGNED_URL.equals(id)) {
            if (profile.enabled()) {
                errors.add(issue(id, "LAB_PROFILE_CANNOT_BE_RUNTIME_SELECTABLE", "RustFS lab profile must be disabled"));
            }
        }

        if (StorageDeliveryProfileId.CUSTOMER_OWNED_S3_EXTERNAL_BUCKET.equals(id)) {
            if (profile.enabled()) {
                errors.add(issue(id, "DESIGN_PROFILE_CANNOT_BE_RUNTIME_SELECTABLE", "Customer-owned profile must be disabled"));
            }
        }
    }

    private StorageDeliveryProfileValidationIssue issue(StorageDeliveryProfileId profileId, String code, String message) {
        return new StorageDeliveryProfileValidationIssue(profileId, code, message, StorageDeliveryProfileValidationSeverity.ERROR);
    }
}
