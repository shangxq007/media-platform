package com.example.platform.storage.contract.validation;
import com.example.platform.storage.contract.error.StorageError;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.identity.StorageObjectLocation;
import com.example.platform.storage.contract.namespace.StoragePlacementPolicy;
import com.example.platform.storage.contract.namespace.StoragePlacementValidator;
import com.example.platform.storage.contract.provider.StorageProviderCapabilities;
import com.example.platform.storage.contract.replica.ReplicaState;
import com.example.platform.storage.contract.replica.StorageReplicaStateMachine;
import com.example.platform.storage.contract.write.StorageWriteSession;
import java.util.*;
public final class UnifiedStorageSemanticsValidator {
    private UnifiedStorageSemanticsValidator() {}
    public static ValidationResult validate(StorageValidationModel model) {
        List<StorageError.Error> errors = new ArrayList<>();
        validateObjects(model, errors);
        validateReplicas(model, errors);
        validateWriteSessions(model, errors);
        validateLocations(model, errors);
        return new ValidationResult(errors);
    }
    private static void validateObjects(StorageValidationModel model, List<StorageError.Error> errors) {
        Set<StorageObjectId> seen = new HashSet<>();
        for (StorageObjectId obj : model.objects()) {
            if (!seen.add(obj)) {
                errors.add(StorageError.Error.builder(StorageError.ErrorCode.STORAGE_OBJECT_ALREADY_EXISTS)
                    .objectId(obj.value()).entityType("STORAGE_OBJECT").expected("unique").actual("duplicate").build());
            }
        }
    }
    private static void validateReplicas(StorageValidationModel model, List<StorageError.Error> errors) {
        for (var replica : model.replicas()) {
            if (replica.state() == ReplicaState.AVAILABLE && replica.committedDigest() == null) {
                errors.add(StorageError.Error.builder(StorageError.ErrorCode.STORAGE_REPLICA_NOT_AVAILABLE)
                    .replicaId(replica.replicaId().value()).entityType("REPLICA")
                    .expected("digest present for AVAILABLE").actual("null").build());
            }
        }
    }
    private static void validateWriteSessions(StorageValidationModel model, List<StorageError.Error> errors) {
        for (StorageWriteSession ws : model.writeSessions()) {
            if (ws.expectedLength() < 0) {
                errors.add(StorageError.Error.builder(StorageError.ErrorCode.STORAGE_CONTENT_LENGTH_MISMATCH)
                    .writeSessionId(ws.writeSessionId()).entityType("WRITE_SESSION")
                    .expected(">= 0").actual(String.valueOf(ws.expectedLength())).build());
            }
        }
    }
    private static void validateLocations(StorageValidationModel model, List<StorageError.Error> errors) {
        // v1: locations are validated structurally; digest immutability enforced by replica records
    }
    public static final class ValidationResult {
        private final List<StorageError.Error> errors;
        ValidationResult(List<StorageError.Error> errors) { this.errors = List.copyOf(errors); }
        public List<StorageError.Error> errors() { return errors; }
        public boolean isValid() { return errors.isEmpty(); }
        public int errorCount() { return errors.size(); }
    }
}
