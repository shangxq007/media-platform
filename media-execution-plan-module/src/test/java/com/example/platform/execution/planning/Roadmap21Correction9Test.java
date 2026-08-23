package com.example.platform.execution.planning;

import com.example.platform.execution.domain.ExecutionEdgeId;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.extension.domain.ContractVersion;
import com.example.platform.extension.domain.ContractVersionRange;
import com.example.platform.render.domain.renderplan.EffectSemanticReference;
import com.example.platform.render.domain.renderplan.RenderComponentKind;
import com.example.platform.render.domain.renderplan.RenderComponentPath;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderDependencyEdge;
import com.example.platform.render.domain.renderplan.RenderExecutionCoverage;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderGraph;
import com.example.platform.render.domain.renderplan.RenderNode;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderPlan;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.render.domain.renderplan.RenderPlanId;
import com.example.platform.render.domain.renderplan.RenderPlanProvenance;
import com.example.platform.render.domain.renderplan.RenderRequest;
import com.example.platform.render.domain.renderplan.RenderRequestId;
import com.example.platform.render.domain.renderplan.TimelineRevisionReference;
import com.example.platform.render.domain.renderplan.graph.RenderGraphBuilder;
import com.example.platform.render.domain.renderplan.graph.RenderGraphValidator;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.semantics.effect.EffectSemanticContractVersion;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotId;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Roadmap #21 Correction 9: edge identity and governance closure. */
class Roadmap21Correction9Test {

    private static final RenderNodeId PRODUCER_ID = new RenderNodeId("p-c9");
    private static final RenderNodeId CONSUMER_ID = new RenderNodeId("c-c9");
    private static final RenderExtent EXTENT = new RenderExtent(
            MediaTime.ofMillis(0), MediaTime.ofMillis(10000), FrameRate.of(25, 1));

    @Test
    void sameEndpointTypedEdgesHaveDistinctDeterministicExecutionEdgeIds() { // C9-T01
        var fixtureA = validatedFixture(edgesInCanonicalOrder());
        var fixtureB = validatedFixture(edgesInCanonicalOrder());

        var resultA = plan(fixtureA, "pep-c9-identity-a");
        var resultB = plan(fixtureB, "pep-c9-identity-b");
        var logicalA = resultA.logicalExecutionGraph();
        var logicalB = resultB.logicalExecutionGraph();
        List<ExecutionEdgeId> idsA = edgeIds(logicalA);
        List<ExecutionEdgeId> idsB = edgeIds(logicalB);

        assertEquals(2, logicalA.edges().size(), "C9-T01 EDGE_COUNT=2");
        assertEquals(2, logicalA.edges().stream()
                        .map(edge -> edge.dependencyVariant().getClass()).distinct().count(),
                "C9-T01 DEPENDENCY_VARIANT_COUNT=2");
        assertEquals(2, idsA.size(), "C9-T01 EXECUTION_EDGE_ID_COUNT=2");
        assertEquals(2, idsA.stream().distinct().count(),
                "C9-T01 DISTINCT_EXECUTION_EDGE_ID_COUNT=2");
        assertEquals(idsA, idsB, "C9-T01 edge ids are deterministic");
        idsA.forEach(edgeId -> {
            assertNotEquals(edgeId.value(), logicalA.digest().sha256Hex(),
                    "C9-T01 ExecutionEdgeId != LogicalExecutionGraphDigest");
            assertNotEquals(edgeId.value(), resultA.physicalExecutionPlan().digest().sha256Hex(),
                    "C9-T01 ExecutionEdgeId != PhysicalExecutionPlanDigest");
        });
    }

