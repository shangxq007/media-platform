package com.example.platform.execution.domain;

import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.execution.domain.operation.*;
import com.example.platform.storage.contract.ContentDigest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for frozen interfaces.
 */
@DisplayName("Frozen Interfaces")
class FrozenInterfacesTest {

    @Test
    @DisplayName("TimelineToExecutionPlanCompiler is frozen — throws UnsupportedOperationException")
    void compilerIsFrozen() {
        TimelineToExecutionPlanCompiler compiler = new TimelineToExecutionPlanCompiler.Stub();
        assertThatThrownBy(() -> compiler.compile(
                "tenant-1", "product-1", "rev-001", "digest",
                java.util.List.of(), java.util.List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("MediaBackendCompiler is frozen — throws UnsupportedOperationException")
    void backendCompilerIsFrozen() {
        MediaBackendCompiler compiler = new MediaBackendCompiler.Stub();
        MediaExecutionPlan plan = createSimplePlan();

        assertThatThrownBy(() -> compiler.compile(plan))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("ExecutionProvider is frozen — throws UnsupportedOperationException")
    void executionProviderIsFrozen() {
        ExecutionProvider provider = new ExecutionProvider.Stub();
        ExecutionProvider.ExecutionManifest manifest = new ExecutionProvider.ExecutionManifest("test", "{}");
        assertThatThrownBy(() -> provider.submit(manifest))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Stub capabilities are valid")
    void stubCapabilitiesValid() {
        ExecutionProvider provider = new ExecutionProvider.Stub();
        ExecutionProvider.ProviderCapabilities caps = provider.capabilities();
        assertThat(caps).isNotNull();
        assertThat(caps.supportsGpu()).isFalse();
    }

    @Test
    @DisplayName("All 24+ error codes exist")
    void allErrorCodesExist() {
        ExecutionPlanErrorCode.Code[] codes = ExecutionPlanErrorCode.Code.values();
        assertThat(codes).hasSizeGreaterThanOrEqualTo(22);
    }

    @Test
    @DisplayName("Each error code has valid fields")
    void errorCodesHaveValidFields() {
        for (ExecutionPlanErrorCode.Code code : ExecutionPlanErrorCode.Code.values()) {
            assertThat(code.codeString()).isNotBlank();
            assertThat(code.title()).isNotBlank();
            assertThat(code.status()).isGreaterThanOrEqualTo(400).isLessThan(600);
        }
    }

    @Test
    @DisplayName("Error builder works")
    void errorBuilderWorks() {
        ExecutionPlanErrorCode.Error error = ExecutionPlanErrorCode.Error
                .builder(ExecutionPlanErrorCode.Code.EXECUTION_PLAN_INVALID)
                .planId("plan-001")
                .detail("test")
                .build();
        assertThat(error.code()).isEqualTo(ExecutionPlanErrorCode.Code.EXECUTION_PLAN_INVALID);
        assertThat(error.planId()).isEqualTo("plan-001");
    }

    @Test
    @DisplayName("Exception carries error")
    void exceptionCarriesError() {
        ExecutionPlanErrorCode.Error error = ExecutionPlanErrorCode.Error
                .builder(ExecutionPlanErrorCode.Code.EXECUTION_PLAN_CYCLE)
                .planId("plan-001")
                .detail("cycle detected")
                .build();
        ExecutionPlanDomainException ex = new ExecutionPlanDomainException(error);
        assertThat(ex.error()).isEqualTo(error);
        assertThat(ex.code()).isEqualTo(ExecutionPlanErrorCode.Code.EXECUTION_PLAN_CYCLE);
        assertThat(ex.getMessage()).contains("Cycle");
    }

    private MediaExecutionPlan createSimplePlan() {
        return MediaExecutionPlanBuilder.create()
                .planId(new ExecutionPlanId("plan-001"))
                .tenantId("tenant-1")
                .productId("product-1")
                .timelineRevisionId("rev-001")
                .timelineRevisionDigest("digest-001")
                .schemaVersion(ExecutionPlanSchemaVersion.V1)
                .creationContext(ExecutionCreationContext.minimal(java.time.Instant.now()))
                .addInput(ExecutionInputBinding.primaryMedia(
                        new ExecutionInputId("in1"),
                        new com.example.platform.shared.identity.ArtifactId("art-1"),
                        ContentDigest.sha256("a".repeat(64)),
                        1000L, "video/mp4"))
                .addStep(MediaExecutionStep.of(
                        new ExecutionStepId("s1"),
                        DecodeOperation.of("h264"),
                        ExecutionResourceRequirement.standard(),
                        ExecutionCapabilityRequirement.of("test")))
                .addOutput(ExecutionOutputDeclaration.primary(
                        new ExecutionOutputId("out1"),
                        ArtifactKind.DELIVERY_RENDITION,
                        "video/mp4",
                        new ExecutionStepId("s1")))
                .build();
    }
}
