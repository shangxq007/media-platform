package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.app.input.RenderInputMaterializationService;
import com.example.platform.render.app.output.RenderOutputRegistrationService;
import com.example.platform.render.app.output.RenderProductProvenance;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.timeline.compile.audit.*;
import com.example.platform.render.domain.product.Product;
import com.example.platform.render.domain.compile.executionplan.*;
import com.example.platform.render.infrastructure.RenderToolCapabilityInventory;
import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Executes individual render execution plan steps with audit events.
 *
 * <p>Internal only — delegates to existing services for each step type.
 * Concrete provider execution has migrated to the typed PF4J provider-native host.</p>
 */
@Service
public class RenderExecutionStepExecutor {

    private static final Logger log = LoggerFactory.getLogger(RenderExecutionStepExecutor.class);

    private final RenderInputMaterializationService materializationService;
    private final RenderOutputRegistrationService registrationService;
    private final ProductRuntimeService productRuntime;
    private final RenderToolCapabilityInventory toolInventory;
    private final ProcessToolRunner processToolRunner;
    private final RenderAuditRecorder auditRecorder;

    public RenderExecutionStepExecutor(
            RenderInputMaterializationService materializationService,
            RenderOutputRegistrationService registrationService,
            ProductRuntimeService productRuntime,
            RenderToolCapabilityInventory toolInventory,
            ProcessToolRunner processToolRunner,
            RenderAuditRecorder auditRecorder) {
        this.materializationService = materializationService;
        this.registrationService = registrationService;
        this.productRuntime = productRuntime;
        this.toolInventory = toolInventory;
        this.processToolRunner = processToolRunner;
        this.auditRecorder = auditRecorder;
    }

    /**
     * Execute a single plan step.
     *
     * @param step    the step to execute
     * @param context the execution context
     * @return the step result
     */
    public LocalExecutionPlanStepResult execute(RenderExecutionStep step,
                                                  LocalExecutionPlanContext context) {
        long start = System.currentTimeMillis();

        LocalExecutionPlanStepResult result = switch (step.type()) {
            case MATERIALIZE_INPUT -> executeMaterializeInput(step, context, start);
            case PREPARE_PROVIDER_DOCUMENT -> executePrepareDocument(step, context, start);
            case EXECUTE_PROVIDER -> executeProvider(step, context, start);
            case VERIFY_OUTPUT -> executeVerifyOutput(step, context, start);
            case REGISTER_OUTPUT -> executeRegisterOutput(step, context, start);
            case LINK_PRODUCT_DEPENDENCY -> executeLinkDependency(step, context, start);
            case FINALIZE_RENDER -> executeFinalize(step, context, start);
        };

        // Emit audit event for completed steps
        emitStepAuditEvent(step, context, result);
        return result;
    }

    private void emitStepAuditEvent(RenderExecutionStep step, LocalExecutionPlanContext context,
                                      LocalExecutionPlanStepResult result) {
        RenderAuditEventType eventType = switch (step.type()) {
            case MATERIALIZE_INPUT -> RenderAuditEventType.INPUT_MATERIALIZATION_COMPLETED;
            case EXECUTE_PROVIDER -> RenderAuditEventType.PROVIDER_EXECUTION_COMPLETED;
            case VERIFY_OUTPUT -> RenderAuditEventType.OUTPUT_VERIFICATION_COMPLETED;
            case REGISTER_OUTPUT -> RenderAuditEventType.OUTPUT_REGISTRATION_COMPLETED;
            case LINK_PRODUCT_DEPENDENCY -> RenderAuditEventType.PRODUCT_DEPENDENCY_LINKED;
            default -> null;
        };
        if (eventType == null) return;

        RenderAuditEventSeverity severity = result.isSuccess()
                ? RenderAuditEventSeverity.INFO
                : RenderAuditEventSeverity.ERROR;

        auditRecorder.record(RenderAuditEvent.builder()
                .eventType(eventType)
                .severity(severity)
                .projectId(context.projectId())
                .timelineRevisionId(context.timelineRevisionId())
                .renderJobId(context.renderJobId())
                .providerName(step.providerName())
                .message(step.type().name() + ": " + result.message())
                .build());
    }

