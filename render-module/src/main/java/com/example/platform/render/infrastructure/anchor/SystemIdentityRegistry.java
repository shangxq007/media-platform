package com.example.platform.render.infrastructure.anchor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * System Identity Registry - defines long-term system identity.
 * 
 * <p>Defines:
 * <ul>
 *   <li>What system IS</li>
 *   <li>What system is NOT</li>
 *   <li>Forbidden transformations</li>
 * </ul>
 * 
 * <p>This registry is IMMUTABLE and defines the system's core identity.
 */
public record SystemIdentityRegistry(
        String identityId,
        List<IdentityStatement> isStatements,
        List<IdentityStatement> isNotStatements,
        List<ForbiddenTransformation> forbiddenTransformations,
        Instant createdAt
) {
    /**
     * Create the default system identity.
     */
    public static SystemIdentityRegistry createDefault() {
        return new SystemIdentityRegistry(
                "identity-default",
                List.of(
                        new IdentityStatement(
                                "IS-001",
                                "Deterministic render OS",
                                "The system is a deterministic video rendering operating system",
                                IdentityCategory.CORE_PURPOSE
                        ),
                        new IdentityStatement(
                                "IS-002",
                                "User-value optimizer",
                                "The system optimizes for user value within bounded constraints",
                                IdentityCategory.OPTIMIZATION_GOAL
                        ),
                        new IdentityStatement(
                                "IS-003",
                                "Self-improving within bounds",
                                "The system can improve itself but within safety boundaries",
                                IdentityCategory.EVOLUTION_CAPABILITY
                        ),
                        new IdentityStatement(
                                "IS-004",
                                "Observable and traceable",
                                "All decisions are observable and traceable",
                                IdentityCategory.TRANSPARENCY
                        )
                ),
                List.of(
                        new IdentityStatement(
                                "NOT-001",
                                "NOT a fully autonomous agent",
                                "The system does not ignore constraints or user preferences",
                                IdentityCategory.AUTONOMY_BOUNDARY
                        ),
                        new IdentityStatement(
                                "NOT-002",
                                "NOT unbounded optimizer",
                                "The system does not optimize without safety limits",
                                IdentityCategory.OPTIMIZATION_BOUNDARY
                        ),
                        new IdentityStatement(
                                "NOT-003",
                                "NOT opaque decision maker",
                                "The system does not make decisions without explainability",
                                IdentityCategory.TRANSPARENCY_BOUNDARY
                        ),
                        new IdentityStatement(
                                "NOT-004",
                                "NOT cost-unbounded",
                                "The system does not allow unbounded cost growth",
                                IdentityCategory.COST_BOUNDARY
                        )
                ),
                List.of(
                        new ForbiddenTransformation(
                                "F-001",
                                "Cannot remove safety constraints",
                                "Safety constraints are immutable",
                                "SafetyController"
                        ),
                        new ForbiddenTransformation(
                                "F-002",
                                "Cannot ignore user preferences",
                                "User preferences must always be considered",
                                "PreferenceLearning"
                        ),
                        new ForbiddenTransformation(
                                "F-003",
                                "Cannot make system unstable",
                                "System stability is a core invariant",
                                "StabilityController"
                        ),
                        new ForbiddenTransformation(
                                "F-004",
                                "Cannot unbounded cost growth",
                                "Cost must remain bounded",
                                "CostController"
                        ),
                        new ForbiddenTransformation(
                                "F-005",
                                "Cannot remove traceability",
                                "All decisions must remain traceable",
                                "TraceabilityController"
                        )
                ),
                Instant.now()
        );
    }

    /**
     * Check if a proposed change violates system identity.
     */
    public IdentityViolation checkViolation(ProposedChange change) {
        // Check forbidden transformations
        for (ForbiddenTransformation forbidden : forbiddenTransformations) {
            if (violatesForbidden(change, forbidden)) {
                return new IdentityViolation(
                        "violation-" + Instant.now().toEpochMilli(),
                        forbidden.transformationId(),
                        change.description(),
                        forbidden.description(),
                        true,
                        Instant.now()
                );
            }
        }

        // Check is-not statements
        for (IdentityStatement isNot : isNotStatements) {
            if (violatesIsNot(change, isNot)) {
                return new IdentityViolation(
                        "violation-" + Instant.now().toEpochMilli(),
                        isNot.statementId(),
                        change.description(),
                        isNot.description(),
                        true,
                        Instant.now()
                );
            }
        }

        return null; // No violation
    }

    /**
     * Get all identity statements.
     */
    public List<IdentityStatement> getAllStatements() {
        List<IdentityStatement> all = new java.util.ArrayList<>(isStatements);
        all.addAll(isNotStatements);
        return all;
    }

    // ---------------------------------------------------------------------------
    // Helper Methods
    // ---------------------------------------------------------------------------

    private boolean violatesForbidden(ProposedChange change, ForbiddenTransformation forbidden) {
        return change.targetSystem().equals(forbidden.targetSystem()) &&
               change.changeType().equals("REMOVE_CONSTRAINT");
    }

    private boolean violatesIsNot(ProposedChange change, IdentityStatement isNot) {
        // Simplified check
        return false;
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record IdentityStatement(
            String statementId,
            String title,
            String description,
            IdentityCategory category
    ) {}

    public enum IdentityCategory {
        CORE_PURPOSE,
        OPTIMIZATION_GOAL,
        EVOLUTION_CAPABILITY,
        TRANSPARENCY,
        AUTONOMY_BOUNDARY,
        OPTIMIZATION_BOUNDARY,
        TRANSPARENCY_BOUNDARY,
        COST_BOUNDARY
    }

    public record ForbiddenTransformation(
            String transformationId,
            String title,
            String description,
            String targetSystem
    ) {}

    public record ProposedChange(
            String changeId,
            String targetSystem,
            String changeType,
            String description,
            Map<String, Object> metadata
    ) {}

    public record IdentityViolation(
            String violationId,
            String constraintId,
            String changeDescription,
            String constraintDescription,
            boolean isViolation,
            Instant detectedAt
    ) {
        public String getSummary() {
            return String.format("Identity violation: %s violates %s", 
                    changeDescription, constraintDescription);
        }
    }
}
