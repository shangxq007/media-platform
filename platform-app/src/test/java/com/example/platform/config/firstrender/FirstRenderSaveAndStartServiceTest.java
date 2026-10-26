package com.example.platform.config.firstrender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.execution.binding.BoundGraphInputStore;
import com.example.platform.execution.binding.BoundGraphInputs;
import com.example.platform.execution.binding.BoundGraphReference;
import com.example.platform.render.domain.renderplan.RenderRequestId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** Phase 6 acceptance for save → start ordering, reference passthrough and failure propagation. */
class FirstRenderSaveAndStartServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String JOB = "rj_1";
    private static final String WORKFLOW_ID = "render:" + TENANT + ":" + JOB;

    private BoundGraphInputStore store;
    private FirstRenderWorkflowStarter starter;
    private FirstRenderSaveAndStartService service;
    private BoundGraphReference savedReference;
    private FirstRenderPlanningAndBindingResult planning;

    @BeforeEach
    void setUp() {
        store = mock(BoundGraphInputStore.class);
        starter = mock(FirstRenderWorkflowStarter.class);
        service = new FirstRenderSaveAndStartService(store, starter, mock(com.example.platform.render.api.GraphRenderLifecycle.class));
        savedReference = new BoundGraphReference(
                TENANT, JOB, "render-binding-inputs/" + TENANT + "/" + JOB,
                "a".repeat(64), "b".repeat(64));
        planning = new FirstRenderPlanningAndBindingResult(
                TENANT, JOB, new RenderRequestId("req-1"), mock(BoundGraphInputs.class));
    }

    @Test
    void savesFirstThenStartsWithTheReturnedReferenceUnmodified() {
        when(store.save(any(), any(), any())).thenReturn(savedReference);
        when(starter.start(any(), any(), any())).thenReturn(true);

        FirstRenderStartResult result = service.start(planning);

        // frozen workflow id
        assertThat(result.workflowId()).isEqualTo(WORKFLOW_ID);
        assertThat(result.workflowStarted()).isTrue();

        // save is called with the Phase 5 bound-graph inputs + tenant + render job id
        verify(store).save(planning.boundGraphInputs(), TENANT, JOB);
        // start receives the EXACT reference returned by save (not rebuilt)
        verify(starter).start(savedReference, TENANT, WORKFLOW_ID);

        // ordering: save strictly before start
        InOrder order = inOrder(store, starter);
        order.verify(store).save(any(), any(), any());
        order.verify(starter).start(any(), any(), any());
    }

    @Test
    void doesNotStartWhenSaveFails() {
        when(store.save(any(), any(), any())).thenThrow(new IllegalStateException("save failed"));

        assertThatThrownBy(() -> service.start(planning))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("save failed");

        verify(starter, never()).start(any(), any(), any());
    }

    @Test
    void propagatesFirstStartFailureWithoutCompensation() {
        when(store.save(any(), any(), any())).thenReturn(savedReference);
        when(starter.start(any(), any(), any())).thenThrow(new IllegalStateException("start failed"));

        assertThatThrownBy(() -> service.start(planning))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("start failed");

        // no compensation: save is called exactly once; nothing else is invoked on the store
        verify(store).save(any(), any(), any());
        verify(store, never()).load(any());
    }

    @Test
    void alreadyStartedIsReportedAsStartedByTheStarter() {
        // The starter returns true for a fresh start AND for an already-existing workflow.
        when(store.save(any(), any(), any())).thenReturn(savedReference);
        when(starter.start(any(), any(), any())).thenReturn(true);

        assertThat(service.start(planning).workflowStarted()).isTrue();
    }
}
