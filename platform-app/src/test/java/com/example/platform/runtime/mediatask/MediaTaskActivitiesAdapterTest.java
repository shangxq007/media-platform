package com.example.platform.runtime.mediatask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.execution.binding.BoundGraphInputs;
import com.example.platform.execution.binding.BoundGraphReference;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutableTaskGraphDigest;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import com.example.platform.workflow.temporal.mediatask.MediaTaskWorkflowResult;
import com.example.platform.workflow.temporal.mediatask.PreparedTaskRef;
import java.util.List;
import com.example.platform.render.api.GraphRenderLifecycle;
import com.example.platform.workerfabric.domain.CompletedTaskOutputQuery;
import com.example.platform.artifact.app.ArtifactApplicationQuery;
import static org.mockito.ArgumentMatchers.*;
import org.junit.jupiter.api.Test;

/**
 * P2-5b-2b-2a: the Temporal adapter reduces the in-process prepared graph to the stable
 * {@link PreparedTaskRef} and delegates execution to the activity unchanged.
 */
class MediaTaskActivitiesAdapterTest {

    private static final String DIGEST = "a".repeat(64);
    private static final BoundGraphReference REFERENCE = new BoundGraphReference(
            "tenant-1", "render-job-1", "render-binding-inputs/tenant-1/render-job-1",
            DIGEST, DIGEST);

    private static final GraphRenderLifecycle.Execution EXECUTION=new GraphRenderLifecycle.Execution("tenant-1","project-1","render-job-1",REFERENCE.planRef(),DIGEST,DIGEST,"attempt-1",1,DIGEST);
    private final GraphRenderLifecycle lifecycle=mock(GraphRenderLifecycle.class);
    private final CompletedTaskOutputQuery completed=mock(CompletedTaskOutputQuery.class);
    private final ArtifactApplicationQuery artifacts=mock(ArtifactApplicationQuery.class);

