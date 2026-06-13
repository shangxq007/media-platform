package com.example.platform.render.domain;

import com.example.platform.shared.web.CommonErrorCode;
import com.example.platform.shared.web.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Deterministic render job state machine.
 *
 * <p>This is the SINGLE SOURCE OF TRUTH for all job state transitions.
 * No service should mutate job status directly - all transitions must go through this state machine.
 *
 * <p>Features:
 * <ul>
 *   <li>Deterministic transitions with validation</li>
 *   <li>Full trace emission for every transition</li>
 *   <li>Transition history per job</li>
 *   <li>Observable state for UI</li>
 * </ul>
 */
public class RenderJobStateMachine {

    private static final Logger log = LoggerFactory.getLogger(RenderJobStateMachine.class);

    /**
     * Valid state transitions. Key = from state, Value = set of valid to states.
     */
    static final Map<RenderJobStatus, Set<RenderJobStatus>> VALID_TRANSITIONS = Map.ofEntries(
            Map.entry(RenderJobStatus.QUEUED, Set.of(
                    RenderJobStatus.SELECTING_PROVIDER,
                    RenderJobStatus.CANCELLED,
                    RenderJobStatus.REJECTED
            )),
            Map.entry(RenderJobStatus.SELECTING_PROVIDER, Set.of(
                    RenderJobStatus.PROVIDER_SELECTED,
                    RenderJobStatus.FAILED,
                    RenderJobStatus.CANCELLED
            )),
            Map.entry(RenderJobStatus.PROVIDER_SELECTED, Set.of(
                    RenderJobStatus.EXECUTING,
                    RenderJobStatus.FAILED,
                    RenderJobStatus.CANCELLED
            )),
            Map.entry(RenderJobStatus.EXECUTING, Set.of(
                    RenderJobStatus.COMPLETING,
                    RenderJobStatus.FAILED,
                    RenderJobStatus.FALLBACKING,
                    RenderJobStatus.RETRYING,
                    RenderJobStatus.CANCELLED
            )),
            Map.entry(RenderJobStatus.FALLBACKING, Set.of(
                    RenderJobStatus.EXECUTING,
                    RenderJobStatus.FAILED,
                    RenderJobStatus.CANCELLED
            )),
            Map.entry(RenderJobStatus.RETRYING, Set.of(
                    RenderJobStatus.EXECUTING,
                    RenderJobStatus.FAILED,
                    RenderJobStatus.CANCELLED
            )),
            Map.entry(RenderJobStatus.COMPLETING, Set.of(
                    RenderJobStatus.COMPLETED,
                    RenderJobStatus.FAILED
            )),
            Map.entry(RenderJobStatus.COMPLETED, Collections.emptySet()),
            Map.entry(RenderJobStatus.FAILED, Set.of(RenderJobStatus.QUEUED)),
            Map.entry(RenderJobStatus.CANCELLED, Collections.emptySet()),
            Map.entry(RenderJobStatus.REJECTED, Collections.emptySet())
    );

    /**
     * Trace listener for state transitions.
     */
    private Consumer<StateTransitionTraceNode> traceListener;

    /**
     * Transition history per job (for debugging/replay).
     */
    private final Map<String, List<StateTransitionTraceNode>> transitionHistory = new ConcurrentHashMap<>();

    /**
     * Current state per job.
     */
    private final Map<String, RenderJobStatus> currentStates = new ConcurrentHashMap<>();

    public RenderJobStateMachine() {
        this(null);
    }

    public RenderJobStateMachine(Consumer<StateTransitionTraceNode> traceListener) {
        this.traceListener = traceListener;
    }

    /**
     * Set the trace listener for state transitions.
     */
    public void setTraceListener(Consumer<StateTransitionTraceNode> traceListener) {
        this.traceListener = traceListener;
    }

