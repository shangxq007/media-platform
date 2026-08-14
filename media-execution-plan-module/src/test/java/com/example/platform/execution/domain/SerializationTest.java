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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for serialization, caching, and determinism.
 */
@DisplayName("Serialization and Caching")
class SerializationTest {

    private static final ContentDigest DIGEST = ContentDigest.sha256("a".repeat(64));
    private static final Instant NOW = Instant.parse("2025-01-15T10:30:00Z");

    private ExecutionInputBinding createInput(String id) {
        return ExecutionInputBinding.primaryMedia(
                new ExecutionInputId(id), new ArtifactId("art-" + id),
                DIGEST, 1000L, "video/mp4");
    }

    private MediaExecutionStep createStep(String id, MediaOperation op) {
        return MediaExecutionStep.of(
                new ExecutionStepId(id), op,
                ExecutionResourceRequirement.standard(),
                ExecutionCapabilityRequirement.of("test"));
    }

    private ExecutionOutputDeclaration createOutput(String id, String stepId) {
        return ExecutionOutputDeclaration.primary(
                new ExecutionOutputId(id), ArtifactKind.DELIVERY_RENDITION,
                "video/mp4", new ExecutionStepId(stepId));
    }

    private MediaExecutionPlan buildPlan() {
        return MediaExecutionPlanBuilder.create()
                .planId(new ExecutionPlanId("plan-001"))
                .tenantId("tenant-1")
                .productId("product-1")
                .timelineRevisionId("rev-001")
                .timelineRevisionDigest("digest-001")
                .schemaVersion(ExecutionPlanSchemaVersion.V1)
                .creationContext(ExecutionCreationContext.minimal(NOW))
                .addInput(createInput("in1"))
                .addStep(createStep("s1", DecodeOperation.of("h264")))
                .addStep(createStep("s2", ScaleOperation.to(1920, 1080)))
                .addEdge(ExecutionDependency.data(
                        new ExecutionEdgeId("e1"),
                        new ExecutionStepId("s1"), new ExecutionStepId("s2")))
                .addOutput(createOutput("out1", "s2"))
                .build();
    }

    @Test
    @DisplayName("Canonical serialization is deterministic")
    void canonicalSerializationDeterministic() {
        MediaExecutionPlan plan = buildPlan();
        String canonical1 = plan.canonicalForm();
        String canonical2 = plan.canonicalForm();
        assertThat(canonical1).isEqualTo(canonical2);
    }

    @Test
    @DisplayName("ExecutionPlanDigestCalculator produces stable digest")
    void digestCalculatorStable() {
        MediaExecutionPlan plan = buildPlan();
        ExecutionPlanDigest digest = ExecutionPlanDigestCalculator.calculate(plan);
        assertThat(digest).isEqualTo(plan.digest());
    }

    @Test
    @DisplayName("Digest verification succeeds for valid plan")
    void digestVerificationSucceeds() {
        MediaExecutionPlan plan = buildPlan();
        assertThat(ExecutionPlanDigestCalculator.verifyDigest(plan)).isTrue();
    }

    @Test
    @DisplayName("ExecutionCacheKey is created for deterministic plans")
    void cacheKeyCreatedForDeterministic() {
        MediaExecutionPlan plan = buildPlan();
        ExecutionCacheKey cacheKey = ExecutionCacheKey.fromPlan(plan);
        assertThat(cacheKey).isNotNull();
        assertThat(cacheKey.key()).isNotBlank();
    }

    @Test
    @DisplayName("Non-deterministic plan cache key is rejected")
    void nonDeterministicCacheKeyRejected() {
        MediaExecutionStep nonDeterministicStep = new MediaExecutionStep(
                new ExecutionStepId("s1"),
                ExecutionStepKind.GENERATE,
                GeneratedMediaOperation.ai("model", Map.of()),
                Set.of(),
                Set.of(),
                ExecutionResourceRequirement.standard(),
                ExecutionCapabilityRequirement.of("test"),
                ExecutionDeterminism.NON_DETERMINISTIC,
                ExecutionStepFailurePolicy.FAIL_PLAN);

        MediaExecutionPlan plan = MediaExecutionPlanBuilder.create()
                .planId(new ExecutionPlanId("plan-001"))
                .tenantId("tenant-1")
                .productId("product-1")
                .timelineRevisionId("rev-001")
                .timelineRevisionDigest("digest-001")
                .schemaVersion(ExecutionPlanSchemaVersion.V1)
                .creationContext(ExecutionCreationContext.minimal(NOW))
                .addInput(createInput("in1"))
                .addStep(nonDeterministicStep)
                .addOutput(createOutput("out1", "s1"))
                .build();

        assertThatThrownBy(() -> ExecutionCacheKey.fromPlan(plan))
                .isInstanceOf(ExecutionPlanDomainException.class);
    }

    @Test
    @DisplayName("Same plan produces same cache key")
    void samePlanSameCacheKey() {
        MediaExecutionPlan plan1 = buildPlan();
        MediaExecutionPlan plan2 = buildPlan();
        ExecutionCacheKey key1 = ExecutionCacheKey.fromPlan(plan1);
        ExecutionCacheKey key2 = ExecutionCacheKey.fromPlan(plan2);
        assertThat(key1.key()).isEqualTo(key2.key());
    }

    @Test
    @DisplayName("Canonical serializer produces hex digests")
    void canonicalSerializerHexDigests() {
        String digest = ExecutionPlanCanonicalSerializer.sha256Hex("test");
        assertThat(digest).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("Plan digest covers schema version")
    void digestCoversSchemaVersion() {
        MediaExecutionPlan plan1 = MediaExecutionPlanBuilder.create()
                .planId(new ExecutionPlanId("plan-001"))
                .tenantId("tenant-1")
                .productId("product-1")
                .timelineRevisionId("rev-001")
                .timelineRevisionDigest("digest-001")
                .schemaVersion(ExecutionPlanSchemaVersion.V1)
                .creationContext(ExecutionCreationContext.minimal(NOW))
                .addInput(createInput("in1"))
                .addStep(createStep("s1", DecodeOperation.of("h264")))
                .addOutput(createOutput("out1", "s1"))
                .build();

        MediaExecutionPlan plan2 = MediaExecutionPlanBuilder.create()
                .planId(new ExecutionPlanId("plan-002"))
                .tenantId("tenant-1")
                .productId("product-1")
                .timelineRevisionId("rev-001")
                .timelineRevisionDigest("digest-001")
                .schemaVersion(new ExecutionPlanSchemaVersion(2))
                .creationContext(ExecutionCreationContext.minimal(NOW))
                .addInput(createInput("in1"))
                .addStep(createStep("s1", DecodeOperation.of("h264")))
                .addOutput(createOutput("out1", "s1"))
                .build();

        assertThat(plan1.digest()).isNotEqualTo(plan2.digest());
    }
}
