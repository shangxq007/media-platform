package com.example.platform.shared.capability.trace;

import com.example.platform.shared.capability.AutomationFlow;
import com.example.platform.shared.capability.AutomationTrigger;
import com.example.platform.shared.capability.flow.*;
import com.example.platform.shared.capability.validation.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AutomationExecutionTrace and related types.
 *
 * <p>These tests verify trace model without implementing persistence.</p>
 */
class AutomationExecutionTraceTest {

    @Test
    void traceCanRepresentDryRunResult() {
        AutomationExecutionTrace trace = createDryRunTrace();

        assertNotNull(trace);
        assertTrue(trace.dryRun());
        assertEquals(AutomationExecutionTraceStatus.DRY_RUN_SUCCEEDED, trace.status());
    }

    @Test
    void dryRunFlagIsPreserved() {
        AutomationExecutionTrace trace = createDryRunTrace();

        assertTrue(trace.dryRun());
        assertTrue(trace.executionId().startsWith("dryrun-"));
    }

    @Test
    void nodeOrderIsPreserved() {
        AutomationExecutionTrace trace = createDryRunTrace();

        assertEquals(3, trace.nodeTraces().size());
        assertEquals("node-1", trace.nodeTraces().get(0).nodeId());
        assertEquals("node-2", trace.nodeTraces().get(1).nodeId());
        assertEquals("node-3", trace.nodeTraces().get(2).nodeId());
    }

    @Test
    void validationIssuesArePreserved() {
        AutomationFlowValidationResult validationResult = new AutomationFlowValidationResult(
            true,
            List.of(
                AutomationFlowValidationIssue.warning(
                    AutomationFlowValidationCode.DISCONNECTED_NODE,
                    "Node is disconnected"
                )
            )
        );

        AutomationFlowDryRunResult dryRunResult = new AutomationFlowDryRunResult(
            AutomationFlowDryRunStatus.SUCCEEDED,
            validationResult,
            List.of(
                AutomationNodeDryRunResult.dryRunSucceeded("node-1", AutomationFlow.NodeType.ACTION, "render.create_job")
            ),
            Instant.now(),
            Instant.now(),
            true
        );

        AutomationExecutionTrace trace = AutomationDryRunTraceMapper.map(
            dryRunResult,
            "flow-1",
            "1.0.0",
            "tenant-123",
            AutomationTrigger.TriggerType.EVENT,
            "asset.uploaded"
        );

        assertEquals(1, trace.issues().size());
        assertEquals(AutomationFlowValidationCode.DISCONNECTED_NODE, trace.issues().get(0).code());
    }

    @Test
    void nodeAttemptModelSupportsRetryMetadata() {
        AutomationNodeExecutionAttempt attempt1 = AutomationNodeExecutionAttempt.failed(
            1,
            Instant.now(),
            Instant.now(),
            "TIMEOUT",
            "Request timed out",
            true
        );

        AutomationNodeExecutionAttempt attempt2 = AutomationNodeExecutionAttempt.succeeded(
            2,
            Instant.now(),
            Instant.now()
        );

        AutomationNodeExecutionTrace nodeTrace = new AutomationNodeExecutionTrace(
            "node-1",
            AutomationFlow.NodeType.ACTION,
            "render.create_job",
            "1.0.0",
            AutomationNodeExecutionTraceStatus.SUCCEEDED,
            Instant.now(),
            Instant.now(),
            2,
            List.of(attempt1, attempt2),
            Map.of(),
            List.of(),
            null,
            false,
            null
        );

        assertEquals(2, nodeTrace.attemptCount());
        assertEquals(2, nodeTrace.attempts().size());
        assertEquals(AutomationNodeExecutionTraceStatus.FAILED, nodeTrace.attempts().get(0).status());
        assertTrue(nodeTrace.attempts().get(0).retryable());
        assertEquals(AutomationNodeExecutionTraceStatus.SUCCEEDED, nodeTrace.attempts().get(1).status());
    }

    @Test
    void traceHasCorrelationCausationIdempotencyIds() {
        AutomationExecutionTrace trace = new AutomationExecutionTrace(
            "exec-1",
            "flow-1",
            "1.0.0",
            "tenant-123",
            AutomationTrigger.TriggerType.EVENT,
            "asset.uploaded",
            AutomationExecutionTraceStatus.DRY_RUN_SUCCEEDED,
            Instant.now(),
            Instant.now(),
            true,
            "correlation-123",
            "causation-456",
            "idempotency-789",
            List.of(),
            List.of(),
            null,
            Map.of()
        );

        assertEquals("correlation-123", trace.correlationId());
        assertEquals("causation-456", trace.causationId());
        assertEquals("idempotency-789", trace.idempotencyKey());
    }