    @Test
    void sameEndpointTypedEdgePermutationPreservesLogicalAndPhysicalModels() { // C9-T02
        var canonicalSource = renderPlan(edgesInCanonicalOrder());
        var permutedSource = renderPlan(edgesInPermutedOrder());
        assertNotEquals(canonicalSource.edges().get(0), permutedSource.edges().get(0),
                "C9-T02 source edge order is genuinely permuted");

        var fixtureA = validatedFixture(canonicalSource);
        var fixtureB = validatedFixture(permutedSource);
        var resultA = plan(fixtureA, "pep-c9-permutation");
        var resultB = plan(fixtureB, "pep-c9-permutation");
        var logicalA = resultA.logicalExecutionGraph();
        var logicalB = resultB.logicalExecutionGraph();
        var physicalA = resultA.physicalExecutionPlan();
        var physicalB = resultB.physicalExecutionPlan();

        assertEquals(2, logicalA.edges().size(), "C9-T02 canonical multiplicity remains 2");
        assertEquals(2, logicalB.edges().size(), "C9-T02 permuted multiplicity remains 2");
        assertEquals(logicalA, logicalB, "C9-T02 LOGICAL_MODEL_EQUAL=YES");
        assertEquals(edgeIds(logicalA), edgeIds(logicalB),
                "C9-T02 LOGICAL_EDGE_IDS_EQUAL=YES");
        assertEquals(logicalA.digest(), logicalB.digest(),
                "C9-T02 LOGICAL_DIGEST_EQUAL=YES");
        assertEquals(physicalA, physicalB, "C9-T02 PHYSICAL_MODEL_EQUAL=YES");
        assertEquals(consumerInputIds(physicalA), consumerInputIds(physicalB),
                "C9-T02 EXECUTION_INPUT_IDS_EQUAL=YES");
        assertEquals(physicalA.digest(), physicalB.digest(),
                "C9-T02 PHYSICAL_DIGEST_EQUAL=YES");
        assertEquals(2, consumerInputIds(physicalA).size(),
                "C9-T02 physical input multiplicity remains 2");
    }

    @Test
    void duplicateExecutionEdgeIdFailsClosedWithTypedContext() { // C9-T03
        var logical = plan(validatedFixture(edgesInCanonicalOrder()), "pep-c9-duplicate-source")
                .logicalExecutionGraph();
        var first = logical.edges().get(0);
        var second = logical.edges().get(1);
        var duplicateSecond = new LogicalExecutionGraph.LogicalDependencyEdge(
                first.edgeId(),
                second.producerLogicalNodeId(), second.consumerLogicalNodeId(),
                second.producerRenderNodeId(), second.consumerRenderNodeId(),
                second.dependencyVariant());
        var duplicateEdges = List.of(first, duplicateSecond);
        var duplicateLogical = new LogicalExecutionGraph(
                logical.formatVersion(), logical.planFingerprint(), logical.nodes(), duplicateEdges,
                logical.pruningEvidence(),
                LogicalExecutionGraphDigest.compute(
                        logical.formatVersion(), EXTENT, logical.nodes(), duplicateEdges,
                        logical.planFingerprint(), logical.pruningEvidence()));

        var failure = assertThrows(ExecutionPlanningException.class,
                () -> LogicalPhysicalPlanner.validateRefsResolve(duplicateLogical),
                "C9-T03 duplicate ExecutionEdgeId must fail closed");
        assertEquals(ExecutionPlanningFailureReason.INVALID_LOGICAL_GRAPH, failure.reason());
        assertTrue(failure.context() instanceof ExecutionPlanningException.DuplicateIdentityContext,
                "C9-T03 typed DuplicateIdentityContext required");
        var context = (ExecutionPlanningException.DuplicateIdentityContext) failure.context();
        assertEquals("executionEdgeId", context.identityKind());
        assertEquals(first.edgeId().value(), context.identity());
    }

    private static ExecutionPlanningEntry.PlanningResult plan(ValidatedFixture fixture, String planId) {
        return LogicalPhysicalPlanner.plan(
                fixture.plan(), fixture.graph(), new ExecutionPlanId(planId));
    }

