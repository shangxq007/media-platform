package com.example.platform.operation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.observability.context.TraceKeys;
import com.example.platform.operation.invocation.OperationInvocationContext;
import com.example.platform.operation.operation.OperationDefinitionId;
import com.example.platform.operation.operation.OperationDefinitionVersion;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.operation.plan.ApplyResult;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.time.MediaTime;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * OPERATION-OBSERVABILITY-BASELINE: {@link CanonicalOperationInvocationService}
 * sets MDC {@code workflowId} from the invocation correlationId for the duration
 * of the call and clears it afterwards (no logging).
 */
class CanonicalOperationInvocationServiceMdcTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void mdcWorkflowIdSetDuringInvoke() {
        AtomicReference<String> during = new AtomicReference<>();

        CanonicalOperationInvocationService canonical = canonicalCapturing(during);
        canonical.invoke(textRequest(), context("corr-1"));

        assertEquals("corr-1", during.get(), "MDC workflowId must be set during invoke");
    }

    @Test
    void mdcWorkflowIdClearedAfterInvoke() {
        AtomicReference<String> during = new AtomicReference<>();

        CanonicalOperationInvocationService canonical = canonicalCapturing(during);
        canonical.invoke(textRequest(), context("corr-1"));

        assertNull(MDC.get(TraceKeys.WORKFLOW_ID), "MDC workflowId must be cleared after invoke");
    }

    @Test
    void nullCorrelationIdDoesNotSetMdc() {
        AtomicReference<String> during = new AtomicReference<>("sentinel");

        CanonicalOperationInvocationService canonical = canonicalCapturing(during);
        canonical.invoke(textRequest(), context(null));

        assertNull(during.get(), "no correlationId -> no MDC workflowId during invoke");
        assertNull(MDC.get(TraceKeys.WORKFLOW_ID));
    }

    // ---- fixtures ----

    private CanonicalOperationInvocationService canonicalCapturing(AtomicReference<String> during) {
        TextOperationService text = mock(TextOperationService.class);
        when(text.invoke(any(), any())).thenAnswer(invocation -> {
            during.set(MDC.get(TraceKeys.WORKFLOW_ID));
            return new TextOperationService.InvocationOutcome(ApplyResult.applied(
                    "plan", "cmd", "R0", "R1", "hash", "R0", "main"));
        });
        return new CanonicalOperationInvocationService(
                mock(TimelineMediaClipOperationService.class), text,
                mock(AlignmentOperationService.class));
    }

    private static OperationInvocationContext context(String correlationId) {
        return new OperationInvocationContext(
                CanonicalActor.user("alice", "tenant-a", Set.of(), "test"),
                "inv-1", correlationId, "workflow");
    }

    private static OperationRequest textRequest() {
        return new OperationRequest(
                OperationDefinitionId.of("timeline.text.add_element"),
                OperationDefinitionVersion.of(1, 0),
                new OperationTargetRequest.TimelineTargetRequest("project-1"),
                new OperationParameters.MoveParameters(MediaTime.ZERO, false),
                "R0", "hash", null);
    }
}
