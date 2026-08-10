package com.example.platform.execution.domain;

import com.example.platform.artifact.domain.ArtifactId;
import com.example.platform.storage.contract.ContentDigest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for value objects.
 */
@DisplayName("Value Objects")
class ValueObjectsTest {

    private static final ContentDigest DIGEST = ContentDigest.sha256("a".repeat(64));

    @Test
    @DisplayName("ExecutionResourceRequirement validates non-negative cores")
    void resourceRequirementValidatesCores() {
        assertThatThrownBy(() -> new ExecutionResourceRequirement(
                CpuClass.STANDARD, -1, MemoryClass.STANDARD, 100L,
                GpuRequirement.NONE, TemporaryStorageClass.STANDARD, 0L, NetworkRequirement.NONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ExecutionResourceRequirement validates non-negative memory")
    void resourceRequirementValidatesMemory() {
        assertThatThrownBy(() -> new ExecutionResourceRequirement(
                CpuClass.STANDARD, 1, MemoryClass.STANDARD, -1L,
                GpuRequirement.NONE, TemporaryStorageClass.STANDARD, 0L, NetworkRequirement.NONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ExecutionResourceRequirement factory methods work")
    void resourceRequirementFactories() {
        ExecutionResourceRequirement minimal = ExecutionResourceRequirement.minimal();
        assertThat(minimal.cpuClass()).isEqualTo(CpuClass.MINIMAL);
        assertThat(minimal.requiresGpu()).isFalse();

        ExecutionResourceRequirement standard = ExecutionResourceRequirement.standard();
        assertThat(standard.cpuClass()).isEqualTo(CpuClass.STANDARD);
        assertThat(standard.requiresGpu()).isTrue();
        assertThat(standard.requiresMandatoryGpu()).isFalse();

        ExecutionResourceRequirement highPerf = ExecutionResourceRequirement.highPerformance();
        assertThat(highPerf.requiresMandatoryGpu()).isTrue();
    }

    @Test
    @DisplayName("ExecutionResourceRequirement canonical form is deterministic")
    void resourceRequirementCanonicalDeterministic() {
        ExecutionResourceRequirement r1 = ExecutionResourceRequirement.standard();
        ExecutionResourceRequirement r2 = ExecutionResourceRequirement.standard();
        assertThat(r1.canonicalForm()).isEqualTo(r2.canonicalForm());
    }

    @Test
    @DisplayName("ExecutionCapabilityRequirement validates blank capabilityId")
    void capabilityRequirementValidatesId() {
        assertThatThrownBy(() -> ExecutionCapabilityRequirement.of(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ExecutionCapabilityRequirement factory methods work")
    void capabilityRequirementFactories() {
        ExecutionCapabilityRequirement basic = ExecutionCapabilityRequirement.of("h264");
        assertThat(basic.hasVersionConstraint()).isFalse();
        assertThat(basic.hasFeatureConstraints()).isFalse();

        ExecutionCapabilityRequirement withVer = ExecutionCapabilityRequirement.withMinVersion("h264", "1.0");
        assertThat(withVer.hasVersionConstraint()).isTrue();

        ExecutionCapabilityRequirement withFeatures = ExecutionCapabilityRequirement.withFeatures(
                "h264", Set.of("high-profile", "4k"));
        assertThat(withFeatures.hasFeatureConstraints()).isTrue();
    }

    @Test
    @DisplayName("ExecutionCapabilityRequirement canonical form is deterministic")
    void capabilityRequirementCanonicalDeterministic() {
        ExecutionCapabilityRequirement c1 = ExecutionCapabilityRequirement.withFeatures(
                "h264", Set.of("b", "a"));
        ExecutionCapabilityRequirement c2 = ExecutionCapabilityRequirement.withFeatures(
                "h264", Set.of("a", "b"));
        assertThat(c1.canonicalForm()).isEqualTo(c2.canonicalForm());
    }

    @Test
    @DisplayName("ExecutionCreationContext factory methods work")
    void creationContextFactories() {
        Instant now = Instant.now();
        ExecutionCreationContext minimal = ExecutionCreationContext.minimal(now);
        assertThat(minimal.getRequestedByUserId()).isEmpty();

        ExecutionCreationContext withUser = ExecutionCreationContext.forUser("user-1", "tenant-1", now);
        assertThat(withUser.getRequestedByUserId()).isPresent().get().isEqualTo("user-1");
        assertThat(withUser.getRequestedByTenantId()).isPresent().get().isEqualTo("tenant-1");
    }

    @Test
    @DisplayName("ExecutionCreationContext with methods work")
    void creationContextWithMethods() {
        Instant now = Instant.now();
        ExecutionCreationContext ctx = ExecutionCreationContext.minimal(now)
                .withTraceId("trace-123")
                .withComment("test");
        assertThat(ctx.getTraceId()).isPresent().get().isEqualTo("trace-123");
        assertThat(ctx.getComment()).isPresent().get().isEqualTo("test");
    }
}
