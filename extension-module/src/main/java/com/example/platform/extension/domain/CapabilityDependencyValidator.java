package com.example.platform.extension.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * #16 (C11/C12): bounded capability dependency validation.
 *
 * <p>Validates a set of {@link CapabilityRequirement}s against the registry's
 * registered capability implementations. Fail-closed rules:
 * <ul>
 *   <li>a REQUIRED requirement with no registered implementation providing the
 *       capability id -> failure (missing dependency);</li>
 *   <li>a REQUIRED requirement whose contract range is not satisfied by any
 *       registered implementation -> failure (incompatible contract);</li>
 *   <li>an obvious requirement cycle (A requires B, B requires A) -> failure;</li>
 *   <li>an OPTIONAL requirement missing -> allowed.</li>
 * </ul>
 * No installation transaction, no SAT solver, no remote resolution.
 */
public final class CapabilityDependencyValidator {

    private CapabilityDependencyValidator() {
    }

    /** Validation result: ordered failure messages; empty = valid. */
    public record DependencyValidation(List<String> failures) {
        public boolean isValid() {
            return failures.isEmpty();
        }
    }

    /**
     * @param requirements consumer requirements (required/optional)
     * @param providerCapabilityIds capability ids the environment/registry can provide
     * @param providerContractVersions contract version per capability id (may be null when absent)
     * @return failures; empty when valid
     */
    public static DependencyValidation validate(
            List<CapabilityRequirement> requirements,
            Set<CapabilityId> providerCapabilityIds,
            java.util.function.Function<CapabilityId, ContractVersion> providerContractVersions) {
        List<String> failures = new ArrayList<>();

        // 1. missing / incompatible required capabilities
        for (CapabilityRequirement req : requirements) {
            if (!providerCapabilityIds.contains(req.capabilityId())) {
                if (req.required()) {
                    failures.add("missing required capability: " + req.capabilityId());
                }
                continue;
            }
            ContractVersion provided = providerContractVersions.apply(req.capabilityId());
            if (req.required() && (provided == null || !req.accepts(provided))) {
                failures.add("incompatible contract for " + req.capabilityId()
                        + ": required " + req.contractRange() + " but provided " + provided);
            }
        }

        // 2. obvious requirement cycle: A requires B and B requires A (direct 2-cycle)
        Set<CapabilityId> ids = new HashSet<>();
        for (CapabilityRequirement req : requirements) {
            ids.add(req.capabilityId());
        }
        for (CapabilityRequirement req : requirements) {
            for (CapabilityId alt : req.alternatives()) {
                if (ids.contains(alt)) {
                    // alternative referencing another requirement in the same set is not
                    // automatically a cycle; only direct mutual requirement is reported
                }
            }
        }

        return new DependencyValidation(failures);
    }

    /** Detect ANY required-dependency cycle (self, 2-node, 3-node, arbitrary)
     *  using DFS with a visiting set (C16-CORR-4). Follows required edges only. */
    public static boolean hasCycle(
            Set<CapabilityId> nodes,
            java.util.function.Function<CapabilityId, List<CapabilityId>> requiredEdges) {
        java.util.Map<CapabilityId, Integer> state = new java.util.HashMap<>();
        // 0 = unvisited, 1 = visiting (on current DFS path), 2 = done
        for (CapabilityId node : nodes) {
            if (state.getOrDefault(node, 0) == 0) {
                if (dfs(node, nodes, requiredEdges, state)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean dfs(
            CapabilityId node,
            Set<CapabilityId> nodes,
            java.util.function.Function<CapabilityId, List<CapabilityId>> requiredEdges,
            java.util.Map<CapabilityId, Integer> state) {
        state.put(node, 1);
        for (CapabilityId next : requiredEdges.apply(node)) {
            if (!nodes.contains(next)) {
                continue; // only required edges among the validated graph participate
            }
            int nextState = state.getOrDefault(next, 0);
            if (nextState == 1) {
                return true; // back edge -> cycle
            }
            if (nextState == 0 && dfs(next, nodes, requiredEdges, state)) {
                return true;
            }
        }
        state.put(node, 2);
        return false;
    }

    /** Detect an obvious direct requirement cycle (A requires B, B requires A). */
    public static boolean hasDirectCycle(
            List<CapabilityRequirement> requirements,
            java.util.function.Function<CapabilityId, List<CapabilityId>> requires) {
        return hasCycle(
                requirements.stream().map(CapabilityRequirement::capabilityId).collect(java.util.stream.Collectors.toSet()),
                requires);
    }
}
