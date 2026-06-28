package com.example.platform.render.domain.remotion;

import com.example.platform.render.app.timeline.compile.RenderCorrelationContext;
import com.example.platform.render.app.timeline.compile.audit.*;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remotion local execution runner — skeleton only, disabled by default.
 *
 * <p>Internal only — not exposed in public APIs.</p>
 *
 * <p>v0: All requests are refused. No external process is started.
 * No Node/npm/npx/remotion invocation. No StorageRuntime mutation.
 * No ProductRuntime mutation. No output file produced.</p>
 *
 * <p>This runner is NOT wired into LocalExecutionPlanRunner.
 * FFmpeg remains the only executable provider.</p>
 */
public class RemotionLocalExecutionRunner {

    private static final Logger log = LoggerFactory.getLogger(RemotionLocalExecutionRunner.class);

    private final RemotionExecutionPolicyEvaluator evaluator;
    private final RenderAuditRecorder auditRecorder;

    /**
     * Create runner without audit recorder.
     */
    public RemotionLocalExecutionRunner() {
        this(null);
    }

    /**
     * Create runner with optional audit recorder.
     *
     * @param auditRecorder audit recorder (null-safe, may be null)
     */
    public RemotionLocalExecutionRunner(RenderAuditRecorder auditRecorder) {
        this.evaluator = new RemotionExecutionPolicyEvaluator();
        this.auditRecorder = auditRecorder;
    }

    /**
     * Execute a Remotion local execution request.
     *
     * <p>v0: Always returns NOT_IMPLEMENTED or BLOCKED status.
     * Never starts a process. Never produces output.</p>
     *
     * @param request the execution request
     * @return the execution result (always non-executed in v0)
     */
    public RemotionLocalExecutionResult execute(RemotionLocalExecutionRequest request) {
        // 1. Null request fails closed
        if (request == null) {
            RemotionLocalExecutionResult result = RemotionLocalExecutionResult.failedClosed("Request must not be null");
            emitAuditRejected(null, result, null);
            return result;
        }

        // 2. Unsupported document rejected
        if (request.documentGenerationResult() != null
                && !request.documentGenerationResult().isGenerated()
                && request.documentGenerationResult().isRejected()) {
            RemotionLocalExecutionResult result = RemotionLocalExecutionResult.rejectedUnsupported(
                    "Document generation rejected: " + request.documentGenerationResult().generationStatus());
            emitAuditRejected(request, result, null);
            return result;
        }

        // 3. Run preflight evaluation
        RemotionExecutionPreflightResult preflight = evaluator.evaluate(
                request.executionPolicy() != null
                        ? request.executionPolicy() : RemotionExecutionPolicy.disabledDefault(),
                request.sandboxPolicy() != null
                        ? request.sandboxPolicy() : RemotionSandboxPolicy.lockedDown(),
                request.providerReadiness(),
                request.commandPlan());

        // 4. Map preflight to execution result
        RemotionLocalExecutionResult result = mapPreflightToResult(preflight);

        // 5. Emit safe audit event
        if (result.notImplemented()) {
            emitAuditNotImplemented(request, result, preflight);
        } else {
            emitAuditRejected(request, result, preflight);
        }

        return result;
    }

    private RemotionLocalExecutionResult mapPreflightToResult(RemotionExecutionPreflightResult preflight) {
        return switch (preflight.status()) {
            case BLOCKED_BY_POLICY -> RemotionLocalExecutionResult.blockedByPolicy(
                    preflight.explanation());
            case BLOCKED_BY_RUNTIME -> RemotionLocalExecutionResult.blockedByRuntime(
                    preflight.explanation());
            case BLOCKED_BY_SANDBOX -> RemotionLocalExecutionResult.blockedByPreflight(
                    preflight.status(), preflight.explanation(), preflight.violations());
            case BLOCKED_BY_UNSUPPORTED_DOCUMENT -> RemotionLocalExecutionResult.rejectedUnsupported(
                    preflight.explanation());
            case BLOCKED_BY_UNSAFE_COMMAND -> RemotionLocalExecutionResult.blockedByPreflight(
                    preflight.status(), preflight.explanation(), preflight.violations());
            case READY_BUT_EXECUTION_DISABLED -> RemotionLocalExecutionResult.notImplemented(
                    "All checks passed but execution remains disabled: " + preflight.explanation());
            case NOT_IMPLEMENTED -> RemotionLocalExecutionResult.notImplemented(
                    preflight.explanation());
        };
    }

    /**
     * Emit safe audit event for rejected/blocked outcomes.
     */
    private void emitAuditRejected(RemotionLocalExecutionRequest request,
                                     RemotionLocalExecutionResult result,
                                     RemotionExecutionPreflightResult preflight) {
        if (auditRecorder == null) return;
        try {
            RenderAuditEvent.Builder builder = RenderAuditEvent.builder()
                    .eventType(RenderAuditEventType.PROVIDER_LOCAL_EXECUTION_PRECHECK_REJECTED)
                    .severity(RenderAuditEventSeverity.WARN)
                    .providerName("remotion")
                    .message("Remotion precheck rejected: " + result.status());

            applySafeFields(builder, request, result, preflight);
            auditRecorder.record(builder.build());
        } catch (Exception e) {
            log.warn("Failed to emit Remotion precheck audit event: {}", e.getMessage());
        }
    }

    /**
     * Emit safe audit event for not-implemented outcomes.
     */
    private void emitAuditNotImplemented(RemotionLocalExecutionRequest request,
                                           RemotionLocalExecutionResult result,
                                           RemotionExecutionPreflightResult preflight) {
        if (auditRecorder == null) return;
        try {
            RenderAuditEvent.Builder builder = RenderAuditEvent.builder()
                    .eventType(RenderAuditEventType.PROVIDER_LOCAL_EXECUTION_NOT_IMPLEMENTED)
                    .severity(RenderAuditEventSeverity.INFO)
                    .providerName("remotion")
                    .message("Remotion execution not implemented: " + result.safeMessage());

            applySafeFields(builder, request, result, preflight);
            auditRecorder.record(builder.build());
        } catch (Exception e) {
            log.warn("Failed to emit Remotion not-implemented audit event: {}", e.getMessage());
        }
    }

    /**
     * Apply safe fields to audit event builder. No raw commands, paths, storage internals, or secrets.
     */
    private void applySafeFields(RenderAuditEvent.Builder builder,
                                   RemotionLocalExecutionRequest request,
                                   RemotionLocalExecutionResult result,
                                   RemotionExecutionPreflightResult preflight) {
        // Correlation fields (safe)
        if (request != null && request.correlationContext() != null) {
            RenderCorrelationContext corr = request.correlationContext();
            builder.renderCorrelationId(corr.renderCorrelationId());
            builder.renderRequestFingerprint(corr.renderRequestFingerprint());
            builder.timelineRevisionId(corr.timelineRevisionId());
            builder.providerBindingPlanId(corr.providerBindingPlanId());
            builder.renderExecutionPlanId(corr.renderExecutionPlanId());
        }

        // Document fields (safe)
        if (request != null && request.documentGenerationResult() != null) {
            var doc = request.documentGenerationResult();
            builder.sanitizedDetails(
                    "documentId=" + doc.documentId()
                    + " documentType=" + doc.documentType()
                    + " draftId=" + doc.draftId()
                    + " generationReady=" + doc.generationReady());
        }

        // Preflight/result status (safe)
        if (preflight != null && preflight.violations() != null) {
            builder.artifactGraphId("violations=" + preflight.violations().size());
        }
    }
}
