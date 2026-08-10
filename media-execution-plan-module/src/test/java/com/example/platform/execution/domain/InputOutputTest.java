package com.example.platform.execution.domain;

import com.example.platform.artifact.domain.ArtifactId;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.storage.contract.ContentDigest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for input bindings and output declarations.
 */
@DisplayName("Input and Output Declarations")
class InputOutputTest {

    private static final ContentDigest DIGEST = ContentDigest.sha256("a".repeat(64));

    @Test
    @DisplayName("ExecutionInputBinding validates required fields")
    void inputBindingValidates() {
        assertThatThrownBy(() -> new ExecutionInputBinding(
                null, new ArtifactId("a1"), DIGEST, 100L, "video/mp4",
                ExecutionInputRole.PRIMARY_MEDIA, true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("ExecutionInputBinding validates non-negative byte length")
    void inputBindingValidatesBytes() {
        assertThatThrownBy(() -> ExecutionInputBinding.primaryMedia(
                new ExecutionInputId("in-1"), new ArtifactId("a1"),
                DIGEST, -1L, "video/mp4"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ExecutionInputBinding factory methods work")
    void inputBindingFactories() {
        ExecutionInputBinding primary = ExecutionInputBinding.primaryMedia(
                new ExecutionInputId("in-1"), new ArtifactId("a1"),
                DIGEST, 1000L, "video/mp4");
        assertThat(primary.isRequired()).isTrue();
        assertThat(primary.inputRole()).isEqualTo(ExecutionInputRole.PRIMARY_MEDIA);

        ExecutionInputBinding optional = ExecutionInputBinding.optional(
                new ExecutionInputId("in-2"), new ArtifactId("a2"),
                DIGEST, 500L, "image/png", ExecutionInputRole.OVERLAY);
        assertThat(optional.isRequired()).isFalse();
    }

    @Test
    @DisplayName("ExecutionInputBinding canonical form is deterministic")
    void inputBindingCanonicalDeterministic() {
        ExecutionInputBinding b1 = ExecutionInputBinding.primaryMedia(
                new ExecutionInputId("in-1"), new ArtifactId("a1"),
                DIGEST, 1000L, "video/mp4");
        ExecutionInputBinding b2 = ExecutionInputBinding.primaryMedia(
                new ExecutionInputId("in-1"), new ArtifactId("a1"),
                DIGEST, 1000L, "video/mp4");
        assertThat(b1.canonicalForm()).isEqualTo(b2.canonicalForm());
    }

    @Test
    @DisplayName("ExecutionOutputDeclaration validates required fields")
    void outputDeclarationValidates() {
        assertThatThrownBy(() -> new ExecutionOutputDeclaration(
                null, ArtifactKind.DELIVERY_RENDITION, "video/mp4",
                ExecutionOutputRole.PRIMARY_OUTPUT, new ExecutionStepId("s1"),
                Map.of(), "standard"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("ExecutionOutputDeclaration factory methods work")
    void outputDeclarationFactories() {
        ExecutionOutputDeclaration primary = ExecutionOutputDeclaration.primary(
                new ExecutionOutputId("out-1"), ArtifactKind.DELIVERY_RENDITION,
                "video/mp4", new ExecutionStepId("s1"));
        assertThat(primary.isPrimaryOutput()).isTrue();
        assertThat(primary.isIntermediate()).isFalse();

        ExecutionOutputDeclaration intermediate = ExecutionOutputDeclaration.intermediate(
                new ExecutionOutputId("out-2"), ArtifactKind.DERIVED_MEDIA,
                "raw/video", new ExecutionStepId("s2"));
        assertThat(intermediate.isPrimaryOutput()).isFalse();
        assertThat(intermediate.isIntermediate()).isTrue();
    }

    @Test
    @DisplayName("ExecutionOutputDeclaration with properties works")
    void outputDeclarationWithProperties() {
        ExecutionOutputDeclaration withProps = ExecutionOutputDeclaration.withProperties(
                new ExecutionOutputId("out-1"), ArtifactKind.DELIVERY_RENDITION,
                "video/mp4", ExecutionOutputRole.PRIMARY_OUTPUT,
                new ExecutionStepId("s1"),
                Map.of("bitrate", "5000000", "codec", "h264"));
        assertThat(withProps.expectedProperties()).containsEntry("bitrate", "5000000");
    }

    @Test
    @DisplayName("ExecutionOutputDeclaration canonical form is deterministic")
    void outputDeclarationCanonicalDeterministic() {
        ExecutionOutputDeclaration o1 = ExecutionOutputDeclaration.primary(
                new ExecutionOutputId("out-1"), ArtifactKind.DELIVERY_RENDITION,
                "video/mp4", new ExecutionStepId("s1"));
        ExecutionOutputDeclaration o2 = ExecutionOutputDeclaration.primary(
                new ExecutionOutputId("out-1"), ArtifactKind.DELIVERY_RENDITION,
                "video/mp4", new ExecutionStepId("s1"));
        assertThat(o1.canonicalForm()).isEqualTo(o2.canonicalForm());
    }

    @Test
    @DisplayName("Different outputs produce different canonical forms")
    void differentOutputsDifferentCanonicalForms() {
        ExecutionOutputDeclaration o1 = ExecutionOutputDeclaration.primary(
                new ExecutionOutputId("out-1"), ArtifactKind.DELIVERY_RENDITION,
                "video/mp4", new ExecutionStepId("s1"));
        ExecutionOutputDeclaration o2 = ExecutionOutputDeclaration.primary(
                new ExecutionOutputId("out-2"), ArtifactKind.DELIVERY_RENDITION,
                "video/mp4", new ExecutionStepId("s1"));
        assertThat(o1.canonicalForm()).isNotEqualTo(o2.canonicalForm());
    }
}
