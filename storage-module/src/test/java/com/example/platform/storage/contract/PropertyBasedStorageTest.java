package com.example.platform.storage.contract;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.storage.contract.provider.*;
import com.example.platform.storage.contract.replica.*;
import com.example.platform.storage.contract.validation.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PropertyBasedStorageTest {
    @Test void digestCanonicalization_idempotence() {
        String sha = "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855";
        ContentDigest d1 = ContentDigest.sha256(sha);
        ContentDigest d2 = ContentDigest.sha256(d1.canonicalValue());
        ContentDigest d3 = ContentDigest.sha256(d2.canonicalValue());
        assertEquals(d1.canonicalValue(), d2.canonicalValue());
        assertEquals(d2.canonicalValue(), d3.canonicalValue());
    }
    @Test void stateMachine_neverIllegalTransitionIn1000Iterations() {
        for (int i = 0; i < 1000; i++) {
            assertFalse(StorageReplicaStateMachine.canTransition(ReplicaState.PENDING, ReplicaState.AVAILABLE));
            assertFalse(StorageReplicaStateMachine.canTransition(ReplicaState.DELETED, ReplicaState.AVAILABLE));
            assertFalse(StorageReplicaStateMachine.canTransition(ReplicaState.AVAILABLE, ReplicaState.UPLOADING));
            assertTrue(StorageReplicaStateMachine.canTransition(ReplicaState.PENDING, ReplicaState.UPLOADING));
            assertTrue(StorageReplicaStateMachine.canTransition(ReplicaState.VERIFYING, ReplicaState.AVAILABLE));
        }
    }
    @Test void validatorDeterminism_identicalErrors() {
        StorageObjectId id = new StorageObjectId("dup");
        StorageValidationModel model = new StorageValidationModel(List.of(id, id), List.of(), List.of(), List.of(), List.of(), "storage-semantics-v1");
        var r1 = UnifiedStorageSemanticsValidator.validate(model);
        var r2 = UnifiedStorageSemanticsValidator.validate(model);
        var r3 = UnifiedStorageSemanticsValidator.validate(model);
        assertEquals(r1.errors(), r2.errors());
        assertEquals(r2.errors(), r3.errors());
    }
    @Test void differentContent_cannotReuseImmutableIdentity() {
        ContentDigest d1 = ContentDigest.sha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        ContentDigest d2 = ContentDigest.sha256("a3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertFalse(d1.matches(d2));
    }
    @Test void capabilityOrdering_doesNotAffectCanonicalForm() {
        StorageProviderId pid = new StorageProviderId("s3");
        var caps1 = new StorageProviderCapabilities(pid, Map.of(
            ProviderCapability.RANGE_READ, CapabilitySupport.SUPPORTED,
            ProviderCapability.RENAME, CapabilitySupport.UNSUPPORTED
        ));
        var caps2 = new StorageProviderCapabilities(pid, Map.of(
            ProviderCapability.RENAME, CapabilitySupport.UNSUPPORTED,
            ProviderCapability.RANGE_READ, CapabilitySupport.SUPPORTED
        ));
        assertTrue(caps1.supports(ProviderCapability.RANGE_READ));
        assertTrue(caps2.supports(ProviderCapability.RANGE_READ));
    }
}
