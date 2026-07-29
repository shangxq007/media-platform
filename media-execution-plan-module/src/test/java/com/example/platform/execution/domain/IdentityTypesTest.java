package com.example.platform.execution.domain;

import com.example.platform.execution.domain.operation.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for strong identity types.
 */
@DisplayName("Strong Identity Types")
class IdentityTypesTest {

    @Test
    @DisplayName("ExecutionPlanId validates non-blank")
    void executionPlanIdValidatesNonBlank() {
        assertThatThrownBy(() -> new ExecutionPlanId(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExecutionPlanId(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExecutionPlanId("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ExecutionPlanId is value-based")
    void executionPlanIdValueBased() {
        ExecutionPlanId id1 = new ExecutionPlanId("plan-001");
        ExecutionPlanId id2 = new ExecutionPlanId("plan-001");
        assertThat(id1).isEqualTo(id2);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }

    @Test
    @DisplayName("ExecutionStepId validates non-blank")
    void executionStepIdValidatesNonBlank() {
        assertThatThrownBy(() -> new ExecutionStepId(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ExecutionEdgeId validates non-blank")
    void executionEdgeIdValidatesNonBlank() {
        assertThatThrownBy(() -> new ExecutionEdgeId(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ExecutionInputId validates non-blank")
    void executionInputIdValidatesNonBlank() {
        assertThatThrownBy(() -> new ExecutionInputId(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ExecutionOutputId validates non-blank")
    void executionOutputIdValidatesNonBlank() {
        assertThatThrownBy(() -> new ExecutionOutputId(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ExecutionPlanDigest validates non-blank")
    void executionPlanDigestValidatesNonBlank() {
        assertThatThrownBy(() -> new ExecutionPlanDigest(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ExecutionPlanSchemaVersion must be >= 1")
    void schemaVersionMinValue() {
        assertThatThrownBy(() -> new ExecutionPlanSchemaVersion(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ExecutionPlanSchemaVersion.V1.value()).isEqualTo(1);
    }

    @Test
    @DisplayName("All enums have stable names")
    void enumNamesStable() {
        assertThat(ExecutionStepKind.INSPECT.name()).isEqualTo("INSPECT");
        assertThat(ExecutionStepFailurePolicy.FAIL_PLAN.name()).isEqualTo("FAIL_PLAN");
        assertThat(ExecutionDependencyType.DATA.name()).isEqualTo("DATA");
        assertThat(ExecutionInputRole.PRIMARY_MEDIA.name()).isEqualTo("PRIMARY_MEDIA");
        assertThat(ExecutionOutputRole.PRIMARY_OUTPUT.name()).isEqualTo("PRIMARY_OUTPUT");
        assertThat(GpuRequirement.REQUIRED.name()).isEqualTo("REQUIRED");
        assertThat(CpuClass.STANDARD.name()).isEqualTo("STANDARD");
        assertThat(MemoryClass.STANDARD.name()).isEqualTo("STANDARD");
        assertThat(NetworkRequirement.NONE.name()).isEqualTo("NONE");
        assertThat(TemporaryStorageClass.STANDARD.name()).isEqualTo("STANDARD");
        assertThat(ExecutionDeterminism.DETERMINISTIC.name()).isEqualTo("DETERMINISTIC");
    }
}
