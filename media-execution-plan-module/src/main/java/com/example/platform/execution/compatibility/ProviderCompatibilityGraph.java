package com.example.platform.execution.compatibility;

import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCanonicalCodec;
import com.example.platform.execution.planning.CanonicalWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable deterministic Stage-1 relation from PhysicalPlanUnit identity to statically feasible
 * ProviderBindingPins. It contains no worker, host, assignment, probe, reservation, or telemetry.
 */
public record ProviderCompatibilityGraph(
        int schemaVersion,
        List<UnitCandidates> unitCandidates) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ProviderCompatibilityGraph {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported provider compatibility graph schema version");
        }
        Objects.requireNonNull(unitCandidates, "unitCandidates");
        var canonical = new ArrayList<UnitCandidates>(unitCandidates.size());
        for (UnitCandidates candidates : unitCandidates) {
            canonical.add(Objects.requireNonNull(candidates, "unitCandidates element"));
        }
        canonical.sort(Comparator.comparing(value -> value.physicalPlanUnitId().value()));
        rejectAdjacentDuplicateUnitIds(canonical);
        unitCandidates = List.copyOf(canonical);
    }

    /** Candidate discovery order and physical-unit input order are explicitly nonsemantic. */
    public static ProviderCompatibilityGraph build(
            List<CompatibilityRequest> requests,
            List<ProviderCandidate> discoveredCandidates) {
        List<CompatibilityRequest> canonicalRequests = canonicalRequests(requests);
        List<ProviderCandidate> canonicalCandidates = canonicalCandidates(discoveredCandidates);

        List<UnitCandidates> units = new ArrayList<>(canonicalRequests.size());
        for (CompatibilityRequest request : canonicalRequests) {
            List<ProviderBindingPin> feasible = new ArrayList<>();
            for (ProviderCandidate candidate : canonicalCandidates) {
                CompatibilityDecision decision = CompatibilityKernel.evaluate(request, candidate);
                if (decision.compatible()) {
                    feasible.add(candidate.bindingPin());
                }
            }
            units.add(new UnitCandidates(request.physicalPlanUnit().stepId(), feasible));
        }
        return new ProviderCompatibilityGraph(CURRENT_SCHEMA_VERSION, units);
    }

    public byte[] canonicalSerialization() {
        List<String> units = unitCandidates.stream()
                .map(ProviderCompatibilityGraph::canonicalUnitCandidates)
                .toList();
        String canonical = new CanonicalWriter()
                .tag("roadmap22.provider-compatibility-graph.v1")
                .field("schemaVersion", Integer.toString(schemaVersion))
                .field("unitCandidates", new CanonicalWriter().list(units).build())
                .build();
        return canonical.getBytes(StandardCharsets.UTF_8);
    }

    public ProviderCompatibilityGraphDigest digest() {
        return ProviderCompatibilityGraphDigest.fromCanonicalBytes(canonicalSerialization());
    }

    public record UnitCandidates(
            ExecutionStepId physicalPlanUnitId,
            List<ProviderBindingPin> feasibleProviderBindings) {

        public UnitCandidates {
            Objects.requireNonNull(physicalPlanUnitId, "physicalPlanUnitId");
            Objects.requireNonNull(feasibleProviderBindings, "feasibleProviderBindings");
            var canonical = new ArrayList<ProviderBindingPin>(feasibleProviderBindings.size());
            for (ProviderBindingPin binding : feasibleProviderBindings) {
                canonical.add(Objects.requireNonNull(binding, "feasibleProviderBindings element"));
            }
            canonical.sort(ProviderCompatibilityGraph::compareBindings);
            for (int i = 1; i < canonical.size(); i++) {
                if (canonical.get(i - 1).equals(canonical.get(i))) {
                    throw new IllegalArgumentException("duplicate feasible provider binding");
                }
            }
            feasibleProviderBindings = List.copyOf(canonical);
        }
    }

    private static List<CompatibilityRequest> canonicalRequests(List<CompatibilityRequest> requests) {
        Objects.requireNonNull(requests, "requests");
        var canonical = new ArrayList<CompatibilityRequest>(requests.size());
        for (CompatibilityRequest request : requests) {
            canonical.add(Objects.requireNonNull(request, "requests element"));
        }
        canonical.sort(Comparator.comparing(value -> value.physicalPlanUnit().stepId().value()));
        for (int i = 1; i < canonical.size(); i++) {
            if (canonical.get(i - 1).physicalPlanUnit().stepId()
                    .equals(canonical.get(i).physicalPlanUnit().stepId())) {
                throw new IllegalArgumentException("duplicate PhysicalPlanUnit compatibility request");
            }
        }
        return List.copyOf(canonical);
    }

    private static List<ProviderCandidate> canonicalCandidates(List<ProviderCandidate> candidates) {
        Objects.requireNonNull(candidates, "discoveredCandidates");
        var canonical = new ArrayList<ProviderCandidate>(candidates.size());
        for (ProviderCandidate candidate : candidates) {
            canonical.add(Objects.requireNonNull(candidate, "discoveredCandidates element"));
        }
        canonical.sort((first, second) -> compareBindings(first.bindingPin(), second.bindingPin()));
        for (int i = 1; i < canonical.size(); i++) {
            if (canonical.get(i - 1).bindingPin().equals(canonical.get(i).bindingPin())) {
                throw new IllegalArgumentException("duplicate ProviderBindingPin candidate");
            }
        }
        return List.copyOf(canonical);
    }

    private static int compareBindings(ProviderBindingPin first, ProviderBindingPin second) {
        return Arrays.compareUnsigned(
                ProviderCanonicalCodec.serialize(first), ProviderCanonicalCodec.serialize(second));
    }

    private static String canonicalUnitCandidates(UnitCandidates candidates) {
        List<String> bindings = candidates.feasibleProviderBindings().stream()
                .map(binding -> new String(
                        ProviderCanonicalCodec.serialize(binding), StandardCharsets.UTF_8))
                .toList();
        return new CanonicalWriter()
                .tag("ProviderCompatibilityGraph.UnitCandidates")
                .field("physicalPlanUnitId", candidates.physicalPlanUnitId().value())
                .field("feasibleProviderBindings", new CanonicalWriter().list(bindings).build())
                .build();
    }

    private static void rejectAdjacentDuplicateUnitIds(List<UnitCandidates> sorted) {
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i - 1).physicalPlanUnitId().equals(sorted.get(i).physicalPlanUnitId())) {
                throw new IllegalArgumentException("duplicate graph PhysicalPlanUnit identity");
            }
        }
    }
}
