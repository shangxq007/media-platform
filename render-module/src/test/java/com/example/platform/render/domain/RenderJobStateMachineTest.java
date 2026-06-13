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
    void queuedToSelectingProviderIsValid() {
        assertTrue(stateMachine.canTransition(RenderJobStatus.QUEUED, RenderJobStatus.SELECTING_PROVIDER));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.QUEUED, RenderJobStatus.SELECTING_PROVIDER));
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
    void selectingProviderToProviderSelectedIsValid() {
        assertTrue(stateMachine.canTransition(RenderJobStatus.SELECTING_PROVIDER, RenderJobStatus.PROVIDER_SELECTED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.SELECTING_PROVIDER, RenderJobStatus.PROVIDER_SELECTED));
    }

    @Test
    void selectingProviderToFailedIsValid() {
        assertTrue(stateMachine.canTransition(RenderJobStatus.SELECTING_PROVIDER, RenderJobStatus.FAILED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.SELECTING_PROVIDER, RenderJobStatus.FAILED));
    }

    @Test
    void selectingProviderToCancelledIsValid() {
        assertTrue(stateMachine.canTransition(RenderJobStatus.SELECTING_PROVIDER, RenderJobStatus.CANCELLED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.SELECTING_PROVIDER, RenderJobStatus.CANCELLED));
    }

    @Test
    void providerSelectedToExecutingIsValid() {
        assertTrue(stateMachine.canTransition(RenderJobStatus.PROVIDER_SELECTED, RenderJobStatus.EXECUTING));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.PROVIDER_SELECTED, RenderJobStatus.EXECUTING));
    }

    @Test
    void executingToCompletingIsValid() {
        assertTrue(stateMachine.canTransition(RenderJobStatus.EXECUTING, RenderJobStatus.COMPLETING));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.EXECUTING, RenderJobStatus.COMPLETING));
    }

    @Test
    void executingToFailedIsValid() {
        assertTrue(stateMachine.canTransition(RenderJobStatus.EXECUTING, RenderJobStatus.FAILED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.EXECUTING, RenderJobStatus.FAILED));
    }

    @Test
    void executingToCancelledIsValid() {
        assertTrue(stateMachine.canTransition(RenderJobStatus.EXECUTING, RenderJobStatus.CANCELLED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.EXECUTING, RenderJobStatus.CANCELLED));
    }

    @Test
    void completingToCompletedIsValid() {
        assertTrue(stateMachine.canTransition(RenderJobStatus.COMPLETING, RenderJobStatus.COMPLETED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.COMPLETING, RenderJobStatus.COMPLETED));
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
        assertFalse(stateMachine.canTransition(RenderJobStatus.COMPLETED, RenderJobStatus.EXECUTING));
        assertThrows(PlatformException.class,
                () -> stateMachine.validateTransition(RenderJobStatus.COMPLETED, RenderJobStatus.FAILED));
    }

    @Test
    void cancelledCannotTransitionToAnything() {
        assertFalse(stateMachine.canTransition(RenderJobStatus.CANCELLED, RenderJobStatus.EXECUTING));
        assertFalse(stateMachine.canTransition(RenderJobStatus.CANCELLED, RenderJobStatus.QUEUED));
        assertFalse(stateMachine.canTransition(RenderJobStatus.CANCELLED, RenderJobStatus.FAILED));
        assertThrows(PlatformException.class,
                () -> stateMachine.validateTransition(RenderJobStatus.CANCELLED, RenderJobStatus.EXECUTING));
    }

    @Test
    void rejectedCannotTransitionToAnything() {
        assertFalse(stateMachine.canTransition(RenderJobStatus.REJECTED, RenderJobStatus.QUEUED));
        assertFalse(stateMachine.canTransition(RenderJobStatus.REJECTED, RenderJobStatus.EXECUTING));
        assertThrows(PlatformException.class,
                () -> stateMachine.validateTransition(RenderJobStatus.REJECTED, RenderJobStatus.QUEUED));
    }

    @Test
    void completedCannotRevertToExecuting() {
        assertFalse(stateMachine.canTransition(RenderJobStatus.COMPLETED, RenderJobStatus.EXECUTING));
        assertThrows(PlatformException.class,
                () -> stateMachine.validateTransition(RenderJobStatus.COMPLETED, RenderJobStatus.EXECUTING));
    }

    @Test
    void queuedCannotSkipToExecuting() {
        assertFalse(stateMachine.canTransition(RenderJobStatus.QUEUED, RenderJobStatus.EXECUTING));
        assertThrows(PlatformException.class,
                () -> stateMachine.validateTransition(RenderJobStatus.QUEUED, RenderJobStatus.EXECUTING));
    }

    @Test
    void queuedCannotSkipToCompleted() {
        assertFalse(stateMachine.canTransition(RenderJobStatus.QUEUED, RenderJobStatus.COMPLETED));
        assertThrows(PlatformException.class,
                () -> stateMachine.validateTransition(RenderJobStatus.QUEUED, RenderJobStatus.COMPLETED));
    }

    @Test
    void executingCannotGoBackToSelectingProvider() {
        assertFalse(stateMachine.canTransition(RenderJobStatus.EXECUTING, RenderJobStatus.SELECTING_PROVIDER));
        assertThrows(PlatformException.class,
                () -> stateMachine.validateTransition(RenderJobStatus.EXECUTING, RenderJobStatus.SELECTING_PROVIDER));
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

    // --- Full path ---

    @Test
    void fullRetryPathIsValid() {
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.FAILED, RenderJobStatus.QUEUED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.QUEUED, RenderJobStatus.SELECTING_PROVIDER));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.SELECTING_PROVIDER, RenderJobStatus.PROVIDER_SELECTED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.PROVIDER_SELECTED, RenderJobStatus.EXECUTING));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.EXECUTING, RenderJobStatus.COMPLETING));
        assertDoesNotThrow(() -> stateMachine.validateTransition(RenderJobStatus.COMPLETING, RenderJobStatus.COMPLETED));
    }

    // --- Same state ---

    @Test
    void sameStateTransitionIsAllowed() {
        assertTrue(stateMachine.canTransition(RenderJobStatus.QUEUED, RenderJobStatus.QUEUED));
        assertTrue(stateMachine.canTransition(RenderJobStatus.COMPLETED, RenderJobStatus.COMPLETED));
    }
}