    @Test
    void prepareTaskReducesThePreparedGraphToAStableReference() {
        MediaTaskActivity activity = mock(MediaTaskActivity.class);
        ProviderBoundExecutableTaskGraph graph = mock(ProviderBoundExecutableTaskGraph.class,org.mockito.Mockito.RETURNS_DEEP_STUBS);
        var unit=mock(com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit.class);
        var output=mock(com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration.class);
        var outputId=new com.example.platform.execution.domain.ExecutionOutputId("final-master");
        when(output.outputId()).thenReturn(outputId);
        when(output.finalArtifactExpectations()).thenReturn(List.of(new com.example.platform.render.domain.renderplan.RenderArtifactReference.FinalArtifactExpectation(com.example.platform.render.domain.renderplan.RenderOutputRole.RENDER_MASTER)));
        when(unit.typedOutputs()).thenReturn(List.of(output));when(graph.sourcePhysicalPlan().units()).thenReturn(List.of(unit));
        when(lifecycle.begin(anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(EXECUTION);
        ExecutableTask task = mock(ExecutableTask.class);
        when(task.authoritativeOutputIds()).thenReturn(List.of(outputId));
        when(task.id()).thenReturn(new com.example.platform.execution.taskgraph.ExecutableTaskId(DIGEST));
        when(graph.tasks()).thenReturn(List.of(task));
        when(activity.prepareTask(REFERENCE, "tenant-1")).thenReturn(new PreparedTask(
                graph, mock(BoundGraphInputs.class), new ExecutableTaskGraphDigest(DIGEST)));
        MediaTaskActivitiesAdapter adapter = new MediaTaskActivitiesAdapter(activity,lifecycle,completed,artifacts,mock(org.springframework.transaction.PlatformTransactionManager.class));

        PreparedTaskRef prepared = adapter.prepareTask(REFERENCE, "tenant-1");

        assertThat(prepared.tenantId()).isEqualTo("tenant-1");
        assertThat(prepared.renderJobId()).isEqualTo("render-job-1");
        assertThat(prepared.planRef()).isEqualTo(REFERENCE.planRef());
        assertThat(prepared.planDigest()).isEqualTo(DIGEST);
        assertThat(prepared.expectedExecutableTaskGraphDigest()).isEqualTo(DIGEST);
        assertThat(prepared.executableTaskIds()).containsExactly(DIGEST);
    }

    @Test
    void executeTaskDelegatesToTheActivityUnchanged() {
        MediaTaskActivity activity = mock(MediaTaskActivity.class);
        MediaTaskActivitiesAdapter adapter = new MediaTaskActivitiesAdapter(activity,lifecycle,completed,artifacts,mock(org.springframework.transaction.PlatformTransactionManager.class));
        PreparedTaskRef prepared = new PreparedTaskRef(
                "tenant-1", "render-job-1", REFERENCE.planRef(), DIGEST, DIGEST, List.of(DIGEST),EXECUTION);
        MediaTaskWorkflowResult result = new MediaTaskWorkflowResult(
                "tenant-1", "render-job-1", DIGEST, DIGEST, List.of(DIGEST),
                MediaTaskWorkflowResult.STATUS_EXECUTED);
        when(activity.executeTask(org.mockito.ArgumentMatchers.eq(prepared),org.mockito.ArgumentMatchers.eq("tenant-1"),any(),any(),any(),any(),any())).thenReturn(result);

        assertThat(adapter.executeTask(prepared, "tenant-1")).isSameAs(result);
        verify(activity).executeTask(org.mockito.ArgumentMatchers.eq(prepared),org.mockito.ArgumentMatchers.eq("tenant-1"),any(),any(),any(),any(),any());
    }

    @Test
    void resumesOnlyAvailableArtifactsForTheSameJobWithCurrentGenerationProof() {
        var activity = mock(MediaTaskActivity.class);
        var adapter = new MediaTaskActivitiesAdapter(activity, lifecycle, completed, artifacts,
                mock(org.springframework.transaction.PlatformTransactionManager.class));
        String intermediate = "b".repeat(64);
        var prepared = new PreparedTaskRef("tenant-1", "render-job-1", REFERENCE.planRef(), DIGEST, DIGEST,
                List.of(intermediate, DIGEST), EXECUTION);
        var id = new com.example.platform.shared.identity.ArtifactId("finished-intermediate");
        var digest = com.example.platform.shared.digest.ContentDigest.sha256("c".repeat(64));
        var available = mock(com.example.platform.artifact.domain.Artifact.class);
        when(available.artifactId()).thenReturn(id);
        when(available.contentDigest()).thenReturn(digest);
        when(available.state()).thenReturn(com.example.platform.artifact.domain.ArtifactState.AVAILABLE);
        when(completed.find(intermediate)).thenReturn(java.util.Optional.of(
                new CompletedTaskOutputQuery.Output(intermediate, "owner", 1, id.value())));
        when(artifacts.findArtifact(new com.example.platform.artifact.app.ArtifactScope("tenant-1", "project-1", "render-job-1"), id))
                .thenReturn(java.util.Optional.of(available));
        adapter.executeTask(prepared, "tenant-1");
        verify(activity).executeTask(org.mockito.ArgumentMatchers.eq(prepared), org.mockito.ArgumentMatchers.eq("tenant-1"),
                any(), any(), any(), any(), org.mockito.ArgumentMatchers.eq(java.util.Map.of(
                        new com.example.platform.execution.taskgraph.ExecutableTaskId(intermediate),
                        new com.example.platform.artifact.app.ArtifactPinService.ArtifactPin(id, digest))));
    }

    @Test
    void acceptedCompletionRemainsIdempotentAfterAnotherJobAdvancesNativeGeneration() {
        var activity=mock(MediaTaskActivity.class);var adapter=new MediaTaskActivitiesAdapter(activity,lifecycle,completed,artifacts,mock(org.springframework.transaction.PlatformTransactionManager.class));
        var output=new GraphRenderLifecycle.Output(DIGEST,"old-native-attempt",1,"accepted-artifact");
        var prepared=new PreparedTaskRef("tenant-1","render-job-1",REFERENCE.planRef(),DIGEST,DIGEST,List.of(DIGEST),EXECUTION);
        var result=new MediaTaskWorkflowResult("tenant-1","render-job-1",DIGEST,DIGEST,List.of(DIGEST),MediaTaskWorkflowResult.STATUS_EXECUTED,output);
        when(lifecycle.find("tenant-1","project-1","render-job-1")).thenReturn(java.util.Optional.of(new GraphRenderLifecycle.Status(EXECUTION,"COMPLETED","accepted-artifact",null,output)));
        adapter.completeTask(prepared,result,"tenant-1");
        verify(lifecycle).complete(EXECUTION,output);org.mockito.Mockito.verifyNoInteractions(completed);
        assertThat(com.example.platform.shared.web.TenantContext.get()).isNull();
    }

    @Test void exactCommittedFinalResponseRecoversWithoutHistoricalInputs() {
        var activity=mock(MediaTaskActivity.class);var adapter=new MediaTaskActivitiesAdapter(activity,lifecycle,completed,artifacts,mock(org.springframework.transaction.PlatformTransactionManager.class));
        var output=new GraphRenderLifecycle.Output(DIGEST,"native-attempt",2,"current-final");
        var prepared=new PreparedTaskRef("tenant-1","render-job-1",REFERENCE.planRef(),DIGEST,DIGEST,List.of(DIGEST),EXECUTION);
        when(lifecycle.find("tenant-1","project-1","render-job-1")).thenReturn(java.util.Optional.of(new GraphRenderLifecycle.Status(EXECUTION,"EXECUTING",null,null,output)));
        when(completed.find(DIGEST)).thenReturn(java.util.Optional.of(new CompletedTaskOutputQuery.Output(DIGEST,"native-attempt",2,"current-final")));
        var finalArtifact=new com.example.platform.artifact.domain.Artifact(new com.example.platform.shared.identity.ArtifactId("current-final"),"tenant-1",com.example.platform.shared.digest.ContentDigest.sha256("d".repeat(64)),10,
                com.example.platform.artifact.domain.ArtifactMediaType.VIDEO,com.example.platform.artifact.domain.ArtifactKind.RENDER_MASTER,com.example.platform.artifact.domain.ArtifactState.AVAILABLE,1,java.time.Instant.EPOCH);
        when(artifacts.findArtifact(new com.example.platform.artifact.app.ArtifactScope("tenant-1","project-1","render-job-1"),new com.example.platform.shared.identity.ArtifactId("current-final"))).thenReturn(java.util.Optional.of(finalArtifact));
        assertThat(adapter.executeTask(prepared,"tenant-1").finalOutput()).isEqualTo(output);
        org.mockito.Mockito.verifyNoInteractions(activity);
    }

    @Test void conflictingPlanReferenceOrTaskInventoryFailsBeforeOwnerOrNativeQueries() {
        var activity=mock(MediaTaskActivity.class);var adapter=new MediaTaskActivitiesAdapter(activity,lifecycle,completed,artifacts,mock(org.springframework.transaction.PlatformTransactionManager.class));
        var p=new PreparedTaskRef("tenant-1","render-job-1","other-ref",DIGEST,DIGEST,List.of(DIGEST),EXECUTION);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->adapter.executeTask(p,"tenant-1"));
        var correct=new PreparedTaskRef("tenant-1","render-job-1",REFERENCE.planRef(),DIGEST,DIGEST,List.of(DIGEST),EXECUTION);
        var badResult=new MediaTaskWorkflowResult("tenant-1","render-job-1",DIGEST,DIGEST,List.of(),MediaTaskWorkflowResult.STATUS_EXECUTED,new GraphRenderLifecycle.Output(DIGEST,"attempt",1,"artifact"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->adapter.completeTask(correct,badResult,"tenant-1"));
        org.mockito.Mockito.verifyNoInteractions(activity,lifecycle,completed,artifacts);
    }
}
