package com.example.platform.artifact.infrastructure;

import com.example.platform.artifact.contract.ArtifactUploadAdmissionPort;
import com.example.platform.artifact.domain.ArtifactCommitService;
import com.example.platform.storage.api.StoragePlacementQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ArtifactUploadPreparationProofTest {
    @Test void callerCannotSupplyAProofThatSkippedStorageAndMediaValidation() {
        var service = new ArtifactUploadAdmissionService(mock(StoragePlacementQuery.class),
                mock(ArtifactCommitService.class), mock(ObjectProvider.class),
                mock(ArtifactSourceFactsStore.class), mock(PlatformTransactionManager.class));
        assertThrows(IllegalArgumentException.class, () -> service.admitPrepared(
                mock(ArtifactUploadAdmissionPort.PreparedSource.class), null));
    }
}
