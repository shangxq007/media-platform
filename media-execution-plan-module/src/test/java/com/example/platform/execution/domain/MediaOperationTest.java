package com.example.platform.execution.domain;

import com.example.platform.execution.domain.operation.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for MediaOperation hierarchy.
 */
@DisplayName("MediaOperation Hierarchy")
class MediaOperationTest {

    @Test
    @DisplayName("All 15 operation types exist and implement MediaOperation")
    void allOperationTypesExist() {
        MediaOperation[] ops = {
                MediaInspectionOperation.fullInspection(),
                DecodeOperation.of("h264"),
                TrimOperation.of(Duration.ZERO, Duration.ofSeconds(10)),
                ScaleOperation.to(1920, 1080),
                CropOperation.of(0, 0, 100, 100),
                AudioMixOperation.stereoMixDown(List.of("stereo")),
                ComposeOperation.layers(List.of("layer1"), "1920x1080", "30"),
                TranscodeOperation.to("h264", "mp4"),
                ThumbnailOperation.at(320, 240),
                WaveformOperation.standard(800, 200),
                SubtitleBurnInOperation.of("sub-001"),
                AnalysisOperation.sceneDetection(),
                GeneratedMediaOperation.ai("model-1", Map.of("prompt", "test")),
                PackageOperation.mp4(List.of("video", "audio")),
                IntegrityVerificationOperation.checksum()
        };
        assertThat(ops).hasSize(15);
    }

    @Test
    @DisplayName("Each operation type has correct step kind")
    void operationStepKindsCorrect() {
        assertThat(MediaInspectionOperation.minimal().stepKind()).isEqualTo(ExecutionStepKind.INSPECT);
        assertThat(DecodeOperation.of("h264").stepKind()).isEqualTo(ExecutionStepKind.DECODE);
        assertThat(TrimOperation.toDuration(Duration.ofSeconds(10)).stepKind()).isEqualTo(ExecutionStepKind.TRANSFORM);
        assertThat(ScaleOperation.to(1920, 1080).stepKind()).isEqualTo(ExecutionStepKind.TRANSFORM);
        assertThat(CropOperation.of(0, 0, 100, 100).stepKind()).isEqualTo(ExecutionStepKind.TRANSFORM);
        assertThat(AudioMixOperation.passthrough("stereo").stepKind()).isEqualTo(ExecutionStepKind.TRANSFORM);
        assertThat(ComposeOperation.layers(List.of("a"), "1920x1080", "30").stepKind()).isEqualTo(ExecutionStepKind.COMPOSE);
        assertThat(TranscodeOperation.to("h264", "mp4").stepKind()).isEqualTo(ExecutionStepKind.ENCODE);
        assertThat(ThumbnailOperation.at(100, 100).stepKind()).isEqualTo(ExecutionStepKind.GENERATE);
        assertThat(WaveformOperation.standard(100, 100).stepKind()).isEqualTo(ExecutionStepKind.GENERATE);
        assertThat(SubtitleBurnInOperation.of("sub").stepKind()).isEqualTo(ExecutionStepKind.TRANSFORM);
        assertThat(AnalysisOperation.qualityAnalysis().stepKind()).isEqualTo(ExecutionStepKind.ANALYZE);
        assertThat(GeneratedMediaOperation.deterministic("proc", 42L).stepKind()).isEqualTo(ExecutionStepKind.GENERATE);
        assertThat(PackageOperation.mp4(List.of("v")).stepKind()).isEqualTo(ExecutionStepKind.PACKAGE);
        assertThat(IntegrityVerificationOperation.checksum().stepKind()).isEqualTo(ExecutionStepKind.VERIFY);
    }

    @Test
    @DisplayName("Operations have valid schema versions")
    void operationsHaveSchemaVersions() {
        assertThat(MediaInspectionOperation.minimal().schemaVersion()).isPositive();
        assertThat(DecodeOperation.of("h264").schemaVersion()).isPositive();
        assertThat(TranscodeOperation.to("h264", "mp4").schemaVersion()).isPositive();
    }

    @Test
    @DisplayName("Trim operation validates time range")
    void trimValidatesTimeRange() {
        assertThatThrownBy(() -> TrimOperation.of(Duration.ofSeconds(10), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Scale operation validates dimensions")
    void scaleValidatesDimensions() {
        assertThatThrownBy(() -> ScaleOperation.to(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Crop operation validates dimensions")
    void cropValidatesDimensions() {
        assertThatThrownBy(() -> CropOperation.of(-1, 0, 100, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CropOperation.of(0, 0, -1, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Operation canonical forms are deterministic")
    void canonicalFormsDeterministic() {
        TrimOperation t1 = TrimOperation.of(Duration.ZERO, Duration.ofSeconds(10));
        TrimOperation t2 = TrimOperation.of(Duration.ZERO, Duration.ofSeconds(10));
        assertThat(t1.canonicalForm()).isEqualTo(t2.canonicalForm());

        ScaleOperation s1 = ScaleOperation.to(1920, 1080);
        ScaleOperation s2 = ScaleOperation.to(1920, 1080);
        assertThat(s1.canonicalForm()).isEqualTo(s2.canonicalForm());
    }

    @Test
    @DisplayName("Different operations produce different canonical forms")
    void differentOperationsDifferentCanonicalForms() {
        ScaleOperation scale = ScaleOperation.to(1920, 1080);
        CropOperation crop = CropOperation.of(0, 0, 1920, 1080);
        assertThat(scale.canonicalForm()).isNotEqualTo(crop.canonicalForm());
    }

    @Test
    @DisplayName("Analysis operation validates confidence threshold")
    void analysisValidatesConfidence() {
        assertThatThrownBy(() -> AnalysisOperation.faceDetection(1.5f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AnalysisOperation.faceDetection(-0.1f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Package operation factory methods work")
    void packageFactories() {
        PackageOperation mp4 = PackageOperation.mp4(List.of("video", "audio"));
        assertThat(mp4.containerFormat()).isEqualTo("mp4");

        PackageOperation dash = PackageOperation.dash(List.of("video"));
        assertThat(dash.containerFormat()).isEqualTo("dash");

        PackageOperation hls = PackageOperation.hls(List.of("video"));
        assertThat(hls.containerFormat()).isEqualTo("hls");
    }

    @Test
    @DisplayName("GeneratedMedia operation canonical form with sorted params")
    void generatedMediaCanonicalSorted() {
        GeneratedMediaOperation g1 = GeneratedMediaOperation.ai("model", Map.of("b", "2", "a", "1"));
        GeneratedMediaOperation g2 = GeneratedMediaOperation.ai("model", Map.of("a", "1", "b", "2"));
        assertThat(g1.canonicalForm()).isEqualTo(g2.canonicalForm());
    }
}
