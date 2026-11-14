package com.example.platform.config.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.execution.binding.BoundGraphReference;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Phase 6 acceptance for the Temporal starter's fail-closed client lookup and its
 * {@code WorkflowExecutionAlreadyStarted} classification (never swallowing unrelated failures).
 */
class TemporalRenderJobWorkflowStarterTest {

    @Test
    void missingTemporalClientFailsClosed() {
        @SuppressWarnings("unchecked")
        ObjectProvider<WorkflowClient> clients = mock(ObjectProvider.class);
        when(clients.getIfAvailable()).thenReturn(null);
        TemporalRenderJobWorkflowStarter starter = new TemporalRenderJobWorkflowStarter(clients);

        assertThatThrownBy(() -> starter.start(
                new BoundGraphReference("tenant-1", "rj_1", "ref", "a".repeat(64), "b".repeat(64)),
                "tenant-1", "render:tenant-1:rj_1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Temporal client unavailable");
    }

    @Test
    void freshStartIsClassifiedAsStarted() {
        assertThat(TemporalRenderJobWorkflowStarter.classifyStart(() -> { })).isTrue();
    }

    @Test
    void alreadyStartedIsClassifiedAsStarted() {
        WorkflowExecutionAlreadyStarted alreadyStarted = mock(WorkflowExecutionAlreadyStarted.class);
        assertThat(TemporalRenderJobWorkflowStarter.classifyStart(() -> {
            throw alreadyStarted;
        })).isTrue();
    }

    @Test
    void unrelatedFailuresAreNotSwallowed() {
        assertThatCode(() -> TemporalRenderJobWorkflowStarter.classifyStart(() -> { }))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> TemporalRenderJobWorkflowStarter.classifyStart(() -> {
            throw new IllegalStateException("unrelated start failure");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unrelated start failure");
    }
}
