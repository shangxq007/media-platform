package com.example.platform.artifact.domain;

import com.example.platform.shared.identity.ArtifactId;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates provenance graph constraints with O(V+E) complexity.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Parent != child (no self-reference)</li>
 *   <li>Parent and child belong to same tenant</li>
 *   <li>No duplicate edgeId</li>
 *   <li>No duplicate semantic edge (same parent, child, relationType, operationId, attemptId)</li>
 *   <li>No derivation cycle (DAG invariant)</li>
 * </ul>
 *
 * <p>All methods are pure, deterministic, and thread-safe (no shared mutable state).
 */
public final class ProvenanceValidator implements Serializable {

    private ProvenanceValidator() {
    }

    /**
     * Validates request-local facts before any endpoint lookup or persistence.
     *
     * <p>The persisted relation identity is {@code child-parent}; consequently a
     * repeated parent is a duplicate canonical edge even if other declaration
     * metadata differs. The semantic identity check is retained independently so
     * the domain rule remains explicit if persistence mechanics evolve.
     */
    public static ValidationResult validateDeclarations(
            ArtifactId childArtifactId,
            Collection<ArtifactCommitRequest.ProvenanceEdgeDeclaration> declarations) {
        List<String> violations = new ArrayList<>();
        ArtifactErrorCode.Code firstCode = null;
        DeclarationErrorContext firstContext = null;
        Set<String> canonicalEdgeIds = new HashSet<>();
        Set<DeclarationSemanticIdentity> semanticIdentities = new HashSet<>();

        int declarationIndex = 0;
        for (ArtifactCommitRequest.ProvenanceEdgeDeclaration declaration : declarations) {
            int violationCountBeforeDeclaration = violations.size();
            if (declaration.parentArtifactId().equals(childArtifactId)) {
                firstCode = addViolation(firstCode, violations,
                        ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_SELF_REFERENCE,
                        "parent == child == " + childArtifactId.value());
            }

            if (isBlank(declaration.operationId())
                    || declaration.operationVersion() < 1
                    || isBlank(declaration.attemptId())
                    || isBlank(declaration.requestDigest())
                    || isBlank(declaration.resultDigest())) {
                firstCode = addViolation(firstCode, violations,
                        ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_OPERATION_INVALID,
                        "operationId, operationVersion, attemptId, requestDigest and resultDigest must be present and valid");
            }

            String canonicalEdgeId = canonicalEdgeId(childArtifactId, declaration.parentArtifactId());
            if (!canonicalEdgeIds.add(canonicalEdgeId)) {
                firstCode = addViolation(firstCode, violations,
                        ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_DUPLICATE,
                        "canonical edge identity repeated: " + canonicalEdgeId);
            }

            DeclarationSemanticIdentity semanticIdentity = new DeclarationSemanticIdentity(
                    declaration.parentArtifactId(), declaration.relationType(), declaration.operationId(),
                    declaration.operationVersion(), declaration.attemptId());
            if (!semanticIdentities.add(semanticIdentity)) {
                firstCode = addViolation(firstCode, violations,
                        ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_DUPLICATE,
                        "semantic edge declaration repeated");
            }

            if (firstContext == null && violations.size() > violationCountBeforeDeclaration) {
                firstContext = new DeclarationErrorContext(declarationIndex, declaration);
            }
            declarationIndex++;
        }

        return result(firstCode, violations, firstContext);
    }

    /** Canonical identity shared by the domain edge and V1 relation row. */
    public static String canonicalEdgeId(ArtifactId childArtifactId, ArtifactId parentArtifactId) {
        return childArtifactId.value() + "-" + parentArtifactId.value();
    }

