package com.example.platform.execution.admission;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The workflow-id bytes are a platform contract shared by the API cancel path and the worker
 * dispatch listener; changing them would orphan in-flight Temporal executions.
 */
class CapabilityExecutionWorkflowIdsTest {

    @Test
    void workflowIdKeepsTheCanonicalByteLayout() {
        assertThat(CapabilityExecutionWorkflowIds.workflowId("tenant-a", "exe-1", 7L))
                .isEqualTo("capability:tenant-a:exe-1:7");
    }

    @Test
    void generationIsPartOfTheIdentity() {
        assertThat(CapabilityExecutionWorkflowIds.workflowId("t", "e", 1L))
                .isNotEqualTo(CapabilityExecutionWorkflowIds.workflowId("t", "e", 2L));
    }
}