    private static List<ExecutionEdgeId> edgeIds(LogicalExecutionGraph logical) {
        return logical.edges().stream().map(LogicalExecutionGraph.LogicalDependencyEdge::edgeId).toList();
    }

    private static List<String> consumerInputIds(PhysicalExecutionPlan physical) {
        return physical.units().stream()
                .filter(unit -> unit.logicalNodeId().equals("ln-" + CONSUMER_ID.value()))
                .findFirst().orElseThrow().typedInputs().stream()
                .map(input -> input.inputId().value()).toList();
    }

    private static List<RenderDependencyEdge> edgesInCanonicalOrder() {
        return List.of(
                new RenderDependencyEdge(PRODUCER_ID, CONSUMER_ID, new RenderDependency.DecodedFrames()),
                new RenderDependencyEdge(PRODUCER_ID, CONSUMER_ID, new RenderDependency.EffectInput()));
    }

    private static List<RenderDependencyEdge> edgesInPermutedOrder() {
        return List.of(
                new RenderDependencyEdge(PRODUCER_ID, CONSUMER_ID, new RenderDependency.EffectInput()),
                new RenderDependencyEdge(PRODUCER_ID, CONSUMER_ID, new RenderDependency.DecodedFrames()));
    }

    private static ValidatedFixture validatedFixture(List<RenderDependencyEdge> edges) {
        return validatedFixture(renderPlan(edges));
    }

    private static ValidatedFixture validatedFixture(RenderPlan sourcePlan) {
        var buildResult = new RenderGraphBuilder().build(sourcePlan);
        var validation = new RenderGraphValidator().validate(
                sourcePlan, buildResult.graph(), buildResult.topology());
        assertTrue(validation.valid(),
                "C9 VALIDATION_VALID=YES before #21 planning: " + validation.diagnostics());
        return new ValidatedFixture(sourcePlan, buildResult.graph());
    }

    private static RenderPlan renderPlan(List<RenderDependencyEdge> edges) {
        var effectReference = new EffectSemanticReference(
                new EffectSemanticSnapshotReference(
                        EffectSemanticSnapshotId.of("snapshot-c9"),
                        "b".repeat(64), EffectSemanticContractVersion.of("v1")),
                "revision-c9");
        return new RenderPlan(
                new RenderPlanId("render-plan-c9"),
                "render-plan-v1",
                new TimelineRevisionReference(
                        "revision-c9",
                        new ContentDigest(ContentDigest.DigestAlgorithm.SHA_256, "a".repeat(64))),
                effectReference,
                new RenderRequest(new RenderRequestId("request-c9"), EXTENT, List.of()),
                List.of(node(PRODUCER_ID, new RenderNodeKind.Decode()),
                        node(CONSUMER_ID, new RenderNodeKind.Effect())),
                edges,
                new RenderPlanFingerprint("fingerprint-c9"),
                new RenderPlanProvenance(
                        "render-plan-v1", "revision-c9", effectReference));
    }

    private static RenderNode node(RenderNodeId id, RenderNodeKind kind) {
        String operation = kind instanceof RenderNodeKind.Decode ? "decode" : "effect";
        return new RenderNode(
                id,
                kind,
                RenderComponentPath.of(RenderComponentKind.CLIP, "clip-" + id.value()),
                operation,
                List.of(),
                List.of(new CapabilityRequirement(
                        CapabilityId.of("media." + operation),
                        ContractVersionRange.exactly(ContractVersion.of(1, 0)),
                        true, List.of())),
                List.of(),
                List.of(new RenderExecutionRequirement(
                        RenderExecutionRequirement.GpuRequirement.NONE,
                        RenderExecutionRequirement.RenderDeterminismClass.DETERMINISTIC,
                        false)),
                List.of(),
                Optional.empty(),
                new RenderExecutionCoverage(
                        MediaTime.ofMillis(0), MediaTime.ofMillis(10000), FrameRate.of(25, 1)));
    }

    private record ValidatedFixture(RenderPlan plan, RenderGraph graph) {
    }
}