    private LocalExecutionPlanStepResult executeMaterializeInput(RenderExecutionStep step,
                                                                   LocalExecutionPlanContext context,
                                                                   long start) {
        String inputProductId = context.inputProductId();
        if (inputProductId == null) {
            return LocalExecutionPlanStepResult.failed(step.stepId(), step.type().name(),
                    "No input product ID in context", System.currentTimeMillis() - start);
        }

        try {
            var materialization = materializationService.materialize(inputProductId, null, null);
            if (!materialization.valid()) {
                return LocalExecutionPlanStepResult.failed(step.stepId(), step.type().name(),
                        "Materialization failed: " + materialization.failureReason(),
                        System.currentTimeMillis() - start);
            }

            long duration = System.currentTimeMillis() - start;
            log.info("MATERIALIZE_INPUT completed: stepId={} duration={}ms", step.stepId(), duration);
            return LocalExecutionPlanStepResult.succeeded(step.stepId(), step.type().name(),
                    "Input materialized successfully", duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("MATERIALIZE_INPUT failed: stepId={} error={}", step.stepId(), e.getMessage());
            return LocalExecutionPlanStepResult.failed(step.stepId(), step.type().name(),
                    "Materialization error: " + e.getMessage(), duration);
        }
    }

    private LocalExecutionPlanStepResult executePrepareDocument(RenderExecutionStep step,
                                                                  LocalExecutionPlanContext context,
                                                                  long start) {
        // v0: Document preparation is a planning step — no real document generated
        // The document draft is already attached to the step
        long duration = System.currentTimeMillis() - start;
        log.debug("PREPARE_PROVIDER_DOCUMENT: stepId={} provider={} (v0: no-op)",
                step.stepId(), step.providerName());
        return LocalExecutionPlanStepResult.succeeded(step.stepId(), step.type().name(),
                "Document preparation planned (v0: no generation)", duration);
    }

    private LocalExecutionPlanStepResult executeProvider(RenderExecutionStep step,
                                                           LocalExecutionPlanContext context,
                                                           long start) {
        return LocalExecutionPlanStepResult.failed(step.stepId(), step.type().name(),
                "TYPED_PROVIDER_PLUGIN_EXECUTION_REQUIRED", System.currentTimeMillis() - start);
    }

    private LocalExecutionPlanStepResult executeVerifyOutput(RenderExecutionStep step,
                                                               LocalExecutionPlanContext context,
                                                               long start) {
        Path outputVideo = context.outputDir().resolve(
                context.outputFileName() != null ? context.outputFileName() : "output.mp4");

        try {
            if (!Files.exists(outputVideo)) {
                return LocalExecutionPlanStepResult.failed(step.stepId(), step.type().name(),
                        "Output file does not exist", System.currentTimeMillis() - start);
            }
            if (Files.size(outputVideo) == 0) {
                return LocalExecutionPlanStepResult.failed(step.stepId(), step.type().name(),
                        "Output file is zero-byte", System.currentTimeMillis() - start);
            }

            long duration = System.currentTimeMillis() - start;
            log.info("VERIFY_OUTPUT completed: stepId={} size={} bytes", step.stepId(), Files.size(outputVideo));
            return LocalExecutionPlanStepResult.succeeded(step.stepId(), step.type().name(),
                    "Output verified: " + Files.size(outputVideo) + " bytes", duration);
        } catch (IOException e) {
            long duration = System.currentTimeMillis() - start;
            return LocalExecutionPlanStepResult.failed(step.stepId(), step.type().name(),
                    "Verification error: " + e.getMessage(), duration);
        }
    }

    private LocalExecutionPlanStepResult executeRegisterOutput(RenderExecutionStep step,
                                                                 LocalExecutionPlanContext context,
                                                                 long start) {
        try {
            Path outputVideo = context.outputDir().resolve(
                    context.outputFileName() != null ? context.outputFileName() : "output.mp4");
            String relativePath = context.storageRoot().relativize(outputVideo).toString();

            RenderProductProvenance provenance = RenderProductProvenance.builder()
                    .renderJobId(context.renderJobId())
                    .baselineRenderer("typed-provider-plugin")
                    .renderMode("plan-based-timeline-revision-render")
                    .inputProductIds(context.inputProductIds())
                    .build();

            Product outputProduct = registrationService.registerOutput(
                    context.renderJobId(), context.tenantId(), context.projectId(),
                    step.providerName(), relativePath, provenance);

            // Store outputProductId in context metadata for downstream steps
            // (The context is immutable, but we use the result to propagate this)
            long duration = System.currentTimeMillis() - start;
            log.info("REGISTER_OUTPUT completed: stepId={} product={} status={}",
                    step.stepId(), outputProduct.productId(), outputProduct.status());
            return LocalExecutionPlanStepResult.succeeded(step.stepId(), step.type().name(),
                    "Output registered: product=" + outputProduct.productId(),
                    duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("REGISTER_OUTPUT failed: stepId={} error={}", step.stepId(), e.getMessage());
            return LocalExecutionPlanStepResult.failed(step.stepId(), step.type().name(),
                    "Registration error: " + e.getMessage(), duration);
        }
    }

    private LocalExecutionPlanStepResult executeLinkDependency(RenderExecutionStep step,
                                                                 LocalExecutionPlanContext context,
                                                                 long start) {
        // ProductDependency lineage is created by RenderOutputRegistrationService
        // as part of registerOutput(). This step is a verification/planning marker.
        long duration = System.currentTimeMillis() - start;
        log.debug("LINK_PRODUCT_DEPENDENCY: stepId={} (handled by registerOutput)", step.stepId());
        return LocalExecutionPlanStepResult.succeeded(step.stepId(), step.type().name(),
                "Product dependency lineage handled by output registration", duration);
    }

    private LocalExecutionPlanStepResult executeFinalize(RenderExecutionStep step,
                                                           LocalExecutionPlanContext context,
                                                           long start) {
        long duration = System.currentTimeMillis() - start;
        log.info("FINALIZE_RENDER completed: stepId={} job={}", step.stepId(), context.renderJobId());
        return LocalExecutionPlanStepResult.succeeded(step.stepId(), step.type().name(),
                "Render plan finalized", duration);
    }
}
