package com.example.platform.execution.domain;

import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.execution.domain.operation.TranscodeOperation;
import com.example.platform.execution.domain.operation.MediaInspectionOperation;
import com.example.platform.shared.digest.ContentDigest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Input Artifact Binding")
class InputArtifactBindingTest {

    @Nested
    @DisplayName("ExecutionInputBinding")
    class ExecutionInputBindingTest {

        @Test
        @DisplayName("creates primary media binding")
        void createsPrimaryMedia() {
            ExecutionInputBinding binding = ExecutionInputBinding.primaryMedia(
                    MediaExecutionPlanFixtures.inputId("in-1"),
                    MediaExecutionPlanFixtures.artifactId("art-001"),
                    MediaExecutionPlanFixtures.defaultDigest(),
                    1024L,
                    "video/mp4");

            assertThat(binding.inputId().value()).isEqualTo("in-1");
            assertThat(binding.artifactId().value()).isEqualTo("art-001");
            assertThat(binding.expectedByteLength()).isEqualTo(1024L);
            assertThat(binding.expectedMediaType()).isEqualTo("video/mp4");
            assertThat(binding.inputRole()).isEqualTo(ExecutionInputRole.PRIMARY_MEDIA);
            assertThat(binding.isRequired()).isTrue();
        }

        @Test
        @DisplayName("creates optional binding")
        void createsOptional() {
            ExecutionInputBinding binding = ExecutionInputBinding.optional(
                    MediaExecutionPlanFixtures.inputId("in-1"),
                    MediaExecutionPlanFixtures.artifactId("art-001"),
                    MediaExecutionPlanFixtures.defaultDigest(),
                    1024L,
                    "audio/aac",
                    ExecutionInputRole.AUDIO);

            assertThat(binding.isRequired()).isFalse();
            assertThat(binding.inputRole()).isEqualTo(ExecutionInputRole.AUDIO);
        }

        @Test
        @DisplayName("rejects null inputId")
        void rejectsNullInputId() {
            assertThatThrownBy(() -> new ExecutionInputBinding(
                    null,
                    MediaExecutionPlanFixtures.artifactId("art-001"),
                    MediaExecutionPlanFixtures.defaultDigest(),
                    1024L,
                    "video/mp4",
                    ExecutionInputRole.PRIMARY_MEDIA,
                    true))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects null artifactId")
        void rejectsNullArtifactId() {
            assertThatThrownBy(() -> new ExecutionInputBinding(
                    MediaExecutionPlanFixtures.inputId("in-1"),
                    null,
                    MediaExecutionPlanFixtures.defaultDigest(),
                    1024L,
                    "video/mp4",
                    ExecutionInputRole.PRIMARY_MEDIA,
                    true))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects null digest")
        void rejectsNullDigest() {
            assertThatThrownBy(() -> new ExecutionInputBinding(
                    MediaExecutionPlanFixtures.inputId("in-1"),
                    MediaExecutionPlanFixtures.artifactId("art-001"),
                    null,
                    1024L,
                    "video/mp4",
                    ExecutionInputRole.PRIMARY_MEDIA,
                    true))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects negative byte length")
        void rejectsNegativeByteLength() {
            assertThatThrownBy(() -> new ExecutionInputBinding(
                    MediaExecutionPlanFixtures.inputId("in-1"),
                    MediaExecutionPlanFixtures.artifactId("art-001"),
                    MediaExecutionPlanFixtures.defaultDigest(),
                    -1L,
                    "video/mp4",
                    ExecutionInputRole.PRIMARY_MEDIA,
                    true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non-negative");
        }

        @Test
        @DisplayName("rejects blank media type")
        void rejectsBlankMediaType() {
            assertThatThrownBy(() -> new ExecutionInputBinding(
                    MediaExecutionPlanFixtures.inputId("in-1"),
                    MediaExecutionPlanFixtures.artifactId("art-001"),
                    MediaExecutionPlanFixtures.defaultDigest(),
                    1024L,
                    "",
                    ExecutionInputRole.PRIMARY_MEDIA,
                    true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be blank");
        }

        @Test
        @DisplayName("canonical form is deterministic")
        void canonicalFormDeterministic() {
            ExecutionInputBinding a = ExecutionInputBinding.primaryMedia(
                    MediaExecutionPlanFixtures.inputId("in-1"),
                    MediaExecutionPlanFixtures.artifactId("art-001"),
                    MediaExecutionPlanFixtures.defaultDigest(),
                    1024L,
                    "video/mp4");

            ExecutionInputBinding b = ExecutionInputBinding.primaryMedia(
                    MediaExecutionPlanFixtures.inputId("in-1"),
                    MediaExecutionPlanFixtures.artifactId("art-001"),
                    MediaExecutionPlanFixtures.defaultDigest(),
                    1024L,
                    "video/mp4");

            assertThat(a.canonicalForm()).isEqualTo(b.canonicalForm());
        }
    }
}
