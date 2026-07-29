package com.example.platform.execution.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Strong Identity Validation")
class StrongIdentityValidationTest {

    @Nested
    @DisplayName("ExecutionPlanId")
    class ExecutionPlanIdTest {

        @Test
        @DisplayName("rejects null value")
        void rejectsNull() {
            assertThatThrownBy(() -> new ExecutionPlanId(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be blank");
        }

        @Test
        @DisplayName("rejects blank value")
        void rejectsBlank() {
            assertThatThrownBy(() -> new ExecutionPlanId("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be blank");
        }

        @Test
        @DisplayName("accepts valid value")
        void acceptsValid() {
            ExecutionPlanId id = new ExecutionPlanId("plan-001");
            assertThat(id.value()).isEqualTo("plan-001");
        }

        @Test
        @DisplayName("two IDs with same value are equal")
        void equality() {
            ExecutionPlanId a = new ExecutionPlanId("plan-001");
            ExecutionPlanId b = new ExecutionPlanId("plan-001");
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("two IDs with different values are not equal")
        void inequality() {
            ExecutionPlanId a = new ExecutionPlanId("plan-001");
            ExecutionPlanId b = new ExecutionPlanId("plan-002");
            assertThat(a).isNotEqualTo(b);
        }
    }

    @Nested
    @DisplayName("ExecutionStepId")
    class ExecutionStepIdTest {

        @Test
        @DisplayName("rejects null value")
        void rejectsNull() {
            assertThatThrownBy(() -> new ExecutionStepId(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects blank value")
        void rejectsBlank() {
            assertThatThrownBy(() -> new ExecutionStepId(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("accepts valid value")
        void acceptsValid() {
            ExecutionStepId id = new ExecutionStepId("step-001");
            assertThat(id.value()).isEqualTo("step-001");
        }
    }

    @Nested
    @DisplayName("ExecutionInputId")
    class ExecutionInputIdTest {

        @Test
        @DisplayName("rejects null value")
        void rejectsNull() {
            assertThatThrownBy(() -> new ExecutionInputId(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects blank value")
        void rejectsBlank() {
            assertThatThrownBy(() -> new ExecutionInputId(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("accepts valid value")
        void acceptsValid() {
            ExecutionInputId id = new ExecutionInputId("in-001");
            assertThat(id.value()).isEqualTo("in-001");
        }
    }

    @Nested
    @DisplayName("ExecutionOutputId")
    class ExecutionOutputIdTest {

        @Test
        @DisplayName("rejects null value")
        void rejectsNull() {
            assertThatThrownBy(() -> new ExecutionOutputId(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects blank value")
        void rejectsBlank() {
            assertThatThrownBy(() -> new ExecutionOutputId(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("accepts valid value")
        void acceptsValid() {
            ExecutionOutputId id = new ExecutionOutputId("out-001");
            assertThat(id.value()).isEqualTo("out-001");
        }
    }

    @Nested
    @DisplayName("ExecutionEdgeId")
    class ExecutionEdgeIdTest {

        @Test
        @DisplayName("rejects null value")
        void rejectsNull() {
            assertThatThrownBy(() -> new ExecutionEdgeId(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects blank value")
        void rejectsBlank() {
            assertThatThrownBy(() -> new ExecutionEdgeId(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("accepts valid value")
        void acceptsValid() {
            ExecutionEdgeId id = new ExecutionEdgeId("edge-001");
            assertThat(id.value()).isEqualTo("edge-001");
        }
    }

    @Nested
    @DisplayName("ExecutionPlanDigest")
    class ExecutionPlanDigestTest {

        @Test
        @DisplayName("rejects null value")
        void rejectsNull() {
            assertThatThrownBy(() -> new ExecutionPlanDigest(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects blank value")
        void rejectsBlank() {
            assertThatThrownBy(() -> new ExecutionPlanDigest(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("accepts valid value")
        void acceptsValid() {
            ExecutionPlanDigest digest = new ExecutionPlanDigest("abc123");
            assertThat(digest.value()).isEqualTo("abc123");
        }
    }

    @Nested
    @DisplayName("ExecutionPlanSchemaVersion")
    class ExecutionPlanSchemaVersionTest {

        @Test
        @DisplayName("rejects value less than 1")
        void rejectsLessThanOne() {
            assertThatThrownBy(() -> new ExecutionPlanSchemaVersion(0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ExecutionPlanSchemaVersion(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("accepts valid value")
        void acceptsValid() {
            ExecutionPlanSchemaVersion v = new ExecutionPlanSchemaVersion(1);
            assertThat(v.value()).isEqualTo(1);
        }

        @Test
        @DisplayName("V1 constant is available")
        void v1Constant() {
            assertThat(ExecutionPlanSchemaVersion.V1.value()).isEqualTo(1);
        }
    }
}
