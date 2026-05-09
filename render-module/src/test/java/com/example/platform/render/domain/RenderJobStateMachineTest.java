package com.example.platform.render.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.web.PlatformException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenderJobStateMachineTest {

    private RenderJobStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new RenderJobStateMachine();
    }

    // --- Valid transitions ---

    @Test
    void queuedToAiProcessingIsValid() {
        assertTrue(stateMachine.canTransition(RenderJobStatus.QUEUED, RenderJobStatus.AI_PROCESSING));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.QUEUED, RenderJobStatus.AI_PROCESSING));
    }

    @Test
    void queuedToCancelledIsValid() {
        assertTrue(stateMachine.canTransition(RenderJobStatus.QUEUED, RenderJobStatus.CANCELLED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.QUEUED, RenderJobStatus.CANCELLED));
    }

    @Test
    void queuedToRejectedIsValid() {
        assertTrue(stateMachine.canTransition(RenderJobStatus.QUEUED, RenderJobStatus.REJECTED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.QUEUED, RenderJobStatus.REJECTED));
    }

    @Test
    void aiProcessingToRenderingIsValid() {
        assertTrue(stateMachine.canTransition(RenderJobStatus.AI_PROCESSING, RenderJobStatus.RENDERING));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.AI_PROCESSING, RenderJobStatus.RENDERING));
    }

    @Test
    void aiProcessingToFailedIsValid() {
        assertTrue(stateMachine.canTransition(RenderJobStatus.AI_PROCESSING, RenderJobStatus.FAILED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.AI_PROCESSING, RenderJobStatus.FAILED));
    }

    @Test
    void aiProcessingToCancelledIsValid() {
        assertTrue(stateMachine.canTransition(RenderJobStatus.AI_PROCESSING, RenderJobStatus.CANCELLED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.AI_PROCESSING, RenderJobStatus.CANCELLED));
    }

    @Test
    void renderingToCompletedIsValid() {
        assertTrue(stateMachine.canTransition(RenderJobStatus.RENDERING, RenderJobStatus.COMPLETED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.RENDERING, RenderJobStatus.COMPLETED));
    }

    @Test
    void renderingToFailedIsValid() {
        assertTrue(stateMachine.canTransition(RenderJobStatus.RENDERING, RenderJobStatus.FAILED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.RENDERING, RenderJobStatus.FAILED));
    }

    @Test
    void renderingToCancelledIsValid() {
        assertTrue(stateMachine.canTransition(RenderJobStatus.RENDERING, RenderJobStatus.CANCELLED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.RENDERING, RenderJobStatus.CANCELLED));
    }

    @Test
    void failedToQueuedIsValidForRetry() {
        assertTrue(stateMachine.canTransition(RenderJobStatus.FAILED, RenderJobStatus.QUEUED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.FAILED, RenderJobStatus.QUEUED));
    }

    // --- Invalid transitions ---

    @Test
    void completedCannotTransitionToAnything() {
        assertFalse(stateMachine.canTransition(RenderJobStatus.COMPLETED, RenderJobStatus.FAILED));
        assertFalse(stateMachine.canTransition(RenderJobStatus.COMPLETED, RenderJobStatus.QUEUED));
        assertFalse(stateMachine.canTransition(RenderJobStatus.COMPLETED, RenderJobStatus.RENDERING));
        assertThrows(PlatformException.class,
                () -> stateMachine.validateTransition(RenderJobStatus.COMPLETED, RenderJobStatus.FAILED));
    }

    @Test
    void cancelledCannotTransitionToAnything() {
        assertFalse(stateMachine.canTransition(RenderJobStatus.CANCELLED, RenderJobStatus.RENDERING));
        assertFalse(stateMachine.canTransition(RenderJobStatus.CANCELLED, RenderJobStatus.QUEUED));
        assertFalse(stateMachine.canTransition(RenderJobStatus.CANCELLED, RenderJobStatus.FAILED));
        assertThrows(PlatformException.class,
                () -> stateMachine.validateTransition(RenderJobStatus.CANCELLED, RenderJobStatus.RENDERING));
    }

    @Test
    void rejectedCannotTransitionToAnything() {
        assertFalse(stateMachine.canTransition(RenderJobStatus.REJECTED, RenderJobStatus.QUEUED));
        assertFalse(stateMachine.canTransition(RenderJobStatus.REJECTED, RenderJobStatus.RENDERING));
        assertThrows(PlatformException.class,
                () -> stateMachine.validateTransition(RenderJobStatus.REJECTED, RenderJobStatus.QUEUED));
    }

    @Test
    void completedCannotRevertToRendering() {
        assertFalse(stateMachine.canTransition(RenderJobStatus.COMPLETED, RenderJobStatus.RENDERING));
        assertThrows(PlatformException.class,
                () -> stateMachine.validateTransition(RenderJobStatus.COMPLETED, RenderJobStatus.RENDERING));
    }

    @Test
    void queuedCannotSkipToRendering() {
        assertFalse(stateMachine.canTransition(RenderJobStatus.QUEUED, RenderJobStatus.RENDERING));
        assertThrows(PlatformException.class,
                () -> stateMachine.validateTransition(RenderJobStatus.QUEUED, RenderJobStatus.RENDERING));
    }

    @Test
    void queuedCannotSkipToCompleted() {
        assertFalse(stateMachine.canTransition(RenderJobStatus.QUEUED, RenderJobStatus.COMPLETED));
        assertThrows(PlatformException.class,
                () -> stateMachine.validateTransition(RenderJobStatus.QUEUED, RenderJobStatus.COMPLETED));
    }

    @Test
    void renderingCannotGoBackToAiProcessing() {
        assertFalse(stateMachine.canTransition(RenderJobStatus.RENDERING, RenderJobStatus.AI_PROCESSING));
        assertThrows(PlatformException.class,
                () -> stateMachine.validateTransition(RenderJobStatus.RENDERING, RenderJobStatus.AI_PROCESSING));
    }

    @Test
    void failedCannotTransitionToCompleted() {
        assertFalse(stateMachine.canTransition(RenderJobStatus.FAILED, RenderJobStatus.COMPLETED));
        assertThrows(PlatformException.class,
                () -> stateMachine.validateTransition(RenderJobStatus.FAILED, RenderJobStatus.COMPLETED));
    }

    @Test
    void failedCannotTransitionToCancelled() {
        assertFalse(stateMachine.canTransition(RenderJobStatus.FAILED, RenderJobStatus.CANCELLED));
        assertThrows(PlatformException.class,
                () -> stateMachine.validateTransition(RenderJobStatus.FAILED, RenderJobStatus.CANCELLED));
    }

    // --- Retry path: FAILED -> QUEUED -> AI_PROCESSING -> RENDERING -> COMPLETED ---

    @Test
    void fullRetryPathIsValid() {
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.FAILED, RenderJobStatus.QUEUED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.QUEUED, RenderJobStatus.AI_PROCESSING));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.AI_PROCESSING, RenderJobStatus.RENDERING));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.RENDERING, RenderJobStatus.COMPLETED));
    }

    // --- Same state ---

    @Test
    void sameStateTransitionIsAllowed() {
        assertTrue(stateMachine.canTransition(RenderJobStatus.QUEUED, RenderJobStatus.QUEUED));
        assertTrue(stateMachine.canTransition(RenderJobStatus.COMPLETED, RenderJobStatus.COMPLETED));
    }
}
