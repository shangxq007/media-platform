package com.example.platform.artifact.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ArtifactStateMachine — pure, deterministic state transitions.
 */
@DisplayName("ArtifactStateMachine")
class ArtifactStateMachineTest {

    @Nested
    @DisplayName("Valid transitions")
    class ValidTransitions {

        @Test
        @DisplayName("REGISTERING -> AVAILABLE")
        void registeringToAvailable() {
            assertThat(ArtifactStateMachine.canTransition(ArtifactState.REGISTERING, ArtifactState.AVAILABLE)).isTrue();
        }

        @Test
        @DisplayName("REGISTERING -> FAILED")
        void registeringToFailed() {
            assertThat(ArtifactStateMachine.canTransition(ArtifactState.REGISTERING, ArtifactState.FAILED)).isTrue();
        }

        @Test
        @DisplayName("REGISTERING -> QUARANTINED")
        void registeringToQuarantined() {
            assertThat(ArtifactStateMachine.canTransition(ArtifactState.REGISTERING, ArtifactState.QUARANTINED)).isTrue();
        }

        @Test
        @DisplayName("REGISTERING -> DELETING")
        void registeringToDeleting() {
            assertThat(ArtifactStateMachine.canTransition(ArtifactState.REGISTERING, ArtifactState.DELETING)).isTrue();
        }

        @Test
        @DisplayName("AVAILABLE -> QUARANTINED")
        void availableToQuarantined() {
            assertThat(ArtifactStateMachine.canTransition(ArtifactState.AVAILABLE, ArtifactState.QUARANTINED)).isTrue();
        }

        @Test
        @DisplayName("AVAILABLE -> DELETING")
        void availableToDeleting() {
            assertThat(ArtifactStateMachine.canTransition(ArtifactState.AVAILABLE, ArtifactState.DELETING)).isTrue();
        }

        @Test
        @DisplayName("QUARANTINED -> DELETING")
        void quarantinedToDeleting() {
            assertThat(ArtifactStateMachine.canTransition(ArtifactState.QUARANTINED, ArtifactState.DELETING)).isTrue();
        }

        @Test
        @DisplayName("QUARANTINED -> DELETED")
        void quarantinedToDeleted() {
            assertThat(ArtifactStateMachine.canTransition(ArtifactState.QUARANTINED, ArtifactState.DELETED)).isTrue();
        }

        @Test
        @DisplayName("FAILED -> DELETING")
        void failedToDeleting() {
            assertThat(ArtifactStateMachine.canTransition(ArtifactState.FAILED, ArtifactState.DELETING)).isTrue();
        }

        @Test
        @DisplayName("FAILED -> DELETED")
        void failedToDeleted() {
            assertThat(ArtifactStateMachine.canTransition(ArtifactState.FAILED, ArtifactState.DELETED)).isTrue();
        }

        @Test
        @DisplayName("DELETING -> DELETED")
        void deletingToDeleted() {
            assertThat(ArtifactStateMachine.canTransition(ArtifactState.DELETING, ArtifactState.DELETED)).isTrue();
        }
    }

    @Nested
    @DisplayName("Forbidden transitions")
    class ForbiddenTransitions {

        @Test
        @DisplayName("REGISTERING -> DELETED is forbidden")
        void registeringToDeletedForbidden() {
            assertThat(ArtifactStateMachine.canTransition(ArtifactState.REGISTERING, ArtifactState.DELETED)).isFalse();
        }

        @Test
        @DisplayName("DELETED -> AVAILABLE is forbidden (DELETED is terminal)")
        void deletedToAvailableForbidden() {
            assertThat(ArtifactStateMachine.canTransition(ArtifactState.DELETED, ArtifactState.AVAILABLE)).isFalse();
        }

        @Test
        @DisplayName("FAILED -> AVAILABLE is forbidden (requires new registration)")
        void failedToAvailableForbidden() {
            assertThat(ArtifactStateMachine.canTransition(ArtifactState.FAILED, ArtifactState.AVAILABLE)).isFalse();
        }

        @Test
        @DisplayName("AVAILABLE -> REGISTERING is forbidden")
        void availableToRegisteringForbidden() {
            assertThat(ArtifactStateMachine.canTransition(ArtifactState.AVAILABLE, ArtifactState.REGISTERING)).isFalse();
        }

        @Test
        @DisplayName("QUARANTINED -> AVAILABLE is forbidden via standard path")
        void quarantinedToAvailableForbidden() {
            assertThat(ArtifactStateMachine.canTransition(ArtifactState.QUARANTINED, ArtifactState.AVAILABLE)).isFalse();
        }

        @Test
        @DisplayName("DELETED -> any state is forbidden")
        void deletedToAnyForbidden() {
            for (ArtifactState state : ArtifactState.values()) {
                assertThat(ArtifactStateMachine.canTransition(ArtifactState.DELETED, state)).isFalse();
            }
        }
    }

    @Nested
    @DisplayName("Authorized release from quarantine")
    class AuthorizedRelease {

        @Test
        @DisplayName("QUARANTINED -> AVAILABLE via authorized path")
        void authorizedReleaseFromQuarantine() {
            assertThat(ArtifactStateMachine.canReleaseFromQuarantine(ArtifactState.QUARANTINED, ArtifactState.AVAILABLE)).isTrue();
        }

        @Test
        @DisplayName("Only QUARANTINED -> AVAILABLE is authorized")
        void onlyQuarantinedToAvailableAuthorized() {
            assertThat(ArtifactStateMachine.canReleaseFromQuarantine(ArtifactState.AVAILABLE, ArtifactState.QUARANTINED)).isFalse();
            assertThat(ArtifactStateMachine.canReleaseFromQuarantine(ArtifactState.REGISTERING, ArtifactState.AVAILABLE)).isFalse();
        }
    }

    @Test
    @DisplayName("State machine is deterministic — same input always returns same output")
    void deterministic() {
        for (int i = 0; i < 100; i++) {
            assertThat(ArtifactStateMachine.canTransition(ArtifactState.REGISTERING, ArtifactState.AVAILABLE)).isTrue();
            assertThat(ArtifactStateMachine.canTransition(ArtifactState.DELETED, ArtifactState.AVAILABLE)).isFalse();
        }
    }
}
