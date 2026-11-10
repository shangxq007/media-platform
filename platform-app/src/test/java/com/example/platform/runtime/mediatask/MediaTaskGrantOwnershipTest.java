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
    @Test void postMintRereadRejectsOwnershipTakenByAnotherJob() throws Exception {
        var boundary=mock(com.example.platform.workerfabric.domain.AtomicAssignmentGrantBoundary.class);
        var minter=mock(FirstRenderGrantMinter.class);
        var catalog=mock(com.example.platform.providerplugin.ProviderPluginCatalog.class);
        var graph=mock(com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph.class);
        var executable=mock(com.example.platform.execution.taskgraph.ExecutableTask.class);
        var contribution=mock(com.example.platform.providerplugin.ProviderPluginContribution.class);
        when(catalog.find(executable.providerBindingPin())).thenReturn(java.util.Optional.of(contribution));
        when(boundary.findCurrentGrant(task)).thenReturn(java.util.Optional.empty(),java.util.Optional.of(grant("job-b",1)));
        when(minter.mint("job-a",task,graph,contribution,1L)).thenReturn(
                new FirstRenderGrantMinter.Result.Minted(FirstRenderGrantMinter.requestWorkId("job-a",task,1),grant("job-a",1)));
        var activity=new MediaTaskActivity(mock(com.example.platform.execution.binding.BoundGraphInputStore.class),boundary,
                mock(com.example.platform.workerfabric.reuse.RuntimeClosedLoopOrchestrator.class),null,minter,catalog);
        var method=MediaTaskActivity.class.getDeclaredMethod("currentGrantOrMint",String.class,ExecutableTaskId.class,
                com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph.class,
                com.example.platform.execution.taskgraph.ExecutableTask.class,long.class);
        method.setAccessible(true);
        var failure=assertThrows(java.lang.reflect.InvocationTargetException.class,
                ()->method.invoke(activity,"job-a",task,graph,executable,1L));
        assertInstanceOf(AdmissionCapacityRetryableException.class,failure.getCause());
        verify(minter,never()).requireLocalGrant(any());
    }
}
