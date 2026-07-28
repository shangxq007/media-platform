package com.example.platform.artifact.domain;

import java.io.Serializable;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Pure, deterministic Artifact lifecycle state machine.
 *
 * <p>Contains no database, clock, filesystem, or network access. Given the same
 * (from, to) input it always returns the same answer, independent of environment.
 *
 * <p>Explicitly forbidden transitions (invariant enforcement):
 * <ul>
 *   <li>REGISTERING → DELETED (must fail/cleanup through governed path)</li>
 *   <li>DELETED → AVAILABLE (DELETED is terminal)</li>
 *   <li>FAILED → AVAILABLE (requires a new registration)</li>
 *   <li>AVAILABLE → REGISTERING (cannot re-register an available artifact)</li>
 *   <li>QUARANTINED → AVAILABLE (requires explicit authorization)</li>
 * </ul>
 */
public final class ArtifactStateMachine implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Map<ArtifactState, Set<ArtifactState>> TRANSITIONS = new EnumMap<>(ArtifactState.class);

    static {
        // An artifact is born REGISTERING; it becomes AVAILABLE once a verified replica exists.
        TRANSITIONS.put(ArtifactState.REGISTERING, EnumSet.of(
                ArtifactState.AVAILABLE,
                ArtifactState.FAILED,
                ArtifactState.QUARANTINED,
                ArtifactState.DELETING));
        // AVAILABLE artifacts may be quarantined, or begin deletion.
        TRANSITIONS.put(ArtifactState.AVAILABLE, EnumSet.of(
                ArtifactState.QUARANTINED,
                ArtifactState.DELETING));
        // QUARANTINED artifacts may be released back to AVAILABLE only via an explicit
        // authorized path (modeled as a separate canTransitionAuthorized check), or deleted.
        TRANSITIONS.put(ArtifactState.QUARANTINED, EnumSet.of(
                ArtifactState.DELETING,
                ArtifactState.DELETED));
        // FAILED artifacts may begin deletion; recovery requires a NEW registration (new Artifact).
        TRANSITIONS.put(ArtifactState.FAILED, EnumSet.of(
                ArtifactState.DELETING,
                ArtifactState.DELETED));
        // DELETING artifacts reach the terminal DELETED state.
        TRANSITIONS.put(ArtifactState.DELETING, EnumSet.of(
                ArtifactState.DELETED));
        // DELETED is terminal.
        TRANSITIONS.put(ArtifactState.DELETED, EnumSet.noneOf(ArtifactState.class));
    }

    private ArtifactStateMachine() {
    }

    /**
     * Returns whether the transition from {@code from} to {@code to} is permitted by the
     * standard (unauthorized) state machine.
     */
    public static boolean canTransition(ArtifactState from, ArtifactState to) {
        Set<ArtifactState> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * Authorized release from QUARANTINED back to AVAILABLE. This is the ONLY permitted
     * path from QUARANTINED to AVAILABLE and represents an explicit authorization action.
     */
    public static boolean canReleaseFromQuarantine(ArtifactState from, ArtifactState to) {
        return from == ArtifactState.QUARANTINED && to == ArtifactState.AVAILABLE;
    }

    public static String describeIllegalTransition(ArtifactState from, ArtifactState to) {
        return "Illegal artifact state transition: " + from + " -> " + to;
    }
}
