package com.example.platform.execution.planning;

import java.util.List;
import java.util.Objects;

/**
 * Roadmap #21 typed failure carrier (C19) with structured machine-readable
 * context. Human-readable message is provided but is NEVER a semantic
 * branching authority — the typed {@link ExecutionPlanningFailureReason} and
 * the sealed {@link PlanningFailureContext} are the machine contract.
 *
 * <p>FAOF-1 hook: context records expose language-neutral invariant fields.
 */
public class ExecutionPlanningException extends RuntimeException {

    private final ExecutionPlanningFailureReason reason;
    private final PlanningFailureContext context;

    public ExecutionPlanningException(ExecutionPlanningFailureReason reason, PlanningFailureContext context) {
        super(reason.name() + (context != null ? ": " + context.summary() : ""));
        this.reason = Objects.requireNonNull(reason, "reason");
        this.context = Objects.requireNonNull(context, "context — structured context required, no free-text semantic authority");
    }

    public ExecutionPlanningFailureReason reason() {
        return reason;
    }

    public PlanningFailureContext context() {
        return context;
    }

    /** Bounded sealed context vocabulary — no free-text semantic branching. */
    public sealed interface PlanningFailureContext permits
            CycleContext, MissingReferenceContext, FingerprintMismatchContext,
            ExtentViolationContext, IllegalPartitionContext,
            DuplicateIdentityContext, UnsupportedConstructContext {

        /** Compact machine-readable summary (non-authoritative, for logs). */
        String summary();

        /** Language-neutral invariant identifier (FAOF-1 hook). */
        String lawId();
    }

    /** Cycle detected in the graph. */
    public record CycleContext(List<String> cycleNodeIds, String summary) implements PlanningFailureContext {
        public CycleContext {
            cycleNodeIds = List.copyOf(cycleNodeIds);
        }

        @Override
        public String lawId() {
            return "law:dag-acyclic";
        }
    }

    /** A referenced node/requirement does not resolve. */
    public record MissingReferenceContext(String referenceKind, String referenceKey, String summary)
            implements PlanningFailureContext {
        @Override
        public String lawId() {
            return "law:inputs-closed";
        }
    }

    /** graph.planFingerprint != plan.fingerprint. */
    public record FingerprintMismatchContext(String graphFingerprint, String planFingerprint, String summary)
            implements PlanningFailureContext {
        @Override
        public String lawId() {
            return "law:structural-constraints";
        }
    }

    /** RenderExtent inconsistency (graph vs request, or pruning violation). */
    public record ExtentViolationContext(String nodeId, String window, String requestedExtent, String summary)
            implements PlanningFailureContext {
        @Override
        public String lawId() {
            return "law:extent-single-authority";
        }
    }

    /** A physical partition violates 1:1 structural constraints. */
    public record IllegalPartitionContext(String logicalNodeId, String summary) implements PlanningFailureContext {
        @Override
        public String lawId() {
            return "law:partition-1-to-1";
        }
    }

    /** Duplicate logical/plan identity. */
    public record DuplicateIdentityContext(String identityKind, String identity, String summary)
            implements PlanningFailureContext {
        @Override
        public String lawId() {
            return "law:logical-graph-valid";
        }
    }

    /** Construct outside bounded V1 surface. */
    public record UnsupportedConstructContext(String construct, String summary) implements PlanningFailureContext {
        @Override
        public String lawId() {
            return "law:v1-surface-bounded";
        }
    }
}