    /**
     * Check if a transition is valid.
     */
    public boolean canTransition(RenderJobStatus from, RenderJobStatus to) {
        if (from == to) {
            return true;
        }
        Set<RenderJobStatus> allowed = VALID_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * Validate and execute a state transition.
     *
     * @param jobId The job ID
     * @param from Current state
     * @param to Target state
     * @param reason Reason for transition
     * @param triggeredBy Who triggered the transition (e.g., "ProviderRuntimeEngine")
     * @return The new state (same as to)
     * @throws PlatformException if transition is invalid
     */
    public RenderJobStatus transition(String jobId, RenderJobStatus from, RenderJobStatus to,
                                       String reason, String triggeredBy) {
        return transition(jobId, from, to, reason, triggeredBy, null);
    }

    /**
     * Validate and execute a state transition with additional metadata.
     */
    public RenderJobStatus transition(String jobId, RenderJobStatus from, RenderJobStatus to,
                                       String reason, String triggeredBy,
                                       Map<String, Object> metadata) {
        // Validate transition
        if (!canTransition(from, to)) {
            throw new PlatformException(
                    CommonErrorCode.CONFLICT,
                    String.format("Invalid state transition from %s to %s for job %s", from, to, jobId)
            );
        }

        // Create trace node
        StateTransitionTraceNode traceNode = metadata != null
                ? StateTransitionTraceNode.createWithMetadata(
                        getTraceId(jobId), jobId, from, to, reason, triggeredBy, metadata)
                : StateTransitionTraceNode.create(
                        getTraceId(jobId), jobId, from, to, reason, triggeredBy);

        // Record transition
        recordTransition(jobId, traceNode);

        // Update current state
        currentStates.put(jobId, to);

        // Emit trace
        emitTrace(traceNode);

        log.info("Job {} transitioned: {} -> {} ({}) by {}",
                jobId, from, to, reason, triggeredBy);

        return to;
    }

    /**
     * Get the current state of a job.
     */
    public RenderJobStatus getCurrentState(String jobId) {
        return currentStates.getOrDefault(jobId, RenderJobStatus.QUEUED);
    }

    /**
     * Set the current state of a job (for initialization/replay).
     */
    public void setCurrentState(String jobId, RenderJobStatus state) {
        currentStates.put(jobId, state);
    }

    /**
     * Get the transition history for a job.
     */
    public List<StateTransitionTraceNode> getTransitionHistory(String jobId) {
        return transitionHistory.getOrDefault(jobId, Collections.emptyList());
    }

    /**
     * Get all transition histories (for debugging).
     */
    public Map<String, List<StateTransitionTraceNode>> getAllTransitionHistories() {
        return Map.copyOf(transitionHistory);
    }

    /**
     * Clear history for a job (for cleanup).
     */
    public void clearHistory(String jobId) {
        transitionHistory.remove(jobId);
        currentStates.remove(jobId);
    }

    /**
     * Clear all histories (for testing).
     */
    public void clearAll() {
        transitionHistory.clear();
        currentStates.clear();
    }

    /**
     * Get a summary of the job's state history.
     */
    public String getStateSummary(String jobId) {
        List<StateTransitionTraceNode> history = getTransitionHistory(jobId);
        if (history.isEmpty()) {
            return "No transitions recorded";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Job ").append(jobId).append(" state history:\n");
        for (StateTransitionTraceNode node : history) {
            sb.append("  ").append(node.getDescription()).append("\n");
        }
        return sb.toString();
    }

    // --- Private helpers ---

    private void recordTransition(String jobId, StateTransitionTraceNode traceNode) {
        transitionHistory.computeIfAbsent(jobId, k -> new ArrayList<>()).add(traceNode);
    }

    private void emitTrace(StateTransitionTraceNode traceNode) {
        if (traceListener != null) {
            try {
                traceListener.accept(traceNode);
            } catch (Exception e) {
                log.warn("Failed to emit state transition trace: {}", e.getMessage());
            }
        }
    }

    private String getTraceId(String jobId) {
        // In production, this would get the trace ID from the job or context
        return "trace-" + jobId;
    }

    // --- Legacy compatibility ---

    /**
     * Legacy validateTransition method for backward compatibility.
     */
    public void validateTransition(RenderJobStatus from, RenderJobStatus to) {
        if (!canTransition(from, to)) {
            throw new PlatformException(
                    CommonErrorCode.CONFLICT,
                    String.format("Invalid state transition from %s to %s", from, to)
            );
        }
    }
}
