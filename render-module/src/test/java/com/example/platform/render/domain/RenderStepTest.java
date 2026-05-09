package com.example.platform.render.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RenderStepTest {

    @Test
    void shouldCreatePendingStep() {
        RenderStep step = RenderStep.pending("rs-1", "rp-1", RenderStepType.BUILD_TIMELINE);
        assertEquals("rs-1", step.id());
        assertEquals("rp-1", step.planId());
        assertEquals(RenderStepType.BUILD_TIMELINE, step.type());
        assertEquals(RenderStepStatus.PENDING, step.status());
        assertTrue(step.inputArtifactIds().isEmpty());
        assertTrue(step.outputArtifactIds().isEmpty());
        assertNull(step.errorCode());
        assertNull(step.startedAt());
    }

    @Test
    void shouldTransitionPendingToRunning() {
        RenderStep step = RenderStep.pending("rs-1", "rp-1", RenderStepType.BUILD_TIMELINE);
        RenderStep running = step.markRunning();

        assertEquals(RenderStepStatus.RUNNING, running.status());
        assertNotNull(running.startedAt());
        assertNull(running.completedAt());
    }

    @Test
    void shouldTransitionRunningToCompleted() {
        RenderStep step = RenderStep.pending("rs-1", "rp-1", RenderStepType.BUILD_TIMELINE)
                .markRunning();
        RenderStep completed = step.markCompleted(List.of("art-1", "art-2"));

        assertEquals(RenderStepStatus.COMPLETED, completed.status());
        assertEquals(List.of("art-1", "art-2"), completed.outputArtifactIds());
        assertNotNull(completed.completedAt());
        assertNotNull(completed.duration());
    }

    @Test
    void shouldTransitionRunningToFailed() {
        RenderStep step = RenderStep.pending("rs-1", "rp-1", RenderStepType.FFMPEG_TRANSCODE)
                .markRunning();
        RenderStep failed = step.markFailed("TRANSCODE_FAILED", "Codec not supported");

        assertEquals(RenderStepStatus.FAILED, failed.status());
        assertEquals("TRANSCODE_FAILED", failed.errorCode());
        assertEquals("Codec not supported", failed.errorMessage());
        assertNotNull(failed.completedAt());
    }

    @Test
    void shouldTransitionRunningToCancelled() {
        RenderStep step = RenderStep.pending("rs-1", "rp-1", RenderStepType.MLT_RENDER_TIMELINE)
                .markRunning();
        RenderStep cancelled = step.markCancelled();

        assertEquals(RenderStepStatus.CANCELLED, cancelled.status());
        assertNotNull(cancelled.completedAt());
    }

    @Test
    void shouldValidateStatusTransitions() {
        // Valid transitions
        assertDoesNotThrow(() -> RenderStep.validateTransition(
                RenderStepStatus.PENDING, RenderStepStatus.RUNNING));
        assertDoesNotThrow(() -> RenderStep.validateTransition(
                RenderStepStatus.PENDING, RenderStepStatus.SKIPPED));
        assertDoesNotThrow(() -> RenderStep.validateTransition(
                RenderStepStatus.RUNNING, RenderStepStatus.COMPLETED));
        assertDoesNotThrow(() -> RenderStep.validateTransition(
                RenderStepStatus.RUNNING, RenderStepStatus.FAILED));
        assertDoesNotThrow(() -> RenderStep.validateTransition(
                RenderStepStatus.RUNNING, RenderStepStatus.CANCELLED));
        assertDoesNotThrow(() -> RenderStep.validateTransition(
                RenderStepStatus.FAILED, RenderStepStatus.PENDING));
        assertDoesNotThrow(() -> RenderStep.validateTransition(
                RenderStepStatus.PENDING, RenderStepStatus.CANCELLED));
    }

    @Test
    void shouldRejectInvalidStatusTransitions() {
        assertThrows(IllegalArgumentException.class, () -> RenderStep.validateTransition(
                RenderStepStatus.COMPLETED, RenderStepStatus.RUNNING));
        assertThrows(IllegalArgumentException.class, () -> RenderStep.validateTransition(
                RenderStepStatus.CANCELLED, RenderStepStatus.RUNNING));
        assertThrows(IllegalArgumentException.class, () -> RenderStep.validateTransition(
                RenderStepStatus.PENDING, RenderStepStatus.COMPLETED));
        assertThrows(IllegalArgumentException.class, () -> RenderStep.validateTransition(
                RenderStepStatus.SKIPPED, RenderStepStatus.RUNNING));
    }

    @Test
    void shouldAllowSameStatusTransition() {
        assertDoesNotThrow(() -> RenderStep.validateTransition(
                RenderStepStatus.PENDING, RenderStepStatus.PENDING));
    }

    @Test
    void shouldCreateStepWithParameters() {
        RenderStep step = RenderStep.pending("rs-1", "rp-1",
                RenderStepType.FFMPEG_TRANSCODE,
                Map.of("bitrate", "8000", "codec", "h264"));
        assertEquals("8000", step.parameters().get("bitrate"));
        assertEquals("h264", step.parameters().get("codec"));
    }
}
