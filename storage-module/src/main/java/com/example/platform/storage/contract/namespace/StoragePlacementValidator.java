package com.example.platform.storage.contract.namespace;
import com.example.platform.storage.contract.identity.StorageObjectLocation;
import com.example.platform.storage.contract.error.StorageError;
import java.util.ArrayList;
import java.util.List;
public final class StoragePlacementValidator {
    private StoragePlacementValidator() {}
    public static List<StorageError.Error> validatePlacement(StorageObjectLocation location, StoragePlacementPolicy policy) {
        List<StorageError.Error> errors = new ArrayList<>();
        if (location.region() != null && !location.region().isBlank() && !policy.allowedRegions().contains(location.region())) {
            errors.add(StorageError.Error.builder(StorageError.ErrorCode.STORAGE_PLACEMENT_POLICY_VIOLATION)
                .providerId(location.providerId().value())
                .expected("region in " + policy.allowedRegions())
                .actual(location.region())
                .operation("validatePlacement")
                .build());
        }
        return errors;
    }
    public static List<StorageError.Error> validateTenantIsolation(StorageNamespace ns1, StorageNamespace ns2) {
        List<StorageError.Error> errors = new ArrayList<>();
        if (!ns1.tenantId().equals(ns2.tenantId())) {
            errors.add(StorageError.Error.builder(StorageError.ErrorCode.STORAGE_CROSS_TENANT_ACCESS_DENIED)
                .expected(ns1.tenantId())
                .actual(ns2.tenantId())
                .operation("validateTenantIsolation")
                .build());
        }
        return errors;
    }
}
