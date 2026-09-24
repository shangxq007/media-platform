package com.example.platform.execution.runtime;

import com.example.platform.execution.domain.ExecutionPlanId;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeExecutionRequestTest {
    private RuntimeExecutionRequest request() {
        return new RuntimeExecutionRequest("execution-1", new ExecutionPlanId("plan-1"), "capability", "1",
                "ExecutableTask", "ProviderExecutionOutput",
                new RuntimeExecutionRequest.Scope("tenant", "workspace", "actor"),
                RuntimeExecutionRequest.ExecutionMode.ASYNCHRONOUS,
                new RuntimeExecutionRequest.PlacementConstraints("NATIVE_PULL_WORKER", "local", Map.of()),
                new RuntimeExecutionRequest.RuntimeEligibilityEvidence("worker", "runtime", "evidence-1"), 1,
                new RuntimeExecutionRequest.Idempotency("key", "hash"),
                List.of(new RuntimeExecutionRequest.TypedReference("input", "MediaAsset", "1", "asset-1")),
                List.of(new RuntimeExecutionRequest.TypedReference("output", "ProviderExecutionOutput", "1", "output-1")));
    }
    @Test void carriesPlatformRuntimeFactsWithoutProviderImplementationFields() {
        var request = request();
        assertEquals("NATIVE_PULL_WORKER", request.placement().backend());
        assertEquals(1, request.ownershipGeneration());
        assertEquals("ProviderExecutionOutput", request.outputs().getFirst().contract());
        assertTrue(java.util.Arrays.stream(RuntimeExecutionRequest.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("process")));
    }
    @Test void rejectsMissingEligibilityAndNegativeGeneration() {
        assertThrows(IllegalArgumentException.class, () -> new RuntimeExecutionRequest(
                "execution", new ExecutionPlanId("plan"), "cap", "1", "in", "out",
                request().scope(), request().executionMode(), request().placement(), request().eligibility(), -1,
                request().idempotency(), request().inputs(), request().outputs()));
    }
}
