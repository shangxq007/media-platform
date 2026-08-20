package com.example.platform.render.domain.renderplan;

import com.example.platform.render.domain.renderplan.graph.RenderGraphBuilder;
import com.example.platform.render.domain.renderplan.graph.RenderGraphBuildResult;
import com.example.platform.render.domain.renderplan.graph.RenderGraphValidationResult;
import com.example.platform.render.domain.renderplan.graph.RenderGraphValidator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Default planner (C16/C20): materialize -> graph build -> validate -> fingerprint
 * -> status, in one deterministic pass. Same inputs -> same plan + graph
 * (fingerprint equality).
 */
public final class DefaultRenderPlanner implements RenderPlanner {

    private final RenderMaterializer materializer;

    public DefaultRenderPlanner() {
        this(new DefaultRenderMaterializer());
    }

    public DefaultRenderPlanner(RenderMaterializer materializer) {
        this.materializer = materializer;
    }

    @Override
    public RenderPlanningResult plan(RenderPlanningInput input) {
        List<RenderPlanningDiagnostic> diagnostics = new ArrayList<>();

        // 1. validate RenderRequest extent (start < end, positive frameRate)
        RenderExtent extent = input.request().extent();
        if (!extent.start().isLessThan(extent.end())) {
            diagnostics.add(RenderPlanningDiagnostic.diagnostic(
                    RenderPlanningDiagnosticCode.INVALID_RENDER_EXTENT,
                    RenderDiagnosticSeverity.ERROR,
                    "RenderExtent requires start < end"));
            return failureResult(input, diagnostics);
        }
        if (extent.frameRate().numerator().signum() <= 0 || extent.frameRate().denominator() <= 0) {
            diagnostics.add(RenderPlanningDiagnostic.diagnostic(
                    RenderPlanningDiagnosticCode.INVALID_RENDER_EXTENT,
                    RenderDiagnosticSeverity.ERROR,
                    "RenderExtent requires positive frameRate"));
            return failureResult(input, diagnostics);
        }

        // 2. materialize -> nodes + edges + diagnostics
        RenderMaterializationResult materialization = materializer.materialize(input);
        diagnostics.addAll(materialization.diagnostics());

        // 3. apply resolution state -> per-node source state (planning-result state only)
        List<RenderSourceResolutionState> sourceStates = new ArrayList<>();
        for (RenderNode node : materialization.nodes()) {
            for (RenderArtifactReference ref : node.artifactReferences()) {
                if (ref instanceof RenderArtifactReference.SourceArtifact source) {
                    // absent artifact == content missing -> FAILED (SOURCE_UNRESOLVED diagnostic) per C4
                    RenderSourceResolutionState state = input.resolution().states()
                            .getOrDefault(source.artifactId(), RenderSourceResolutionState.FAILED);
                    sourceStates.add(state);
                    if (state != RenderSourceResolutionState.RESOLVED) {
                        diagnostics.add(RenderPlanningDiagnostic.forNode(
                                sourceDiagnosticCode(state), node.id(),
                                RenderDiagnosticSeverity.ERROR, "source state: " + state));
                    }
                }
            }
        }

        // capability check -> CAPABILITY_UNAVAILABLE diagnostics
        for (RenderNode node : materialization.nodes()) {
            for (com.example.platform.extension.domain.CapabilityRequirement cap
                    : node.capabilityRequirements()) {
                if (!input.capabilities().supports(cap.capabilityId())) {
                    diagnostics.add(RenderPlanningDiagnostic.forNode(
                            RenderPlanningDiagnosticCode.CAPABILITY_UNAVAILABLE,
                            node.id(), RenderDiagnosticSeverity.ERROR,
                            "capability unavailable: " + cap.capabilityId()));
                }
            }
        }

        // identity (fingerprint computed from plan semantics, independent of graph)
        RenderPlanId id = RenderPlanId.of(input.revision().revisionId(), input.request().id().value());

        // R4-A2/A4: the authored Effect semantic reference is retained by the
        // final plan, participates in the fingerprint (R4-A3), and is explained
        // in provenance.
        EffectSemanticReference effectReference =
                input.effectSemanticSnapshot().toReference();

        // 6. compute plan fingerprint directly from ingredients (C7): avoids constructing
        // a provisional plan with a non-semantic placeholder fingerprint.
        RenderPlanFingerprint planFingerprint = RenderPlanFingerprintCalculator.compute(
                input.revision(), effectReference, input.request(),
                materialization.nodes(), materialization.edges());
        RenderPlan planWithFingerprint = new RenderPlan(
                id, RenderPlanCanonicalCodec.PLAN_FORMAT_VERSION,
                input.revision(), effectReference, input.request(),
                materialization.nodes(), materialization.edges(),
                planFingerprint,
                new RenderPlanProvenance(
                        RenderPlanCanonicalCodec.PLAN_FORMAT_VERSION,
                        input.revision().revisionId(),
                        effectReference));

        // 4. build graph (kernel delegation)
        RenderGraphBuilder graphBuilder = new RenderGraphBuilder();
        RenderGraphBuildResult buildResult = graphBuilder.build(planWithFingerprint);
        diagnostics.addAll(buildResult.diagnostics());

        RenderGraph graph = buildResult.graph();

        // 5. validate graph
        RenderGraphValidator validator = new RenderGraphValidator();
        RenderGraphValidationResult validation = validator.validate(
                planWithFingerprint, graph, buildResult.topology());
        diagnostics.addAll(validation.diagnostics());

        // 7. status
        RenderPlanStatus status = deriveStatus(diagnostics, sourceStates);

        // 8. deterministic diagnostic ordering (by code, then node id, then message)
        List<RenderPlanningDiagnostic> orderedDiagnostics = diagnostics.stream()
                .sorted(Comparator
                        .comparing(RenderPlanningDiagnostic::code)
                        .thenComparing(d -> d.nodeId().map(RenderNodeId::value).orElse(""))
                        .thenComparing(RenderPlanningDiagnostic::message))
                .collect(Collectors.toList());

        return new RenderPlanningResult(planWithFingerprint, graph, status, orderedDiagnostics);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private RenderPlanningDiagnosticCode sourceDiagnosticCode(RenderSourceResolutionState state) {
        return switch (state) {
            case FAILED -> RenderPlanningDiagnosticCode.SOURCE_UNRESOLVED;
            case UNAVAILABLE -> RenderPlanningDiagnosticCode.SOURCE_UNAVAILABLE;
            case PENDING -> RenderPlanningDiagnosticCode.SOURCE_RESOLUTION_PENDING;
            case BLOCKED -> RenderPlanningDiagnosticCode.DEPENDENCY_MISSING;
            default -> RenderPlanningDiagnosticCode.SOURCE_UNRESOLVED;
        };
    }

    private RenderPlanStatus deriveStatus(List<RenderPlanningDiagnostic> diagnostics,
            List<RenderSourceResolutionState> sourceStates) {
        boolean anyFailed = sourceStates.contains(RenderSourceResolutionState.FAILED);
        boolean anyHard = diagnostics.stream().anyMatch(d ->
                d.code() == RenderPlanningDiagnosticCode.INVALID_RENDER_EXTENT
                        || d.code() == RenderPlanningDiagnosticCode.MATERIALIZATION_FAILED
                        || d.code() == RenderPlanningDiagnosticCode.GRAPH_CYCLE
                        || d.code() == RenderPlanningDiagnosticCode.CAPABILITY_UNAVAILABLE);
        boolean anyPending = sourceStates.stream().anyMatch(s ->
                s == RenderSourceResolutionState.PENDING
                        || s == RenderSourceResolutionState.BLOCKED
                        || s == RenderSourceResolutionState.UNAVAILABLE);
        boolean hasError = diagnostics.stream().anyMatch(d -> d.severity() == RenderDiagnosticSeverity.ERROR);

        if (anyFailed || anyHard) {
            return RenderPlanStatus.UNRENDERABLE;
        }
        if (hasError) {
            return RenderPlanStatus.UNRENDERABLE;
        }
        if (anyPending) {
            return RenderPlanStatus.PREPARATION_REQUIRED;
        }
        boolean allResolved = sourceStates.stream().allMatch(s -> s == RenderSourceResolutionState.RESOLVED);
        if (allResolved) {
            return RenderPlanStatus.PLANNABLE;
        }
        return RenderPlanStatus.UNRENDERABLE;
    }

    /** Builds a minimal failure result when extent validation fails before materialization. */
    private RenderPlanningResult failureResult(RenderPlanningInput input, List<RenderPlanningDiagnostic> diagnostics) {
        RenderPlanId id = RenderPlanId.of(input.revision().revisionId(), input.request().id().value());
        EffectSemanticReference effectReference =
                input.effectSemanticSnapshot().toReference();
        RenderPlanFingerprint fp = RenderPlanFingerprintCalculator.compute(
                input.revision(), effectReference, input.request(), List.of(), List.of());
        RenderPlan plan = new RenderPlan(
                id, RenderPlanCanonicalCodec.PLAN_FORMAT_VERSION,
                input.revision(), effectReference, input.request(),
                List.of(), List.of(),
                fp,
                new RenderPlanProvenance(
                        RenderPlanCanonicalCodec.PLAN_FORMAT_VERSION,
                        input.revision().revisionId(),
                        effectReference));
        RenderGraphBuilder graphBuilder = new RenderGraphBuilder();
        RenderGraph graph = graphBuilder.build(plan).graph();
        List<RenderPlanningDiagnostic> ordered = diagnostics.stream()
                .sorted(Comparator
                        .comparing(RenderPlanningDiagnostic::code)
                        .thenComparing(d -> d.nodeId().map(RenderNodeId::value).orElse(""))
                        .thenComparing(RenderPlanningDiagnostic::message))
                .collect(Collectors.toList());
        return new RenderPlanningResult(plan, graph, RenderPlanStatus.UNRENDERABLE, ordered);
    }
}
