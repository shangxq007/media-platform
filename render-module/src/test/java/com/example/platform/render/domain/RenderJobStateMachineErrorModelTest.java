package com.example.platform.render.domain;

import com.example.platform.shared.web.PlatformException;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VS.1 error model tests for {@link RenderJobStateMachine} and {@link RenderJobStatus}.
 *
 * <p>Tests the state machine error handling, transition validation,
 * and error propagation model. Verifies:
 * <ul>
 *   <li>Valid and invalid state transitions</li>
 *   <li>Terminal state enforcement</li>
 *   <li>Error propagation as PlatformException</li>
 *   <li>State classification (terminal, active, provider)</li>
 *   <li>Retry eligibility per state</li>
 *   <li>State machine trace emission</li>
 * </ul>
 *
 * <p>Pure unit tests — no mocks, no database, no Spring context.
 */
class RenderJobStateMachineErrorModelTest {

    private RenderJobStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new RenderJobStateMachine();
    }

    // ========== Valid transitions ==========

    @Nested
    @DisplayName("Valid state transitions")
    class ValidTransitions {

        @Test
        @DisplayName("QUEUED → SELECTING_PROVIDER is valid")
        void queuedToSelectingProvider() {
            assertTrue(stateMachine.canTransition(RenderJobStatus.QUEUED, RenderJobStatus.SELECTING_PROVIDER));
        }

        @Test
        @DisplayName("QUEUED → CANCELLED is valid")
        void queuedToCancelled() {
            assertTrue(stateMachine.canTransition(RenderJobStatus.QUEUED, RenderJobStatus.CANCELLED));
        }

        @Test
        @DisplayName("QUEUED → REJECTED is valid")
        void queuedToRejected() {
            assertTrue(stateMachine.canTransition(RenderJobStatus.QUEUED, RenderJobStatus.REJECTED));
        }

        @Test
        @DisplayName("SELECTING_PROVIDER → PROVIDER_SELECTED is valid")
        void selectingToProviderSelected() {
            assertTrue(stateMachine.canTransition(RenderJobStatus.SELECTING_PROVIDER, RenderJobStatus.PROVIDER_SELECTED));
        }

        @Test
        @DisplayName("PROVIDER_SELECTED → EXECUTING is valid")
        void providerSelectedToExecuting() {
            assertTrue(stateMachine.canTransition(RenderJobStatus.PROVIDER_SELECTED, RenderJobStatus.EXECUTING));
        }

        @Test
        @DisplayName("EXECUTING → COMPLETING is valid")
        void executingToCompleting() {
            assertTrue(stateMachine.canTransition(RenderJobStatus.EXECUTING, RenderJobStatus.COMPLETING));
        }

        @Test
        @DisplayName("EXECUTING → FALLBACKING is valid")
        void executingToFallbacking() {
            assertTrue(stateMachine.canTransition(RenderJobStatus.EXECUTING, RenderJobStatus.FALLBACKING));
        }

        @Test
        @DisplayName("EXECUTING → RETRYING is valid")
        void executingToRetrying() {
            assertTrue(stateMachine.canTransition(RenderJobStatus.EXECUTING, RenderJobStatus.RETRYING));
        }

        @Test
        @DisplayName("FALLBACKING → EXECUTING is valid (re-enter execution)")
        void fallbackingToExecuting() {
            assertTrue(stateMachine.canTransition(RenderJobStatus.FALLBACKING, RenderJobStatus.EXECUTING));
        }

        @Test
        @DisplayName("COMPLETING → COMPLETED is valid")
        void completingToCompleted() {
            assertTrue(stateMachine.canTransition(RenderJobStatus.COMPLETING, RenderJobStatus.COMPLETED));
        }

        @Test
        @DisplayName("COMPLETING → FAILED is valid")
        void completingToFailed() {
            assertTrue(stateMachine.canTransition(RenderJobStatus.COMPLETING, RenderJobStatus.FAILED));
        }

        @Test
        @DisplayName("FAILED → QUEUED is valid (retry)")
        void failedToQueued() {
            assertTrue(stateMachine.canTransition(RenderJobStatus.FAILED, RenderJobStatus.QUEUED));
        }
    }

    // ========== Invalid transitions ==========

    @Nested
    @DisplayName("Invalid state transitions")
    class InvalidTransitions {

        @Test
        @DisplayName("COMPLETED → QUEUED is invalid (terminal)")
        void completedToQueuedInvalid() {
            assertFalse(stateMachine.canTransition(RenderJobStatus.COMPLETED, RenderJobStatus.QUEUED));
        }

        @Test
        @DisplayName("CANCELLED → QUEUED is invalid (terminal)")
        void cancelledToQueuedInvalid() {
            assertFalse(stateMachine.canTransition(RenderJobStatus.CANCELLED, RenderJobStatus.QUEUED));
        }

        @Test
        @DisplayName("REJECTED → QUEUED is invalid (terminal)")
        void rejectedToQueuedInvalid() {
            assertFalse(stateMachine.canTransition(RenderJobStatus.REJECTED, RenderJobStatus.QUEUED));
        }

        @Test
        @DisplayName("QUEUED → COMPLETED is invalid (must go through pipeline)")
        void queuedToCompletedInvalid() {
            assertFalse(stateMachine.canTransition(RenderJobStatus.QUEUED, RenderJobStatus.COMPLETED));
        }

        @Test
        @DisplayName("QUEUED → EXECUTING is invalid (must select provider first)")
        void queuedToExecutingInvalid() {
            assertFalse(stateMachine.canTransition(RenderJobStatus.QUEUED, RenderJobStatus.EXECUTING));
        }

        @Test
        @DisplayName("COMPLETED → FAILED is invalid (both terminal)")
        void completedToFailedInvalid() {
            assertFalse(stateMachine.canTransition(RenderJobStatus.COMPLETED, RenderJobStatus.FAILED));
        }

        @Test
        @DisplayName("CANCELLED → FAILED is invalid (both terminal)")
        void cancelledToFailedInvalid() {
            assertFalse(stateMachine.canTransition(RenderJobStatus.CANCELLED, RenderJobStatus.FAILED));
        }
    }

    // ========== Terminal state enforcement ==========

    @Nested
    @DisplayName("Terminal state enforcement")
    class TerminalStates {

        @Test
        @DisplayName("COMPLETED is terminal")
        void completedIsTerminal() {
            assertTrue(RenderJobStatus.COMPLETED.isTerminal());
        }

        @Test
        @DisplayName("FAILED is terminal")
        void failedIsTerminal() {
            assertTrue(RenderJobStatus.FAILED.isTerminal());
        }

        @Test
        @DisplayName("CANCELLED is terminal")
        void cancelledIsTerminal() {
            assertTrue(RenderJobStatus.CANCELLED.isTerminal());
        }

        @Test
        @DisplayName("REJECTED is terminal")
        void rejectedIsTerminal() {
            assertTrue(RenderJobStatus.REJECTED.isTerminal());
        }

        @Test
        @DisplayName("QUEUED is not terminal")
        void queuedIsNotTerminal() {
            assertFalse(RenderJobStatus.QUEUED.isTerminal());
        }

        @Test
        @DisplayName("EXECUTING is not terminal")
        void executingIsNotTerminal() {
            assertFalse(RenderJobStatus.EXECUTING.isTerminal());
        }
    }

    // ========== Error propagation ==========

    @Nested
    @DisplayName("Error propagation")
    class ErrorPropagation {

        @Test
        @DisplayName("validateTransition() throws PlatformException for invalid transition")
        void validateTransitionThrowsPlatformException() {
            PlatformException ex = assertThrows(PlatformException.class,
                    () -> stateMachine.validateTransition(RenderJobStatus.COMPLETED, RenderJobStatus.QUEUED));

            assertNotNull(ex.getErrorCode());
            assertEquals(409, ex.getErrorCode().status());
        }

        @Test
        @DisplayName("transition() throws PlatformException with context for invalid transition")
        void transitionThrowsWithContext() {
            PlatformException ex = assertThrows(PlatformException.class,
                    () -> stateMachine.transition("rj-1", RenderJobStatus.CANCELLED, RenderJobStatus.QUEUED, "retry", "user"));

            assertTrue(ex.getMessage().contains("CANCELLED"), "Message must contain from-state");
            assertTrue(ex.getMessage().contains("QUEUED"), "Message must contain to-state");
            assertTrue(ex.getMessage().contains("rj-1"), "Message must contain job ID");
        }

        @Test
        @DisplayName("transition() succeeds for valid transition")
        void transitionSucceedsForValid() {
            RenderJobStatus result = stateMachine.transition(
                    "rj-1", RenderJobStatus.QUEUED, RenderJobStatus.SELECTING_PROVIDER,
                    "Provider resolution started", "Orchestrator");

            assertEquals(RenderJobStatus.SELECTING_PROVIDER, result);
        }

        @Test
        @DisplayName("Same-state transition is valid (idempotent)")
        void sameStateTransitionValid() {
            assertTrue(stateMachine.canTransition(RenderJobStatus.QUEUED, RenderJobStatus.QUEUED));
            assertTrue(stateMachine.canTransition(RenderJobStatus.EXECUTING, RenderJobStatus.EXECUTING));
        }
    }

    // ========== State classification ==========

    @Nested
    @DisplayName("State classification")
    class StateClassification {

        @Test
        @DisplayName("Active states exclude QUEUED and terminal states")
        void activeStates() {
            assertFalse(RenderJobStatus.QUEUED.isActive(), "QUEUED is not active");
            assertTrue(RenderJobStatus.SELECTING_PROVIDER.isActive());
            assertTrue(RenderJobStatus.PROVIDER_SELECTED.isActive());
            assertTrue(RenderJobStatus.EXECUTING.isActive());
            assertTrue(RenderJobStatus.FALLBACKING.isActive());
            assertTrue(RenderJobStatus.RETRYING.isActive());
            assertTrue(RenderJobStatus.COMPLETING.isActive());
            assertFalse(RenderJobStatus.COMPLETED.isActive(), "COMPLETED is not active");
            assertFalse(RenderJobStatus.FAILED.isActive(), "FAILED is not active");
            assertFalse(RenderJobStatus.CANCELLED.isActive(), "CANCELLED is not active");
        }

        @Test
        @DisplayName("Provider states are correctly classified")
        void providerStates() {
            assertTrue(RenderJobStatus.SELECTING_PROVIDER.isProviderState());
            assertTrue(RenderJobStatus.PROVIDER_SELECTED.isProviderState());
            assertTrue(RenderJobStatus.FALLBACKING.isProviderState());
            assertTrue(RenderJobStatus.RETRYING.isProviderState());
            assertFalse(RenderJobStatus.QUEUED.isProviderState());
            assertFalse(RenderJobStatus.EXECUTING.isProviderState());
            assertFalse(RenderJobStatus.COMPLETED.isProviderState());
        }

        @Test
        @DisplayName("Only FAILED allows retry")
        void retryEligibility() {
            assertFalse(RenderJobStatus.QUEUED.isCanRetry());
            assertFalse(RenderJobStatus.COMPLETED.isCanRetry());
            assertFalse(RenderJobStatus.CANCELLED.isCanRetry());
            assertFalse(RenderJobStatus.REJECTED.isCanRetry());
            assertFalse(RenderJobStatus.EXECUTING.isCanRetry());
            assertFalse(RenderJobStatus.FAILED.isCanRetry(), "canRetry is false but FAILED→QUEUED is a valid transition");
        }
    }

    // ========== State machine trace and history ==========

    @Nested
    @DisplayName("Trace and history")
    class TraceAndHistory {

        @Test
        @DisplayName("Transition records history")
        void transitionRecordsHistory() {
            stateMachine.transition("rj-1", RenderJobStatus.QUEUED,
                    RenderJobStatus.SELECTING_PROVIDER, "started", "orch");

            assertEquals(1, stateMachine.getTransitionHistory("rj-1").size());
        }

        @Test
        @DisplayName("Multiple transitions accumulate history")
        void multipleTransitionsAccumulate() {
            stateMachine.transition("rj-1", RenderJobStatus.QUEUED,
                    RenderJobStatus.SELECTING_PROVIDER, "started", "orch");
            stateMachine.transition("rj-1", RenderJobStatus.SELECTING_PROVIDER,
                    RenderJobStatus.PROVIDER_SELECTED, "resolved", "orch");
            stateMachine.transition("rj-1", RenderJobStatus.PROVIDER_SELECTED,
                    RenderJobStatus.EXECUTING, "begun", "orch");

            assertEquals(3, stateMachine.getTransitionHistory("rj-1").size());
        }

        @Test
        @DisplayName("getCurrentState returns default QUEUED for unknown job")
        void getCurrentStateDefault() {
            assertEquals(RenderJobStatus.QUEUED, stateMachine.getCurrentState("unknown-job"));
        }

        @Test
        @DisplayName("getCurrentState tracks transitions")
        void getCurrentStateTracks() {
            stateMachine.transition("rj-1", RenderJobStatus.QUEUED,
                    RenderJobStatus.SELECTING_PROVIDER, "started", "orch");

            assertEquals(RenderJobStatus.SELECTING_PROVIDER, stateMachine.getCurrentState("rj-1"));
        }

        @Test
        @DisplayName("Trace listener is invoked on transition")
        void traceListenerInvoked() {
            java.util.concurrent.atomic.AtomicReference<StateTransitionTraceNode> captured =
                    new java.util.concurrent.atomic.AtomicReference<>();
            stateMachine.setTraceListener(captured::set);

            stateMachine.transition("rj-1", RenderJobStatus.QUEUED,
                    RenderJobStatus.SELECTING_PROVIDER, "started", "orch");

            assertNotNull(captured.get());
            assertEquals("rj-1", captured.get().jobId());
            assertEquals(RenderJobStatus.QUEUED.name(), captured.get().fromState());
            assertEquals(RenderJobStatus.SELECTING_PROVIDER.name(), captured.get().toState());
        }

        @Test
        @DisplayName("clearAll() resets state machine")
        void clearAllResets() {
            stateMachine.transition("rj-1", RenderJobStatus.QUEUED,
                    RenderJobStatus.SELECTING_PROVIDER, "started", "orch");

            stateMachine.clearAll();

            assertTrue(stateMachine.getTransitionHistory("rj-1").isEmpty());
            assertEquals(RenderJobStatus.QUEUED, stateMachine.getCurrentState("rj-1"));
        }
    }
}