    /**
     * Validates a single edge against the existing graph.
     *
     * @param edge       the edge to validate
     * @param existing   all existing edges in the graph (for cycle/duplicate detection)
     * @param tenantIds  map of artifactId -> tenantId for ownership verification
     * @return validation result
     */
    public static ValidationResult validateEdge(ProvenanceEdge edge, Collection<ProvenanceEdge> existing,
                                                 Map<String, String> tenantIds) {
        List<String> violations = new ArrayList<>();
        ArtifactErrorCode.Code firstCode = null;

        // Self-reference check
        if (edge.parentArtifactId().value().equals(edge.childArtifactId().value())) {
            firstCode = addViolation(firstCode, violations,
                    ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_SELF_REFERENCE,
                    "parent == child == " + edge.parentArtifactId().value());
        }

        // Tenant ownership check
        String parentTenant = tenantIds.get(edge.parentArtifactId().value());
        String childTenant = tenantIds.get(edge.childArtifactId().value());
        if (parentTenant == null) {
            firstCode = addViolation(firstCode, violations,
                    ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_ENDPOINT_NOT_FOUND,
                    "parent " + edge.parentArtifactId().value());
        }
        if (childTenant == null) {
            firstCode = addViolation(firstCode, violations,
                    ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_ENDPOINT_NOT_FOUND,
                    "child " + edge.childArtifactId().value());
        }
        if (parentTenant != null && childTenant != null && !parentTenant.equals(childTenant)) {
            firstCode = addViolation(firstCode, violations,
                    ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_CROSS_TENANT,
                    "parent tenant=" + parentTenant + " child tenant=" + childTenant);
        }
        if (parentTenant != null && !parentTenant.equals(edge.tenantId())) {
            firstCode = addViolation(firstCode, violations,
                    ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_CROSS_TENANT,
                    "edge tenant=" + edge.tenantId() + " != parent tenant=" + parentTenant);
        }

        // Duplicate edgeId check
        for (ProvenanceEdge e : existing) {
            if (e.edgeId().equals(edge.edgeId())) {
                firstCode = addViolation(firstCode, violations,
                        ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_DUPLICATE,
                        "edgeId=" + edge.edgeId());
                break;
            }
        }

        // Duplicate semantic edge check
        for (ProvenanceEdge e : existing) {
            if (e.parentArtifactId().value().equals(edge.parentArtifactId().value()) &&
                e.childArtifactId().value().equals(edge.childArtifactId().value()) &&
                e.relationType() == edge.relationType() &&
                e.operationId().equals(edge.operationId()) &&
                e.attemptId().equals(edge.attemptId())) {
                firstCode = addViolation(firstCode, violations,
                        ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_DUPLICATE,
                        "semantic edge already exists");
                break;
            }
        }

        // Cycle detection: O(V+E) using BFS/DFS from child following parent links
        // If we can reach the parent from the child via existing edges, adding this edge creates a cycle
        if (violations.isEmpty() && wouldCreateCycle(edge, existing)) {
            firstCode = addViolation(firstCode, violations,
                    ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_CYCLE,
                    "adding edge " + edge.edgeId() + " would create a cycle");
        }

        return result(firstCode, violations);
    }

    private static ArtifactErrorCode.Code addViolation(
            ArtifactErrorCode.Code firstCode,
            List<String> violations,
            ArtifactErrorCode.Code code,
            String detail) {
        violations.add(code.name() + ": " + detail);
        return firstCode == null ? code : firstCode;
    }

    private static ValidationResult result(ArtifactErrorCode.Code firstCode, List<String> violations) {
        return result(firstCode, violations, null);
    }