    @Test
    void mapperFromDryRunResultWorks() {
        AutomationFlowDryRunResult dryRunResult = new AutomationFlowDryRunResult(
            AutomationFlowDryRunStatus.SUCCEEDED,
            new AutomationFlowValidationResult(true, List.of()),
            List.of(
                AutomationNodeDryRunResult.dryRunSucceeded("node-1", AutomationFlow.NodeType.ACTION, "render.create_job"),
                AutomationNodeDryRunResult.notImplemented("node-2", AutomationFlow.NodeType.EXTENSION_POINT, "ai.transcribe", "Not implemented")
            ),
            Instant.now(),
            Instant.now(),
            true
        );

        AutomationExecutionTrace trace = AutomationDryRunTraceMapper.map(
            dryRunResult,
            "flow-1",
            "1.0.0",
            "tenant-123",
            AutomationTrigger.TriggerType.EVENT,
            "asset.uploaded"
        );

        assertNotNull(trace);
        assertTrue(trace.dryRun());
        assertEquals(AutomationExecutionTraceStatus.DRY_RUN_PARTIALLY_SUPPORTED, trace.status());
        assertEquals(2, trace.nodeTraces().size());
        assertEquals(AutomationNodeExecutionTraceStatus.DRY_RUN_SUCCEEDED, trace.nodeTraces().get(0).status());
        assertEquals(AutomationNodeExecutionTraceStatus.NOT_IMPLEMENTED, trace.nodeTraces().get(1).status());
    }

    @Test
    void traceDurationCanBeCalculated() {
        Instant startedAt = Instant.now();
        Instant completedAt = startedAt.plusSeconds(5);

        AutomationExecutionTrace trace = new AutomationExecutionTrace(
            "exec-1",
            "flow-1",
            "1.0.0",
            "tenant-123",
            AutomationTrigger.TriggerType.EVENT,
            "asset.uploaded",
            AutomationExecutionTraceStatus.DRY_RUN_SUCCEEDED,
            startedAt,
            completedAt,
            true,
            null,
            null,
            "idempotency-123",
            List.of(),
            List.of(),
            null,
            Map.of()
        );

        assertEquals(java.time.Duration.ofSeconds(5), trace.duration());
    }

    @Test
    void allNodesSucceededChecksCorrectly() {
        AutomationExecutionTrace allSucceeded = new AutomationExecutionTrace(
            "exec-1",
            "flow-1",
            "1.0.0",
            "tenant-123",
            AutomationTrigger.TriggerType.EVENT,
            "asset.uploaded",
            AutomationExecutionTraceStatus.DRY_RUN_SUCCEEDED,
            Instant.now(),
            Instant.now(),
            true,
            null,
            null,
            "idempotency-123",
            List.of(
                AutomationNodeExecutionTrace.dryRunSucceeded("node-1", AutomationFlow.NodeType.ACTION, "render.create_job"),
                AutomationNodeExecutionTrace.dryRunSucceeded("node-2", AutomationFlow.NodeType.ACTION, "media.generate_thumbnail")
            ),
            List.of(),
            null,
            Map.of()
        );

        assertTrue(allSucceeded.allNodesSucceeded());

        AutomationExecutionTrace hasNotImplemented = new AutomationExecutionTrace(
            "exec-2",
            "flow-1",
            "1.0.0",
            "tenant-123",
            AutomationTrigger.TriggerType.EVENT,
            "asset.uploaded",
            AutomationExecutionTraceStatus.DRY_RUN_PARTIALLY_SUPPORTED,
            Instant.now(),
            Instant.now(),
            true,
            null,
            null,
            "idempotency-456",
            List.of(
                AutomationNodeExecutionTrace.dryRunSucceeded("node-1", AutomationFlow.NodeType.ACTION, "render.create_job"),
                AutomationNodeExecutionTrace.notImplemented("node-2", AutomationFlow.NodeType.EXTENSION_POINT, "ai.transcribe", "NOT_IMPLEMENTED")
            ),
            List.of(),
            null,
            Map.of()
        );

        assertFalse(hasNotImplemented.allNodesSucceeded());
        assertTrue(hasNotImplemented.hasNotImplementedNodes());
    }

    private AutomationExecutionTrace createDryRunTrace() {
        AutomationFlowDryRunResult dryRunResult = new AutomationFlowDryRunResult(
            AutomationFlowDryRunStatus.SUCCEEDED,
            new AutomationFlowValidationResult(true, List.of()),
            List.of(
                AutomationNodeDryRunResult.dryRunSucceeded("node-1", AutomationFlow.NodeType.ACTION, "render.create_job"),
                AutomationNodeDryRunResult.dryRunSucceeded("node-2", AutomationFlow.NodeType.ACTION, "media.generate_thumbnail"),
                AutomationNodeDryRunResult.skipped("node-3", AutomationFlow.NodeType.CONDITION, "condition.check", "Skipped")
            ),
            Instant.now(),
            Instant.now(),
            true
        );

        return AutomationDryRunTraceMapper.map(
            dryRunResult,
            "flow-1",
            "1.0.0",
            "tenant-123",
            AutomationTrigger.TriggerType.EVENT,
            "asset.uploaded"
        );
    }
}
