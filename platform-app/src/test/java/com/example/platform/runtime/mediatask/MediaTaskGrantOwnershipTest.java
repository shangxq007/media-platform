package com.example.platform.runtime.mediatask;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.workerfabric.domain.AssignmentGrant;
import com.example.platform.workerfabric.reuse.AdmissionCapacityRetryableException;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class MediaTaskGrantOwnershipTest {
    final ExecutableTaskId task=new ExecutableTaskId("a".repeat(64));
    AssignmentGrant grant(String job,long attempt) {
        var grant=mock(AssignmentGrant.class);when(grant.requestWorkId()).thenReturn(FirstRenderGrantMinter.requestWorkId(job,task,attempt));return grant;
    }
    @Test void activeGrantBelongsToItsExactJobRequest() {
        assertDoesNotThrow(()->MediaTaskActivity.requireOwnedGrant(grant("job-a",1),"job-a",task,1));
        assertThrows(AdmissionCapacityRetryableException.class,()->MediaTaskActivity.requireOwnedGrant(grant("job-a",1),"job-b",task,1));
    }
    @Test void changedActivityAttemptCannotReclaimOldLiveGrant() {
        assertThrows(AdmissionCapacityRetryableException.class,()->MediaTaskActivity.requireOwnedGrant(grant("job-a",1),"job-a",task,2));
    }
}
