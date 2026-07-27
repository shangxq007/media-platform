package com.example.platform.render.domain.storage;
import com.example.platform.render.domain.storage.replica.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplicaStateMachineTest {
    @Test void pendingToUploading_isValid() {
        assertTrue(StorageReplicaStateMachine.canTransition(ReplicaState.PENDING, ReplicaState.UPLOADING));
    }
    @Test void pendingToVerifying_isIllegal() {
        assertFalse(StorageReplicaStateMachine.canTransition(ReplicaState.PENDING, ReplicaState.VERIFYING));
    }
    @Test void verifyingToAvailable_isValid() {
        assertTrue(StorageReplicaStateMachine.canTransition(ReplicaState.VERIFYING, ReplicaState.AVAILABLE));
    }
    @Test void deletedToAvailable_isIllegal() {
        assertFalse(StorageReplicaStateMachine.canTransition(ReplicaState.DELETED, ReplicaState.AVAILABLE));
    }
    @Test void availableToUploadingOverwrite_isIllegal() {
        assertFalse(StorageReplicaStateMachine.canTransition(ReplicaState.AVAILABLE, ReplicaState.UPLOADING));
    }
    @Test void failedToAvailableWithoutNewAttempt_isIllegal() {
        assertFalse(StorageReplicaStateMachine.canTransition(ReplicaState.FAILED, ReplicaState.AVAILABLE));
    }
    @Test void failedToUploading_isValid() {
        assertTrue(StorageReplicaStateMachine.canTransition(ReplicaState.FAILED, ReplicaState.UPLOADING));
    }
    @Test void deletingToDeleted_isValid() {
        assertTrue(StorageReplicaStateMachine.canTransition(ReplicaState.DELETING, ReplicaState.DELETED));
    }
    @Test void deterministicOrdering() {
        // Run 100 times to check stability
        for (int i = 0; i < 100; i++) {
            assertTrue(StorageReplicaStateMachine.canTransition(ReplicaState.PENDING, ReplicaState.UPLOADING));
            assertFalse(StorageReplicaStateMachine.canTransition(ReplicaState.DELETED, ReplicaState.AVAILABLE));
        }
    }
}
