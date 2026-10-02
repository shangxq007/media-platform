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

    @Test
    void prepareTaskReducesThePreparedGraphToAStableReference() {
        MediaTaskActivity activity = mock(MediaTaskActivity.class);
        ProviderBoundExecutableTaskGraph graph = mock(ProviderBoundExecutableTaskGraph.class);
        ExecutableTask task = mock(ExecutableTask.class);
        when(task.id()).thenReturn(new com.example.platform.execution.taskgraph.ExecutableTaskId(DIGEST));
        when(graph.tasks()).thenReturn(List.of(task));
        when(activity.prepareTask(REFERENCE, "tenant-1")).thenReturn(new PreparedTask(
                graph, mock(BoundGraphInputs.class), new ExecutableTaskGraphDigest(DIGEST)));
        MediaTaskActivitiesAdapter adapter = new MediaTaskActivitiesAdapter(activity);

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
        MediaTaskActivitiesAdapter adapter = new MediaTaskActivitiesAdapter(activity);
        PreparedTaskRef prepared = new PreparedTaskRef(
                "tenant-1", "render-job-1", REFERENCE.planRef(), DIGEST, DIGEST, List.of(DIGEST));
        MediaTaskWorkflowResult result = new MediaTaskWorkflowResult(
                "tenant-1", "render-job-1", DIGEST, DIGEST, List.of(DIGEST),
                MediaTaskWorkflowResult.STATUS_EXECUTED);
        when(activity.executeTask(prepared, "tenant-1")).thenReturn(result);

        assertThat(adapter.executeTask(prepared, "tenant-1")).isSameAs(result);
        verify(activity).executeTask(prepared, "tenant-1");
    }
}
