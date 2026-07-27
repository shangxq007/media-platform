package com.example.platform.render.domain.storage;
import com.example.platform.render.domain.storage.validation.*;
import com.example.platform.render.domain.storage.identity.*;
import com.example.platform.render.domain.storage.namespace.*;
import com.example.platform.render.domain.storage.provider.*;
import com.example.platform.render.domain.storage.replica.*;
import com.example.platform.render.domain.storage.write.*;
import com.example.platform.render.domain.storage.digest.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class UnifiedStorageValidatorTest {
    @Test void emptyModel_isValid() {
        StorageValidationModel model = new StorageValidationModel(List.of(), List.of(), List.of(), List.of(), List.of(), "storage-semantics-v1");
        UnifiedStorageSemanticsValidator.ValidationResult result = UnifiedStorageSemanticsValidator.validate(model);
        assertTrue(result.isValid());
    }
    @Test void duplicateObjectId_detected() {
        StorageObjectId id = new StorageObjectId("obj-1");
        StorageValidationModel model = new StorageValidationModel(List.of(id, id), List.of(), List.of(), List.of(), List.of(), "storage-semantics-v1");
        UnifiedStorageSemanticsValidator.ValidationResult result = UnifiedStorageSemanticsValidator.validate(model);
        assertFalse(result.isValid());
        assertTrue(result.errors().size() >= 1);
    }
    @Test void replicaWithoutDigest_detected() {
        StorageObjectId objId = new StorageObjectId("obj-1");
        StorageReplicaId repId = new StorageReplicaId("rep-1");
        StorageProviderId provId = new StorageProviderId("s3");
        StorageNamespace ns = new StorageNamespace("t1", "p1", NamespaceClass.SOURCE, RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        StorageObjectLocation loc = new StorageObjectLocation(provId, ns, "bucket/key", null, "us-east-1");
        var replica = new StorageValidationModel.StorageReplicaRecord(repId, objId, loc, ReplicaState.AVAILABLE, null, 0);
        StorageValidationModel model = new StorageValidationModel(List.of(objId), List.of(replica), List.of(), List.of(), List.of(loc), "storage-semantics-v1");
        UnifiedStorageSemanticsValidator.ValidationResult result = UnifiedStorageSemanticsValidator.validate(model);
        assertFalse(result.isValid());
    }
    @Test void validator_pure_noSideEffects() {
        StorageValidationModel model = new StorageValidationModel(List.of(), List.of(), List.of(), List.of(), List.of(), "storage-semantics-v1");
        UnifiedStorageSemanticsValidator.validate(model);
        UnifiedStorageSemanticsValidator.validate(model);
        UnifiedStorageSemanticsValidator.validate(model);
        UnifiedStorageSemanticsValidator.ValidationResult result = UnifiedStorageSemanticsValidator.validate(model);
        assertTrue(result.isValid());
    }
    @Test void validator_inputImmutability() {
        java.util.ArrayList<StorageObjectId> mutableList = new java.util.ArrayList<>();
        mutableList.add(new StorageObjectId("obj-1"));
        StorageValidationModel model = new StorageValidationModel(mutableList, List.of(), List.of(), List.of(), List.of(), "storage-semantics-v1");
        UnifiedStorageSemanticsValidator.validate(model);
        // Mutate after validation
        mutableList.add(new StorageObjectId("obj-2"));
        // Model should have been immutable
        assertEquals(1, model.objects().size());
    }
}