    private static ValidationResult result(
            ArtifactErrorCode.Code firstCode,
            List<String> violations,
            DeclarationErrorContext declarationErrorContext) {
        return new ValidationResult(
                violations.isEmpty(), firstCode, Collections.unmodifiableList(violations),
                declarationErrorContext);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Detects whether adding the new edge would create a cycle.
     * Uses BFS from child following parent links — O(V+E).
     */
    private static boolean wouldCreateCycle(ProvenanceEdge newEdge, Collection<ProvenanceEdge> existing) {
        // Build adjacency: child -> set of parents
        Map<String, Set<String>> childToParents = new HashMap<>();
        for (ProvenanceEdge e : existing) {
            childToParents.computeIfAbsent(e.childArtifactId().value(), k -> new HashSet<>())
                    .add(e.parentArtifactId().value());
        }
        // Add the new edge
        childToParents.computeIfAbsent(newEdge.childArtifactId().value(), k -> new HashSet<>())
                .add(newEdge.parentArtifactId().value());

        // BFS from the new edge's child, following parent links
        // If we can reach the new edge's child again, there's a cycle
        String start = newEdge.childArtifactId().value();
        String target = newEdge.childArtifactId().value();
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            Set<String> parents = childToParents.getOrDefault(current, Set.of());
            for (String parent : parents) {
                if (parent.equals(target) && !current.equals(start)) {
                    return true;
                }
                if (!visited.contains(parent)) {
                    visited.add(parent);
                    queue.add(parent);
                }
            }
        }
        return false;
    }

    /**
     * Validates an entire graph for cycles. O(V+E).
     *
     * @param edges all edges in the graph
     * @return true if the graph is acyclic (DAG)
     */
    public static boolean isAcyclic(Collection<ProvenanceEdge> edges) {
        Map<String, Set<String>> childToParents = new HashMap<>();
        Set<String> allNodes = new HashSet<>();
        for (ProvenanceEdge e : edges) {
            childToParents.computeIfAbsent(e.childArtifactId().value(), k -> new HashSet<>())
                    .add(e.parentArtifactId().value());
            allNodes.add(e.parentArtifactId().value());
            allNodes.add(e.childArtifactId().value());
        }

        // Kahn's algorithm: topological sort
        Map<String, Integer> inDegree = new HashMap<>();
        // Build parent -> children for topological sort
        Map<String, Set<String>> parentToChildren = new HashMap<>();
        for (ProvenanceEdge e : edges) {
            inDegree.merge(e.childArtifactId().value(), 1, Integer::sum);
            inDegree.putIfAbsent(e.parentArtifactId().value(), 0);
            parentToChildren.computeIfAbsent(e.parentArtifactId().value(), k -> new HashSet<>())
                    .add(e.childArtifactId().value());
        }

        ArrayDeque<String> queue = new ArrayDeque<>();
        for (String node : allNodes) {
            if (inDegree.getOrDefault(node, 0) == 0) {
                queue.add(node);
            }
        }

        int processed = 0;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            processed++;
            for (String child : parentToChildren.getOrDefault(current, Set.of())) {
                int newDegree = inDegree.get(child) - 1;
                inDegree.put(child, newDegree);
                if (newDegree == 0) {
                    queue.add(child);
                }
            }
        }

        // If we processed all nodes, no cycle exists
        return processed == allNodes.size();
    }

    /**
     * Result of a validation operation.
     */
    public record DeclarationErrorContext(
            int declarationIndex,
            ArtifactCommitRequest.ProvenanceEdgeDeclaration declaration) implements Serializable {
        public DeclarationErrorContext {
            if (declarationIndex < 0) {
                throw new IllegalArgumentException("declaration index must not be negative");
            }
            if (declaration == null) {
                throw new IllegalArgumentException("declaration must be present");
            }
        }
    }

    public record ValidationResult(
            boolean valid,
            ArtifactErrorCode.Code errorCode,
            List<String> violations,
            DeclarationErrorContext declarationErrorContext) implements Serializable {
        public ValidationResult {
            violations = violations != null ? List.copyOf(violations) : List.of();
            if (valid && errorCode != null) {
                throw new IllegalArgumentException("valid result must not carry an error code");
            }
            if (valid && declarationErrorContext != null) {
                throw new IllegalArgumentException("valid result must not carry declaration error context");
            }
            if (!valid && errorCode == null) {
                throw new IllegalArgumentException("invalid result must carry an error code");
            }
        }
    }

    private record DeclarationSemanticIdentity(
            ArtifactId parentArtifactId,
            ProvenanceRelationType relationType,
            String operationId,
            int operationVersion,
            String attemptId) {
    }
}
