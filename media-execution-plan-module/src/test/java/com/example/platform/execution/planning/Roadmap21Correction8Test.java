package com.example.platform.execution.planning;

import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.domain.ExecutionPlanSchemaVersion;
import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.extension.domain.ContractVersion;
import com.example.platform.extension.domain.ContractVersionRange;
import com.example.platform.fonttext.resolution.FontFallbackPolicy;
import com.example.platform.fonttext.resolution.ResolvedFontInstance;
import com.example.platform.fonttext.resolution.ResolvedFontRun;
import com.example.platform.fonttext.resource.FaceIndex;
import com.example.platform.fonttext.resource.FontContentDigest;
import com.example.platform.fonttext.resource.FontFormat;
import com.example.platform.fonttext.resource.ValidatedFontExecutionReference;
import com.example.platform.fonttext.security.FontSecurityState;
import com.example.platform.fonttext.text.ParagraphBaseDirection;
import com.example.platform.fonttext.text.RangeDirectionOverride;
import com.example.platform.fonttext.text.ScriptTag;
import com.example.platform.fonttext.text.StyledText;
import com.example.platform.fonttext.text.TextContent;
import com.example.platform.fonttext.text.TextRange;
import com.example.platform.fonttext.text.TextSemanticRun;
import com.example.platform.fonttext.typography.FontFamilyName;
import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.fonttext.typography.FontSelectionIntent;
import com.example.platform.fonttext.typography.FontSize;
import com.example.platform.fonttext.typography.LineHeight;
import com.example.platform.fonttext.typography.OpenTypeFeatureIntent;
import com.example.platform.fonttext.typography.OpticalSizingIntent;
import com.example.platform.fonttext.typography.ParagraphStyle;
import com.example.platform.fonttext.typography.TextFrame;
import com.example.platform.render.domain.renderplan.CapabilityContext;
import com.example.platform.render.domain.renderplan.DefaultRenderPlanner;
import com.example.platform.render.domain.renderplan.EffectMaterializationRequirement;
import com.example.platform.render.domain.renderplan.EffectSemanticReference;
import com.example.platform.render.domain.renderplan.RenderComponentKind;
import com.example.platform.render.domain.renderplan.RenderComponentPath;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderDependencyEdge;
import com.example.platform.render.domain.renderplan.RenderExecutionCoverage;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement.GpuRequirement;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement.RenderDeterminismClass;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderGraph;
import com.example.platform.render.domain.renderplan.RenderGraphFingerprint;
import com.example.platform.render.domain.renderplan.RenderMaterializationRequirement;
import com.example.platform.render.domain.renderplan.RenderNode;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderOutputRequirement;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.RenderPlan;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.render.domain.renderplan.RenderPlanId;
import com.example.platform.render.domain.renderplan.RenderPlanProvenance;
import com.example.platform.render.domain.renderplan.RenderPlanStatus;
import com.example.platform.render.domain.renderplan.RenderPlanningInput;
import com.example.platform.render.domain.renderplan.RenderRequest;
import com.example.platform.render.domain.renderplan.RenderRequestId;
import com.example.platform.render.domain.renderplan.RenderSourceResolutionState;
import com.example.platform.render.domain.renderplan.SourceResolutionInput;
import com.example.platform.render.domain.renderplan.TimelineRevisionReference;
import com.example.platform.render.domain.renderplan.VerifiedRenderSemanticSnapshotFactory;
import com.example.platform.render.domain.renderplan.graph.RenderGraphBuilder;
import com.example.platform.render.domain.renderplan.graph.RenderGraphValidator;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.digest.ContentDigest.DigestAlgorithm;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.canonical.TextElementId;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.effect.ClipEffectTarget;
import com.example.platform.timeline.semantics.effect.EffectDefinitionVersionRegistry;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.effect.EffectSemanticContractVersion;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotId;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import com.example.platform.timeline.version.TimelineRevision;
import com.example.platform.timeline.version.TimelineRevisionSemanticContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap #21 Correction 8: model normalization closure.
 */
class Roadmap21Correction8Test {

    private static final String SEP = "\u0001";
    private static final String PLAIN_PRODUCER = "P";
    private static final String COLLIDING_PRODUCER = "P" + SEP + "AUDIO_INPUT|1:t|16:x";
    private static final String CONSUMER = "C";
    private static final String REVISION_ID = "rev-c8";
    private static final String TRACK_ID = "track-c8";
    private static final String CLIP_ID = "clip-c8";
    private static final String ARTIFACT_ID = "artifact-c8";
    private static final String TEXT_ELEMENT_ID = "text-c8";
    private static final String ARTIFACT_DIGEST_HEX =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final RenderExtent EXTENT = new RenderExtent(
            MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1));

    @Test
    void oldEdgeSortKeyCollisionReproduced() { // C8-T01
        var a = adversarialEdgeA();
        var b = adversarialEdgeB();

        assertNotEquals(a, b, "C8-T01 semantic edge records differ");
        assertEquals(oldRawDelimiterKey(a), oldRawDelimiterKey(b),
                "C8-T01 old delimiter grammar collides for distinct edge inputs");
    }

    @Test
    void newEdgeSortKeyInjectiveForAdversarialRecords() { // C8-T02
        assertNotEquals(
                PhysicalPlannerV1.edgeCanonical(adversarialEdgeA()),
                PhysicalPlannerV1.edgeCanonical(adversarialEdgeB()),
                "C8-T02 framed edge input keys must be distinct");
    }

    @Test
    void adversarialEdgePermutationInputModelInvariant() { // C8-T03
        assertEquals(
                consumerInputs(plan(List.of(edgeA(), edgeB()), "pep-a")),
                consumerInputs(plan(List.of(edgeB(), edgeA()), "pep-b")),
                "C8-T03 normalized InputBinding model identical under adversarial edge permutation");
    }

    @Test
    void adversarialEdgePermutationInputIdsInvariant() { // C8-T04
        assertEquals(
                consumerInputs(plan(List.of(edgeA(), edgeB()), "pep-a")).stream()
                        .map(i -> i.inputId().value()).toList(),
                consumerInputs(plan(List.of(edgeB(), edgeA()), "pep-b")).stream()
                        .map(i -> i.inputId().value()).toList(),
                "C8-T04 ExecutionInputIds identical under adversarial edge permutation");
    }

    @Test
    void adversarialEdgePermutationPhysicalDigestInvariant() { // C8-T05
        assertEquals(
                plan(List.of(edgeA(), edgeB()), "pep-a").digest(),
                plan(List.of(edgeB(), edgeA()), "pep-b").digest(),
                "C8-T05 physical digest identical under adversarial edge permutation");
    }

    @Test
    void executionRequirementNodeOrderNormalized() { // C8-T06
        var n1 = requirementNode("n1", List.of(capability("media.alpha")), List.of(intent(false)));
        var n2 = requirementNode("n2", List.of(capability("media.beta")), List.of(intent(true)));

        assertEquals(
                ExecutionRequirement.derive(Roadmap21ContractBehaviorTest.plan(n1, n2)),
                ExecutionRequirement.derive(Roadmap21ContractBehaviorTest.plan(n2, n1)),
                "C8-T06 ExecutionRequirement equal under plan node order permutation");
    }

    @Test
    void executionRequirementCapabilityOrderNormalized() { // C8-T07
        var c1 = capability("media.alpha");
        var c2 = capability("media.beta");

        assertEquals(
                ExecutionRequirement.derive(Roadmap21ContractBehaviorTest.plan(
                        requirementNode("n1", List.of(c1, c2), List.of()))),
                ExecutionRequirement.derive(Roadmap21ContractBehaviorTest.plan(
                        requirementNode("n1", List.of(c2, c1), List.of()))),
                "C8-T07 ExecutionRequirement equal under capability requirement order permutation");
    }

    @Test
    void executionRequirementExecutionIntentOrderNormalized() { // C8-T08
        var e1 = intent(false);
        var e2 = intent(true);

        assertEquals(
                ExecutionRequirement.derive(Roadmap21ContractBehaviorTest.plan(
                        requirementNode("n1", List.of(), List.of(e1, e2)))),
                ExecutionRequirement.derive(Roadmap21ContractBehaviorTest.plan(
                        requirementNode("n1", List.of(), List.of(e2, e1)))),
                "C8-T08 ExecutionRequirement equal under execution-intent order permutation");
    }

    @Test
    void logicalArtifactModelNormalized() { // C8-T09
        var a1 = sourceArtifact("art-1", "a".repeat(64));
        var a2 = sourceArtifact("art-2", "b".repeat(64));

        assertEquals(
                onlyNode(build(graph(List.of(nodeWithArtifacts("n1", List.of(a1, a2))), List.of())))
                        .artifactReferences(),
                onlyNode(build(graph(List.of(nodeWithArtifacts("n1", List.of(a2, a1))), List.of())))
                        .artifactReferences(),
                "C8-T09 LogicalExecutionNode artifactReferences typed list equality");
    }

    @Test
    void logicalCapabilityModelNormalized() { // C8-T10
        var c1 = capability("media.alpha");
        var c2 = capability("media.beta");

        assertEquals(
                onlyNode(build(graph(List.of(requirementNode("n1", List.of(c1, c2), List.of())), List.of())))
                        .capabilityRequirements(),
                onlyNode(build(graph(List.of(requirementNode("n1", List.of(c2, c1), List.of())), List.of())))
                        .capabilityRequirements(),
                "C8-T10 LogicalExecutionNode capabilityRequirements typed list equality");
    }

    @Test
    void logicalExecutionRequirementModelNormalized() { // C8-T11
        var e1 = intent(false);
        var e2 = intent(true);

        assertEquals(
                onlyNode(build(graph(List.of(requirementNode("n1", List.of(), List.of(e1, e2))), List.of())))
                        .executionRequirements(),
                onlyNode(build(graph(List.of(requirementNode("n1", List.of(), List.of(e2, e1))), List.of())))
                        .executionRequirements(),
                "C8-T11 LogicalExecutionNode executionRequirements typed list equality");
    }

    @Test
    void logicalOutputRequirementModelNormalized() { // C8-T12
        var o1 = RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER);
        var o2 = RenderOutputRequirement.of(RenderOutputRole.DELIVERY_RENDITION);

        assertEquals(
                onlyNode(build(graph(List.of(nodeWithOutputs("n1", List.of(o1, o2))), List.of())))
                        .outputRequirements(),
                onlyNode(build(graph(List.of(nodeWithOutputs("n1", List.of(o2, o1))), List.of())))
                        .outputRequirements(),
                "C8-T12 LogicalExecutionNode outputRequirements typed list equality");
    }

    @Test
    void logicalMaterializationModelNormalized() { // C8-T13
        var m1 = materialization("inst-1", "def-1", EffectInstance.EffectCategory.COLOR_ADJUSTMENT);
        var m2 = materialization("inst-2", "def-2", EffectInstance.EffectCategory.FADE);

        assertEquals(
                onlyNode(build(graph(List.of(nodeWithMaterializations("n1", List.of(m1, m2))), List.of())))
                        .materializationRequirements(),
                onlyNode(build(graph(List.of(nodeWithMaterializations("n1", List.of(m2, m1))), List.of())))
                        .materializationRequirements(),
                "C8-T13 LogicalExecutionNode materializationRequirements typed list equality");
    }

    @Test
    void logicalNodeOrderModelNormalized() { // C8-T14
        var n1 = node("n1");
        var n2 = node("n2");

        assertEquals(
                build(graph(List.of(n1, n2), List.of())).nodes(),
                build(graph(List.of(n2, n1), List.of())).nodes(),
                "C8-T14 LogicalExecutionGraph node model order normalized by logicalNodeId");
    }

    @Test
    void logicalEdgeOrderModelNormalizedForValidatedGraph() { // C8-T15
        var p1 = validatedNode("p1", new RenderNodeKind.Decode());
        var p2 = validatedNode("p2", new RenderNodeKind.Decode());
        var consumer = validatedNode("c1", new RenderNodeKind.Effect());
        var e1 = new RenderDependencyEdge(
                p1.id(), consumer.id(), new RenderDependency.DecodedFrames());
        var e2 = new RenderDependencyEdge(
                p2.id(), consumer.id(), new RenderDependency.DecodedFrames());
        var plan = renderPlan(List.of(p1, p2, consumer), List.of(e1, e2));
        var buildResult = new RenderGraphBuilder().build(plan);
        var validation = new RenderGraphValidator().validate(plan, buildResult.graph(), buildResult.topology());
        assertTrue(validation.valid(), "C8-T15 graph must pass actual #20 validation: " + validation.diagnostics());

        var gA = graph(List.of(p1, p2, consumer), List.of(e1, e2));
        var gB = graph(List.of(p1, p2, consumer), List.of(e2, e1));
        assertNotEquals(gA.edges().get(0), gB.edges().get(0),
                "C8-T15 edge permutation must genuinely differ");

        assertEquals(
                build(gA).edges(),
                build(gB).edges(),
                "C8-T15 LogicalExecutionGraph edge list normalized by complete framed semantics");
    }

    @Test
    void physicalOutputRequirementModelNormalized() { // C8-T16
        var o1 = RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER);
        var o2 = RenderOutputRequirement.of(RenderOutputRole.DELIVERY_RENDITION);

        assertEquals(
                onlyOutput(plan(graph(List.of(nodeWithOutputs("n1", List.of(o1, o2))), List.of()), "pep-a"),
                        "ln-n1").outputRequirements(),
                onlyOutput(plan(graph(List.of(nodeWithOutputs("n1", List.of(o2, o1))), List.of()), "pep-b"),
                        "ln-n1").outputRequirements(),
                "C8-T16 OutputDeclaration outputRequirements typed list equality");
    }

    @Test
    void physicalMaterializationModelNormalized() { // C8-T17
        var m1 = materialization("inst-1", "def-1", EffectInstance.EffectCategory.COLOR_ADJUSTMENT);
        var m2 = materialization("inst-2", "def-2", EffectInstance.EffectCategory.FADE);

        assertEquals(
                onlyOutput(plan(graph(List.of(nodeWithMaterializations("n1", List.of(m1, m2))), List.of()),
                        "pep-a"), "ln-n1").materializationRequirements(),
                onlyOutput(plan(graph(List.of(nodeWithMaterializations("n1", List.of(m2, m1))), List.of()),
                        "pep-b"), "ln-n1").materializationRequirements(),
                "C8-T17 OutputDeclaration materializationRequirements typed list equality");
    }

    @Test
    void physicalArtifactExpectationModelNormalized() { // C8-T18
        var mid1 = new com.example.platform.render.domain.renderplan.RenderArtifactReference
                .IntermediateArtifactExpectation(
                        new com.example.platform.render.domain.renderplan.LogicalArtifactId("mid-1"),
                        RenderOutputRole.RENDER_MASTER);
        var mid2 = new com.example.platform.render.domain.renderplan.RenderArtifactReference
                .IntermediateArtifactExpectation(
                        new com.example.platform.render.domain.renderplan.LogicalArtifactId("mid-2"),
                        RenderOutputRole.DELIVERY_RENDITION);
        var fin1 = new com.example.platform.render.domain.renderplan.RenderArtifactReference
                .FinalArtifactExpectation(RenderOutputRole.RENDER_MASTER);
        var fin2 = new com.example.platform.render.domain.renderplan.RenderArtifactReference
                .FinalArtifactExpectation(RenderOutputRole.DELIVERY_RENDITION);

        assertEquals(
                onlyOutput(plan(graph(List.of(nodeWithArtifacts("n1", List.of(mid1, mid2, fin1, fin2))),
                        List.of()), "pep-a"), "ln-n1"),
                onlyOutput(plan(graph(List.of(nodeWithArtifacts("n1", List.of(fin2, fin1, mid2, mid1))),
                        List.of()), "pep-b"), "ln-n1"),
                "C8-T18 OutputDeclaration equal under intermediate/final expectation permutations");
    }

    @Test
    void physicalCapabilityRefModelNormalized() { // C8-T19
        var c1 = capability("media.alpha");
        var c2 = capability("media.beta");

        assertEquals(
                unitFor(plan(graph(List.of(requirementNode("n1", List.of(c1, c2), List.of())), List.of()),
                        "pep-a"), "ln-n1").capabilityRequirementRefs(),
                unitFor(plan(graph(List.of(requirementNode("n1", List.of(c2, c1), List.of())), List.of()),
                        "pep-b"), "ln-n1").capabilityRequirementRefs(),
                "C8-T19 PhysicalPlanUnit capabilityRequirementRefs typed list equality");
    }

    @Test
    void physicalExecutionIntentRefModelNormalized() { // C8-T20
        var e1 = intent(false);
        var e2 = intent(true);

        assertEquals(
                unitFor(plan(graph(List.of(requirementNode("n1", List.of(), List.of(e1, e2))), List.of()),
                        "pep-a"), "ln-n1").executionIntentRefs(),
                unitFor(plan(graph(List.of(requirementNode("n1", List.of(), List.of(e2, e1))), List.of()),
                        "pep-b"), "ln-n1").executionIntentRefs(),
                "C8-T20 PhysicalPlanUnit executionIntentRefs typed list equality");
    }

    @Test
    void physicalDependencyModelNormalizedForValidatedGraph() { // C8-T21
        var p1 = validatedNode("p1", new RenderNodeKind.Decode());
        var p2 = validatedNode("p2", new RenderNodeKind.Decode());
        var consumer = validatedNode("c1", new RenderNodeKind.Effect());
        var e1 = new RenderDependencyEdge(
                p1.id(), consumer.id(), new RenderDependency.DecodedFrames());
        var e2 = new RenderDependencyEdge(
                p2.id(), consumer.id(), new RenderDependency.DecodedFrames());
        var plan = renderPlan(List.of(p1, p2, consumer), List.of(e1, e2));
        var buildResult = new RenderGraphBuilder().build(plan);
        var validation = new RenderGraphValidator().validate(plan, buildResult.graph(), buildResult.topology());
        assertTrue(validation.valid(), "C8-T21 graph must pass actual #20 validation: " + validation.diagnostics());

        assertEquals(
                unitFor(plan(graph(List.of(p1, p2, consumer), List.of(e1, e2)), "pep-a"),
                        "ln-c1").typedDependencies(),
                unitFor(plan(graph(List.of(p1, p2, consumer), List.of(e2, e1)), "pep-b"),
                        "ln-c1").typedDependencies(),
                "C8-T21 PhysicalPlanUnit typedDependencies equal under validated edge permutation");
    }

    @Test
    void physicalUnitOrderModelNormalized() { // C8-T22
        var n1 = node("n1");
        var n2 = node("n2");

        assertEquals(
                physicalSemanticProjection(plan(graph(List.of(n1, n2), List.of()), "pep-a")),
                physicalSemanticProjection(plan(graph(List.of(n2, n1), List.of()), "pep-b")),
                "C8-T22 physical semantic unit projection equal under input node traversal permutation");
    }

    @Test
    void fullModelAndDigestDeterminismAcrossNonSemanticPermutations() { // C8-T23
        var p1A = validatedNodeWithDeclarations("p1", new RenderNodeKind.Decode(),
                List.of(capability("media.decode"), capability("media.decode.alt")),
                List.of(intent(false), intent(true)), List.of(), List.of());
        var p2A = validatedNodeWithDeclarations("p2", new RenderNodeKind.Decode(),
                List.of(capability("media.decode.alt"), capability("media.decode")),
                List.of(intent(true), intent(false)), List.of(), List.of());
        var c1A = validatedNodeWithDeclarations("c1", new RenderNodeKind.Effect(),
                List.of(capability("media.effect")),
                List.of(intent(false)),
                List.of(RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER),
                        RenderOutputRequirement.of(RenderOutputRole.DELIVERY_RENDITION)),
                List.of(materialization("inst-1", "def-1", EffectInstance.EffectCategory.COLOR_ADJUSTMENT),
                        materialization("inst-2", "def-2", EffectInstance.EffectCategory.FADE)));
        var c1B = validatedNodeWithDeclarations("c1", new RenderNodeKind.Effect(),
                List.of(capability("media.effect")),
                List.of(intent(false)),
                List.of(RenderOutputRequirement.of(RenderOutputRole.DELIVERY_RENDITION),
                        RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER)),
                List.of(materialization("inst-2", "def-2", EffectInstance.EffectCategory.FADE),
                        materialization("inst-1", "def-1", EffectInstance.EffectCategory.COLOR_ADJUSTMENT)));
        var e1 = new RenderDependencyEdge(p1A.id(), c1A.id(), new RenderDependency.DecodedFrames());
        var e2 = new RenderDependencyEdge(p2A.id(), c1A.id(), new RenderDependency.DecodedFrames());

        var planA = renderPlan(List.of(p1A, p2A, c1A), List.of(e1, e2));
        var planB = renderPlan(List.of(p2A, p1A, c1B), List.of(e2, e1));
        assertNotEquals(planA.nodes().get(0), planB.nodes().get(0),
                "C8-T23 node order permutation must genuinely differ");
        assertNotEquals(planA.edges().get(0), planB.edges().get(0),
                "C8-T23 edge order permutation must genuinely differ");

        var graphA = graph(planA.nodes(), planA.edges());
        var graphB = graph(planB.nodes(), planB.edges());
        var logicalA = build(graphA);
        var logicalB = build(graphB);
        var physicalA = plan(graphA, "pep-c8-full-a");
        var physicalB = plan(graphB, "pep-c8-full-b");

        assertEquals(
                executionRequirementSemanticProjection(ExecutionRequirement.derive(planA)),
                executionRequirementSemanticProjection(ExecutionRequirement.derive(planB)),
                "C8-T23 ExecutionRequirement semantic projection equal under non-semantic permutations");
        assertEquals(
                logicalSemanticProjection(logicalA),
                logicalSemanticProjection(logicalB),
                "C8-T23 LogicalExecutionGraph semantic projection equal under non-semantic permutations");
        assertEquals(
                physicalSemanticProjection(physicalA),
                physicalSemanticProjection(physicalB),
                "C8-T23 PhysicalExecutionPlan semantic projection equal under non-semantic permutations");
        assertEquals(logicalA.digest(), logicalB.digest(),
                "C8-T23 logical digest equal under non-semantic permutations");
        assertEquals(physicalA.digest(), physicalB.digest(),
                "C8-T23 physical digest equal under non-semantic permutations");
    }

    @Test
    void capabilitySemanticMutationsChangeLogicalAndPhysicalDigests() { // C8-T24
        var base = capability("media.capability.base");
        assertCapabilityMutationChangesDigests(base, new CapabilityRequirement(
                CapabilityId.of("media.capability.changed"),
                ContractVersionRange.exactly(ContractVersion.of(1, 0)),
                true,
                List.of()), "capability id");
        assertCapabilityMutationChangesDigests(base, new CapabilityRequirement(
                CapabilityId.of("media.capability.base"),
                ContractVersionRange.exactly(ContractVersion.of(2, 0)),
                true,
                List.of()), "capability contract range");
        assertCapabilityMutationChangesDigests(base, new CapabilityRequirement(
                CapabilityId.of("media.capability.base"),
                ContractVersionRange.exactly(ContractVersion.of(1, 0)),
                true,
                List.of(CapabilityId.of("media.capability.alternative"))), "capability alternatives");
    }

    @Test
    void outputSemanticMutationsChangeLogicalAndPhysicalDigests() { // C8-T25
        var base = outputRequirement(RenderOutputRole.RENDER_MASTER, bt709(), raster8bit());
        assertOutputMutationChangesDigests(base,
                outputRequirement(RenderOutputRole.DELIVERY_RENDITION, bt709(), raster8bit()),
                "output role");
        assertOutputMutationChangesDigests(base,
                outputRequirement(RenderOutputRole.RENDER_MASTER, bt2020(), raster8bit()),
                "output color description");
        assertOutputMutationChangesDigests(base,
                outputRequirement(RenderOutputRole.RENDER_MASTER, bt709(), raster10bit()),
                "output raster sample");
    }

    @Test
    void materializationSemanticMutationChangesLogicalAndPhysicalDigests() { // C8-T26
        var base = materializationWithParameter("amount", "0.50");
        var mutated = materializationWithParameter("amount", "0.75");

        assertNotEquals(
                logicalSemanticProjection(build(graph(List.of(nodeWithMaterializations("n1", List.of(base))),
                        List.of()))),
                logicalSemanticProjection(build(graph(List.of(nodeWithMaterializations("n1", List.of(mutated))),
                        List.of()))),
                "C8-T26 materialization payload mutation changes logical typed semantic model");
        assertNotEquals(
                build(graph(List.of(nodeWithMaterializations("n1", List.of(base))), List.of())).digest(),
                build(graph(List.of(nodeWithMaterializations("n1", List.of(mutated))), List.of())).digest(),
                "C8-T26 materialization payload mutation changes logical digest");
        assertNotEquals(
                plan(graph(List.of(nodeWithMaterializations("n1", List.of(base))), List.of()),
                        "pep-c8-materialization-a").digest(),
                plan(graph(List.of(nodeWithMaterializations("n1", List.of(mutated))), List.of()),
                        "pep-c8-materialization-b").digest(),
                "C8-T26 materialization payload mutation changes physical digest");
    }

    @Test
    void dependencySemanticMutationsChangeLogicalAndPhysicalDigests() { // C8-T27
        var producer = validatedNode("p1", new RenderNodeKind.Decode());
        var output = validatedNode("out1", new RenderNodeKind.Output());
        var base = new RenderDependencyEdge(
                producer.id(), output.id(), new RenderDependency.AudioInput(new AudioMixInput("main", "left")));

        assertDependencyMutationChangesDigests(producer, output, base,
                new RenderDependencyEdge(
                        producer.id(), output.id(), new RenderDependency.AudioInput(new AudioMixInput("main", "right"))),
                "dependency payload");
        assertDependencyMutationChangesDigests(producer, output, base,
                new RenderDependencyEdge(producer.id(), output.id(), new RenderDependency.EffectInput()),
                "dependency variant");
    }

    @Test
    void executionPlanIdExcludedFromPhysicalSemanticDigest() { // C8-T28
        var graph = graph(List.of(node("n1")), List.of());
        var a = plan(graph, "pep-c8-id-a");
        var b = plan(graph, "pep-c8-id-b");

        assertNotEquals(a.planId(), b.planId(),
                "C8-T28 fixture must use different ExecutionPlanId values");
        assertEquals(physicalSemanticProjection(a), physicalSemanticProjection(b),
                "C8-T28 different plan id leaves physical semantic model unchanged");
        assertEquals(a.digest(), b.digest(),
                "C8-T28 ExecutionPlanId is excluded from physical semantic digest");
    }

    @Test
    void onlyExecutionPlanningEntryIsPublicPlanningEntry() throws IOException { // C8-T29
        String entry = stripComments(Files.readString(repoRoot().resolve(
                "media-execution-plan-module/src/main/java/com/example/platform/execution/planning/ExecutionPlanningEntry.java")));
        String logicalBuilder = stripComments(Files.readString(repoRoot().resolve(
                "media-execution-plan-module/src/main/java/com/example/platform/execution/planning/LogicalExecutionGraphBuilder.java")));
        String physicalPlanner = stripComments(Files.readString(repoRoot().resolve(
                "media-execution-plan-module/src/main/java/com/example/platform/execution/planning/PhysicalPlannerV1.java")));
        String logicalPhysicalPlanner = stripComments(Files.readString(repoRoot().resolve(
                "media-execution-plan-module/src/main/java/com/example/platform/execution/planning/LogicalPhysicalPlanner.java")));

        assertTrue(entry.contains("public final class ExecutionPlanningEntry"),
                "C8-T29 ExecutionPlanningEntry is the public supported entry");
        assertTrue(entry.contains("public static PlanningResult plan("),
                "C8-T29 public entry returns entry-owned result type");
        assertTrue(entry.contains("public record PlanningResult("),
                "C8-T29 public result carrier is owned by ExecutionPlanningEntry");
        assertFalse(entry.contains("LogicalPhysicalPlanner.PlanningResult"),
                "C8-T29 public API must not leak package-private planner result type");
        assertFalse(logicalBuilder.contains("public final class LogicalExecutionGraphBuilder"),
                "PUBLIC_LOGICAL_GRAPH_BUILDER_ENTRY_COUNT=0");
        assertFalse(logicalBuilder.contains("public static LogicalExecutionGraph build("),
                "LogicalExecutionGraphBuilder.build is package-private");
        assertFalse(physicalPlanner.contains("public final class PhysicalPlannerV1"),
                "PUBLIC_PHYSICAL_PLANNER_ENTRY_COUNT=0");
        assertFalse(physicalPlanner.contains("public static PhysicalExecutionPlan plan("),
                "PhysicalPlannerV1.plan is package-private");
        assertFalse(logicalPhysicalPlanner.contains("public final class LogicalPhysicalPlanner"),
                "PUBLIC_LOGICAL_PHYSICAL_PLANNER_ENTRY_COUNT=0");
        assertFalse(logicalPhysicalPlanner.contains("public static"),
                "LogicalPhysicalPlanner has no public static planning entry");
    }

    @Test
    void repositoryWideProductionBypassCallersZero() throws IOException { // C8-T30
        var sources = productionJavaSources();
        assertTrue(sources.stream().anyMatch(p -> p.endsWith(
                        Path.of("media-execution-plan-module/src/main/java/com/example/platform/execution/planning/ExecutionPlanningEntry.java"))),
                "C8-T30 scan scope is all repository production Java sources");

        int directGraphBuilderCallers = 0;
        int directPhysicalPlannerCallers = 0;
        int directLogicalPhysicalPlannerCallers = 0;
        int publicLowLevelEntryCount = 0;
        int publicLogicalGraphBuilderEntryCount = 0;
        int publicPhysicalPlannerEntryCount = 0;
        int publicLogicalPhysicalPlannerEntryCount = 0;
        int publicExecutionPlanningEntryCount = 0;

        for (Path file : sources) {
            String source = stripComments(Files.readString(file));
            String path = repoRoot().relativize(file).toString();
            if (source.contains("LogicalExecutionGraphBuilder.build(")
                    && !path.endsWith("LogicalPhysicalPlanner.java")) {
                directGraphBuilderCallers++;
            }
            if (source.contains("PhysicalPlannerV1.plan(")
                    && !path.endsWith("LogicalPhysicalPlanner.java")) {
                directPhysicalPlannerCallers++;
            }
            if (source.contains("LogicalPhysicalPlanner.plan(")
                    && !path.endsWith("ExecutionPlanningEntry.java")) {
                directLogicalPhysicalPlannerCallers++;
            }
            if (source.contains("public final class LogicalExecutionGraphBuilder")) {
                publicLowLevelEntryCount++;
                publicLogicalGraphBuilderEntryCount++;
            }
            if (source.contains("public static LogicalExecutionGraph build(")) {
                publicLowLevelEntryCount++;
                publicLogicalGraphBuilderEntryCount++;
            }
            if (source.contains("public final class PhysicalPlannerV1")) {
                publicLowLevelEntryCount++;
                publicPhysicalPlannerEntryCount++;
            }
            if (source.contains("public static PhysicalExecutionPlan plan(")) {
                publicLowLevelEntryCount++;
                publicPhysicalPlannerEntryCount++;
            }
            if (source.contains("public final class LogicalPhysicalPlanner")) {
                publicLowLevelEntryCount++;
                publicLogicalPhysicalPlannerEntryCount++;
            }
            if (source.contains("public static ExecutionPlanningEntry.PlanningResult plan(")
                    || source.contains("public static PlanningResult plan(RenderPlan")) {
                publicLowLevelEntryCount++;
                publicLogicalPhysicalPlannerEntryCount++;
            }
            if (source.contains("public final class ExecutionPlanningEntry")) {
                publicExecutionPlanningEntryCount++;
            }
        }

        assertEquals(0, directGraphBuilderCallers,
                "PRODUCTION_DIRECT_LOGICAL_GRAPH_BUILDER_CALLERS_OUTSIDE_INTERNAL_CHAIN=0");
        assertEquals(0, directPhysicalPlannerCallers,
                "PRODUCTION_DIRECT_PHYSICAL_PLANNER_CALLERS_OUTSIDE_INTERNAL_CHAIN=0");
        assertEquals(0, directLogicalPhysicalPlannerCallers,
                "PRODUCTION_DIRECT_LOGICAL_PHYSICAL_PLANNER_CALLERS_OUTSIDE_GUARDED_ENTRY=0");
        assertEquals(0, publicLowLevelEntryCount,
                "PUBLIC_LOW_LEVEL_EXECUTION_PLANNING_ENTRY_COUNT=0");
        assertEquals(0, publicLogicalGraphBuilderEntryCount,
                "PUBLIC_LOGICAL_GRAPH_BUILDER_ENTRY_COUNT=0");
        assertEquals(0, publicPhysicalPlannerEntryCount,
                "PUBLIC_PHYSICAL_PLANNER_ENTRY_COUNT=0");
        assertEquals(0, publicLogicalPhysicalPlannerEntryCount,
                "PUBLIC_LOGICAL_PHYSICAL_PLANNER_ENTRY_COUNT=0");
        assertEquals(1, publicExecutionPlanningEntryCount,
                "PUBLIC_EXECUTION_PLANNING_ENTRY_COUNT=1");
    }

    @Test
    void defaultRenderPlannerUnrenderableRejectedFailClosed() { // C8-T31
        var input = renderPlanningInput(textElement(), true);
        var result = new DefaultRenderPlanner().plan(input);

        assertEquals(RenderPlanStatus.UNRENDERABLE, result.status(),
                "C8-T31 real DefaultRenderPlanner source failure -> UNRENDERABLE");
        var ex = assertThrows(ExecutionPlanningException.class,
                () -> ExecutionPlanningEntry.plan(result, new ExecutionPlanId("pep-c8-reject")),
                "C8-T31 ExecutionPlanningEntry rejects UNRENDERABLE");
        assertEquals(ExecutionPlanningFailureReason.RENDER_PLANNING_RESULT_NOT_PLANNABLE, ex.reason());
        ExecutionPlanningEntry.PlanningResult[] planned = new ExecutionPlanningEntry.PlanningResult[1];
        assertThrows(ExecutionPlanningException.class,
                () -> planned[0] = ExecutionPlanningEntry.plan(result, new ExecutionPlanId("pep-c8-none")));
        assertNull(planned[0], "C8-T31 rejected result produces no logical or physical result carrier");
    }

    @Test
    void validTimedTextReaches21ThroughPublicEntry() { // C8-T32
        var input = renderPlanningInput(textElement(), false);
        var result = new DefaultRenderPlanner().plan(input);

        assertEquals(RenderPlanStatus.PLANNABLE, result.status(),
                "C8-T32 valid TIMED_TEXT result is PLANNABLE");
        assertTrue(result.graph().nodes().stream().anyMatch(n -> n.kind() instanceof RenderNodeKind.TimedText),
                "C8-T32 DefaultRenderPlanner produced a real TIMED_TEXT node");
        ExecutionPlanningEntry.PlanningResult planned =
                ExecutionPlanningEntry.plan(result, new ExecutionPlanId("pep-c8-timed-text"));
        assertNotNull(planned.executionRequirement(), "C8-T32 execution requirement produced");
        assertNotNull(planned.logicalExecutionGraph(), "C8-T32 logical result produced");
        assertNotNull(planned.physicalExecutionPlan(), "C8-T32 physical result produced");
        assertTrue(planned.logicalExecutionGraph().nodes().stream()
                        .anyMatch(n -> n.sourceRenderNodeKind() instanceof RenderNodeKind.TimedText),
                "C8-T32 valid TIMED_TEXT reaches #21 logical model through public entry");
        assertTrue(planned.physicalExecutionPlan().units().stream()
                        .anyMatch(u -> u.sourceRenderNodeKind() instanceof RenderNodeKind.TimedText),
                "C8-T32 valid TIMED_TEXT reaches #21 physical model through public entry");
    }

    @Test
    void validatedMultiEdgeGraphPassesValidationBeforePermutationProof() { // C8-T33
        var p1 = validatedNode("p1", new RenderNodeKind.Decode());
        var p2 = validatedNode("p2", new RenderNodeKind.Decode());
        var consumer = validatedNode("c1", new RenderNodeKind.Effect());
        var e1 = new RenderDependencyEdge(
                p1.id(), consumer.id(), new RenderDependency.DecodedFrames());
        var e2 = new RenderDependencyEdge(
                p2.id(), consumer.id(), new RenderDependency.DecodedFrames());
        var renderPlan = renderPlan(List.of(p1, p2, consumer), List.of(e1, e2));
        var buildResult = new RenderGraphBuilder().build(renderPlan);
        var validation = new RenderGraphValidator().validate(
                renderPlan, buildResult.graph(), buildResult.topology());
        assertTrue(validation.valid(),
                "C8-T33 VALIDATED_MULTI_EDGE_GRAPH=YES via actual #20 validator: " + validation.diagnostics());

        var gA = graph(List.of(p1, p2, consumer), List.of(e1, e2));
        var gB = graph(List.of(p1, p2, consumer), List.of(e2, e1));
        assertNotEquals(gA.edges().get(0), gB.edges().get(0),
                "C8-T33 permutation actually differs in source input edge order");

        var logicalA = build(gA);
        var logicalB = build(gB);
        var physicalA = plan(gA, "pep-c8-validated-a");
        var physicalB = plan(gB, "pep-c8-validated-b");

        assertEquals(logicalSemanticProjection(logicalA), logicalSemanticProjection(logicalB),
                "C8-T33 validated multi-edge graph logical model equal under edge permutation");
        assertEquals(physicalSemanticProjection(physicalA), physicalSemanticProjection(physicalB),
                "C8-T33 validated multi-edge graph physical semantic model equal under edge permutation");
        assertEquals(
                unitFor(physicalA, "ln-c1").typedInputs().stream().map(i -> i.inputId().value()).toList(),
                unitFor(physicalB, "ln-c1").typedInputs().stream().map(i -> i.inputId().value()).toList(),
                "C8-T33 validated multi-edge graph ExecutionInputIds equal under edge permutation");
        assertEquals(logicalA.digest(), logicalB.digest(),
                "C8-T33 validated multi-edge graph logical digest equal under edge permutation");
        assertEquals(physicalA.digest(), physicalB.digest(),
                "C8-T33 validated multi-edge graph physical digest equal under edge permutation");
    }


    private static PhysicalPlannerV1.RenderDependencyEdgeLike adversarialEdgeA() {
        return new PhysicalPlannerV1.RenderDependencyEdgeLike(
                "ln-" + PLAIN_PRODUCER,
                "ln-" + CONSUMER,
                new RenderNodeId(PLAIN_PRODUCER),
                new RenderNodeId(CONSUMER),
                new RenderDependency.AudioInput(new AudioMixInput("t", "x" + SEP + "DECODED_FRAMES")));
    }

    private static PhysicalPlannerV1.RenderDependencyEdgeLike adversarialEdgeB() {
        return new PhysicalPlannerV1.RenderDependencyEdgeLike(
                "ln-" + COLLIDING_PRODUCER,
                "ln-" + CONSUMER,
                new RenderNodeId(COLLIDING_PRODUCER),
                new RenderNodeId(CONSUMER),
                new RenderDependency.DecodedFrames());
    }

    private static String oldRawDelimiterKey(PhysicalPlannerV1.RenderDependencyEdgeLike e) {
        return e.producerLogicalNodeId() + SEP + Canonical.dependency(e.dependencyVariant());
    }

    private static RenderDependencyEdge edgeA() {
        return new RenderDependencyEdge(
                new RenderNodeId(PLAIN_PRODUCER),
                new RenderNodeId(CONSUMER),
                new RenderDependency.AudioInput(new AudioMixInput("t", "x" + SEP + "DECODED_FRAMES")));
    }

    private static RenderDependencyEdge edgeB() {
        return new RenderDependencyEdge(
                new RenderNodeId(COLLIDING_PRODUCER),
                new RenderNodeId(CONSUMER),
                new RenderDependency.DecodedFrames());
    }

    private static PhysicalExecutionPlan plan(List<RenderDependencyEdge> edges, String planId) {
        return plan(graph(edges), planId);
    }

    private static PhysicalExecutionPlan plan(RenderGraph graph, String planId) {
        var logical = LogicalExecutionGraphBuilder.build(graph, EXTENT);
        return PhysicalPlannerV1.plan(logical, EXTENT, new ExecutionPlanId(planId));
    }

    private static List<ExecutionIoProjection.InputBinding> consumerInputs(PhysicalExecutionPlan plan) {
        return plan.units().stream()
                .filter(u -> u.logicalNodeId().equals("ln-" + CONSUMER))
                .findFirst()
                .orElseThrow()
                .typedInputs();
    }

    private static ExecutionIoProjection.OutputDeclaration onlyOutput(
            PhysicalExecutionPlan plan, String logicalNodeId) {
        var outputs = unitFor(plan, logicalNodeId).typedOutputs();
        assertEquals(1, outputs.size(), "fixture must produce exactly one output declaration");
        return outputs.get(0);
    }

    private static PhysicalExecutionPlan.PhysicalPlanUnit unitFor(
            PhysicalExecutionPlan plan, String logicalNodeId) {
        return plan.units().stream()
                .filter(u -> u.logicalNodeId().equals(logicalNodeId))
                .findFirst()
                .orElseThrow();
    }

    private record ExecutionRequirementSemanticProjection(
            RenderPlanFingerprint planFingerprint,
            RenderExtent requestedExtent,
            List<ExecutionIoProjection.CapabilityRequirementRef> capabilityRequirementRefs,
            List<ExecutionIoProjection.ExecutionIntentRef> executionIntentRefs) {
    }

    private static ExecutionRequirementSemanticProjection executionRequirementSemanticProjection(
            ExecutionRequirement requirement) {
        return new ExecutionRequirementSemanticProjection(
                requirement.planFingerprint(),
                requirement.requestedExtent(),
                requirement.capabilityRequirementRefs(),
                requirement.executionIntentRefs());
    }

    private record LogicalSemanticProjection(
            String formatVersion,
            RenderPlanFingerprint planFingerprint,
            List<LogicalExecutionGraph.LogicalExecutionNode> nodes,
            List<LogicalExecutionGraph.LogicalDependencyEdge> edges,
            LogicalExecutionGraph.PruningEvidence pruningEvidence) {
    }

    private static LogicalSemanticProjection logicalSemanticProjection(LogicalExecutionGraph graph) {
        return new LogicalSemanticProjection(
                graph.formatVersion(),
                graph.planFingerprint(),
                graph.nodes(),
                graph.edges(),
                graph.pruningEvidence());
    }

    private record PhysicalSemanticProjection(
            String formatVersion,
            ExecutionPlanSchemaVersion schemaVersion,
            RenderPlanFingerprint planFingerprint,
            List<PhysicalExecutionPlan.PhysicalPlanUnit> units,
            RenderExtent propagatedExtent) {
    }

    private static PhysicalSemanticProjection physicalSemanticProjection(PhysicalExecutionPlan plan) {
        return new PhysicalSemanticProjection(
                plan.formatVersion(),
                plan.schemaVersion(),
                plan.planFingerprint(),
                plan.units(),
                plan.propagatedExtent());
    }

    private static RenderGraph graph(List<RenderDependencyEdge> edges) {
        return graph(List.of(node(PLAIN_PRODUCER), node(COLLIDING_PRODUCER), node(CONSUMER)), edges);
    }

    private static RenderGraph graph(List<RenderNode> nodes, List<RenderDependencyEdge> edges) {
        return new RenderGraph("render-graph-v1",
                new RenderPlanFingerprint("fp-c8"),
                nodes, edges,
                new RenderGraphFingerprint("gf-c8"));
    }

    private static LogicalExecutionGraph build(RenderGraph graph) {
        return LogicalExecutionGraphBuilder.build(graph, EXTENT);
    }

    private static LogicalExecutionGraph.LogicalExecutionNode onlyNode(LogicalExecutionGraph graph) {
        assertEquals(1, graph.nodes().size(), "fixture must produce one logical node");
        return graph.nodes().get(0);
    }

    private static void assertCapabilityMutationChangesDigests(
            CapabilityRequirement base,
            CapabilityRequirement mutated,
            String fieldName) {
        var baseGraph = graph(List.of(requirementNode("n1", List.of(base), List.of())), List.of());
        var mutatedGraph = graph(List.of(requirementNode("n1", List.of(mutated), List.of())), List.of());

        assertNotEquals(logicalSemanticProjection(build(baseGraph)), logicalSemanticProjection(build(mutatedGraph)),
                "C8-T24 " + fieldName + " mutation changes logical typed semantic model");
        assertNotEquals(build(baseGraph).digest(), build(mutatedGraph).digest(),
                "C8-T24 " + fieldName + " mutation changes logical digest");
        assertNotEquals(plan(baseGraph, "pep-c8-capability-a").digest(),
                plan(mutatedGraph, "pep-c8-capability-b").digest(),
                "C8-T24 " + fieldName + " mutation changes physical digest");
    }

    private static void assertOutputMutationChangesDigests(
            RenderOutputRequirement base,
            RenderOutputRequirement mutated,
            String fieldName) {
        var baseGraph = graph(List.of(nodeWithOutputs("n1", List.of(base))), List.of());
        var mutatedGraph = graph(List.of(nodeWithOutputs("n1", List.of(mutated))), List.of());

        assertNotEquals(logicalSemanticProjection(build(baseGraph)), logicalSemanticProjection(build(mutatedGraph)),
                "C8-T25 " + fieldName + " mutation changes logical typed semantic model");
        assertNotEquals(build(baseGraph).digest(), build(mutatedGraph).digest(),
                "C8-T25 " + fieldName + " mutation changes logical digest");
        assertNotEquals(plan(baseGraph, "pep-c8-output-a").digest(),
                plan(mutatedGraph, "pep-c8-output-b").digest(),
                "C8-T25 " + fieldName + " mutation changes physical digest");
    }

    private static void assertDependencyMutationChangesDigests(
            RenderNode producer,
            RenderNode consumer,
            RenderDependencyEdge base,
            RenderDependencyEdge mutated,
            String fieldName) {
        var baseGraph = graph(List.of(producer, consumer), List.of(base));
        var mutatedGraph = graph(List.of(producer, consumer), List.of(mutated));

        assertNotEquals(logicalSemanticProjection(build(baseGraph)), logicalSemanticProjection(build(mutatedGraph)),
                "C8-T27 " + fieldName + " mutation changes logical typed semantic model");
        assertNotEquals(build(baseGraph).digest(), build(mutatedGraph).digest(),
                "C8-T27 " + fieldName + " mutation changes logical digest");
        assertNotEquals(plan(baseGraph, "pep-c8-dependency-a").digest(),
                plan(mutatedGraph, "pep-c8-dependency-b").digest(),
                "C8-T27 " + fieldName + " mutation changes physical digest");
    }

    private static RenderNode node(String id) {
        return new RenderNode(
                new RenderNodeId(id),
                new RenderNodeKind.Decode(),
                RenderComponentPath.of(RenderComponentKind.CLIP, "clip-" + id),
                "decode",
                List.of(), List.of(), List.of(), List.of(), List.of(),
                java.util.Optional.empty(), coverage());
    }

    private static RenderNode validatedNodeWithDeclarations(
            String id,
            RenderNodeKind kind,
            List<CapabilityRequirement> capabilityRequirements,
            List<RenderExecutionRequirement> executionRequirements,
            List<RenderOutputRequirement> outputRequirements,
            List<RenderMaterializationRequirement> materializationRequirements) {
        return new RenderNode(
                new RenderNodeId(id),
                kind,
                RenderComponentPath.of(RenderComponentKind.CLIP, "clip-" + id),
                kind instanceof RenderNodeKind.Effect ? "effect" : "decode",
                List.of(),
                capabilityRequirements,
                outputRequirements,
                executionRequirements,
                materializationRequirements,
                Optional.empty(),
                coverage());
    }

    private static RenderNode nodeWithArtifacts(
            String id,
            List<com.example.platform.render.domain.renderplan.RenderArtifactReference> artifactReferences) {
        return new RenderNode(
                new RenderNodeId(id),
                new RenderNodeKind.Decode(),
                RenderComponentPath.of(RenderComponentKind.CLIP, "clip-" + id),
                "decode",
                artifactReferences, List.of(), List.of(), List.of(), List.of(),
                Optional.empty(), coverage());
    }

    private static RenderNode nodeWithOutputs(String id, List<RenderOutputRequirement> outputRequirements) {
        return new RenderNode(
                new RenderNodeId(id),
                new RenderNodeKind.Decode(),
                RenderComponentPath.of(RenderComponentKind.CLIP, "clip-" + id),
                "decode",
                List.of(), List.of(), outputRequirements, List.of(), List.of(),
                Optional.empty(), coverage());
    }

    private static RenderNode nodeWithMaterializations(
            String id,
            List<RenderMaterializationRequirement> materializationRequirements) {
        return new RenderNode(
                new RenderNodeId(id),
                new RenderNodeKind.Effect(),
                RenderComponentPath.of(RenderComponentKind.CLIP, "clip-" + id),
                "effect",
                List.of(), List.of(), List.of(), List.of(), materializationRequirements,
                Optional.empty(), coverage());
    }

    private static RenderNode validatedNode(String id, RenderNodeKind kind) {
        return new RenderNode(
                new RenderNodeId(id),
                kind,
                RenderComponentPath.of(RenderComponentKind.CLIP, "clip-" + id),
                kind instanceof RenderNodeKind.Effect ? "effect" : "decode",
                List.of(),
                List.of(capability(kind instanceof RenderNodeKind.Effect ? "media.effect" : "media.decode")),
                List.of(), List.of(intent(false)), List.of(),
                Optional.empty(), coverage());
    }

    private static RenderNode requirementNode(
            String id,
            List<CapabilityRequirement> capabilityRequirements,
            List<RenderExecutionRequirement> executionRequirements) {
        return new RenderNode(
                new RenderNodeId(id),
                new RenderNodeKind.Decode(),
                RenderComponentPath.of(RenderComponentKind.CLIP, "clip-" + id),
                "decode",
                List.of(), capabilityRequirements, List.of(), executionRequirements, List.of(),
                java.util.Optional.empty(), coverage());
    }

    private static com.example.platform.render.domain.renderplan.RenderArtifactReference.SourceArtifact sourceArtifact(
            String id, String digestHex) {
        return new com.example.platform.render.domain.renderplan.RenderArtifactReference.SourceArtifact(
                new ArtifactId(id),
                new ContentDigest(DigestAlgorithm.SHA_256, digestHex));
    }

    private static CapabilityRequirement capability(String id) {
        return new CapabilityRequirement(
                CapabilityId.of(id),
                ContractVersionRange.exactly(ContractVersion.of(1, 0)),
                true,
                List.of());
    }

    private static RenderExecutionRequirement intent(boolean sandboxed) {
        return new RenderExecutionRequirement(
                sandboxed ? GpuRequirement.OPTIONAL : GpuRequirement.NONE,
                sandboxed ? RenderDeterminismClass.CONDITIONALLY_DETERMINISTIC
                        : RenderDeterminismClass.DETERMINISTIC,
                sandboxed);
    }

    private static EffectMaterializationRequirement materialization(
            String instanceId,
            String definitionId,
            EffectInstance.EffectCategory category) {
        return new EffectMaterializationRequirement(
                category,
                List.of(),
                instanceId,
                definitionId,
                "v1",
                true,
                new MediaClip.TimeRange(MediaTime.ofMillis(0), MediaTime.ofMillis(1000)),
                List.of(),
                EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                new ClipEffectTarget("track-1", "clip-" + instanceId));
    }

    private static EffectMaterializationRequirement materializationWithParameter(String key, String value) {
        return new EffectMaterializationRequirement(
                EffectInstance.EffectCategory.COLOR_ADJUSTMENT,
                List.of(new EffectMaterializationRequirement.EffectParameter(key, value)),
                "inst-parameter",
                "def-parameter",
                "v1",
                true,
                new MediaClip.TimeRange(MediaTime.ofMillis(0), MediaTime.ofMillis(1000)),
                List.of(),
                EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                new ClipEffectTarget("track-1", "clip-inst-parameter"));
    }

    private static RenderOutputRequirement outputRequirement(
            RenderOutputRole role,
            com.example.platform.colorimage.ColorDescription color,
            com.example.platform.colorimage.RasterSampleDescription raster) {
        return new RenderOutputRequirement(role, Optional.of(color), Optional.of(raster));
    }

    private static com.example.platform.colorimage.ColorDescription bt709() {
        return new com.example.platform.colorimage.ColorDescription.ParametricColorDescription(
                com.example.platform.colorimage.ColorPrimaries.WellKnown.BT709,
                com.example.platform.colorimage.TransferCharacteristic.BT709,
                com.example.platform.colorimage.MatrixCoefficients.BT709,
                com.example.platform.colorimage.SignalRange.LIMITED);
    }

    private static com.example.platform.colorimage.ColorDescription bt2020() {
        return new com.example.platform.colorimage.ColorDescription.ParametricColorDescription(
                com.example.platform.colorimage.ColorPrimaries.WellKnown.BT2020,
                com.example.platform.colorimage.TransferCharacteristic.PQ,
                com.example.platform.colorimage.MatrixCoefficients.BT2020_NCL,
                com.example.platform.colorimage.SignalRange.LIMITED);
    }

    private static com.example.platform.colorimage.RasterSampleDescription raster8bit() {
        return com.example.platform.colorimage.RasterSampleDescription.ycbcr(
                8, com.example.platform.colorimage.ChromaSubsampling.SAMPLE_420);
    }

    private static com.example.platform.colorimage.RasterSampleDescription raster10bit() {
        return com.example.platform.colorimage.RasterSampleDescription.ycbcr(
                10, com.example.platform.colorimage.ChromaSubsampling.SAMPLE_420);
    }

    private static RenderPlan renderPlan(List<RenderNode> nodes, List<RenderDependencyEdge> edges) {
        var effectRef = new EffectSemanticReference(
                new EffectSemanticSnapshotReference(
                        EffectSemanticSnapshotId.of("snap-c8"),
                        "c".repeat(64),
                        EffectSemanticContractVersion.of("v1")),
                "rev-c8");
        return new RenderPlan(
                new RenderPlanId("plan-c8"),
                "render-plan-v1",
                new TimelineRevisionReference("rev-c8",
                        new ContentDigest(DigestAlgorithm.SHA_256, "d".repeat(64))),
                effectRef,
                new RenderRequest(new RenderRequestId("req-c8"), EXTENT, List.of()),
                nodes,
                edges,
                new RenderPlanFingerprint("fp-c8"),
                new RenderPlanProvenance("render-plan-v1", "rev-c8", effectRef));
    }

    private static RenderPlanningInput renderPlanningInput(TextElement text, boolean sourceFailed) {
        var document = timelineDocument(text);
        var digester = new TimelineContentDigester();
        String timelineDigest = digester.digest(document);
        var effectSnapshot = new EffectSemanticSnapshotAuthority(
                new EffectDefinitionVersionRegistry.InMemory(),
                new EffectSemanticSnapshotStore.InMemory()).mintEmpty();
        String revisionSemanticDigest =
                com.example.platform.timeline.semantics.effect.TimelineRevisionEffectSemanticCommitment
                        .revisionEffectSemanticDigest(timelineDigest, effectSnapshot.reference());
        var revision = new TimelineRevision(
                REVISION_ID,
                "product-c8",
                null,
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                document,
                revisionSemanticDigest,
                Instant.EPOCH,
                "c8",
                new TimelineRevisionSemanticContext(
                        timelineDigest,
                        effectSnapshot.reference(),
                        revisionSemanticDigest,
                        TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1));
        var authoredSnapshot = VerifiedRenderSemanticSnapshotFactory.verified(
                revision, digester, effectSnapshot);
        RenderSourceResolutionState sourceState = sourceFailed
                ? RenderSourceResolutionState.FAILED
                : RenderSourceResolutionState.RESOLVED;
        return new RenderPlanningInput(
                authoredSnapshot,
                new RenderRequest(
                        new RenderRequestId("req-c8"),
                        new RenderExtent(MediaTime.ofRational(0, 1),
                                MediaTime.ofRational(2, 1), FrameRate.of(30, 1)),
                        List.of(RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER))),
                new SourceResolutionInput(Map.of(new ArtifactId(ARTIFACT_ID), sourceState)),
                new CapabilityContext(Set.of(
                        CapabilityId.of("video.decode"),
                        CapabilityId.of("subtitle.rasterize"),
                        CapabilityId.of("render.composite"),
                        CapabilityId.of("render.output"))));
    }

    private static TimelineDocument timelineDocument(TextElement text) {
        return new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack(
                        TRACK_ID,
                        "track-c8",
                        TrackType.VIDEO,
                        List.of(new TimelineClip(
                                CLIP_ID,
                                "asset-c8",
                                "stream-c8",
                                ARTIFACT_ID,
                                ARTIFACT_DIGEST_HEX,
                                MediaTime.ofRational(0, 1),
                                MediaTime.ofRational(2, 1),
                                MediaTime.ofRational(0, 1),
                                MediaTime.ofRational(2, 1),
                                "MEDIA_STREAM",
                                ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD))))),
                TimelineMetadata.empty(),
                AudioMix.EMPTY,
                List.of(),
                List.of(text));
    }

    private static TextElement textElement() {
        TextContent content = new TextContent("Hello C8");
        FontContentDigest digest = FontContentDigest.ofText("inter-c8");
        ValidatedFontExecutionReference ref = new ValidatedFontExecutionReference(
                digest, digest, FontSecurityState.VALIDATED_EXECUTION_FONT,
                FontFormat.TRUETYPE, new FaceIndex(0));
        ResolvedFontInstance font = new ResolvedFontInstance(ref, List.of());
        StyledText styled = new StyledText(
                content,
                List.of(new TextSemanticRun(TextRange.of(0, content.scalarCount()),
                        null, ScriptTag.LATIN, RangeDirectionOverride.NONE)),
                List.of(new com.example.platform.fonttext.typography.TextStyleRun(
                        TextRange.of(0, content.scalarCount()), sampleStyle())),
                new ParagraphStyle(ParagraphStyle.Alignment.START, ParagraphStyle.Justification.NONE,
                        LineHeight.ratio(FontRational.of(12, 10)),
                        ParagraphStyle.WrapPolicy.WRAP, ParagraphBaseDirection.AUTO,
                        ParagraphStyle.LineBreakPolicy.STANDARD));
        return new TextElement(
                new TextElementId(TEXT_ELEMENT_ID),
                FontRational.whole(0),
                FontRational.whole(5),
                styled,
                new TextFrame(FontRational.of(640, 1), null,
                        TextFrame.HorizontalAlignment.START,
                        TextFrame.VerticalAlignment.TOP,
                        ParagraphStyle.WrapPolicy.WRAP,
                        TextFrame.OverflowBehavior.CLIP),
                new FontFallbackPolicy(List.of(new FontFamilyName("Arial")), List.of(), List.of(), List.of()),
                List.of(new ResolvedFontRun(TextRange.of(0, content.scalarCount()), font)));
    }

    private static com.example.platform.fonttext.typography.TextStyle sampleStyle() {
        return new com.example.platform.fonttext.typography.TextStyle(
                new FontSelectionIntent(List.of(new FontFamilyName("Inter")),
                        FontSelectionIntent.WeightIntent.NORMAL,
                        FontSelectionIntent.StretchIntent.NORMAL,
                        FontSelectionIntent.SlantIntent.NORMAL,
                        OpticalSizingIntent.disabled(),
                        List.of()),
                new FontSize(FontRational.of(24, 1)),
                FontRational.of(0, 1),
                OpenTypeFeatureIntent.empty());
    }

    private static Path repoRoot() {
        Path p = Path.of(System.getProperty("user.dir"));
        while (p != null && !Files.exists(p.resolve(".git"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("repo root not found");
        }
        return p;
    }

    private static List<Path> productionJavaSources() throws IOException {
        Path root = repoRoot();
        List<Path> result = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> isProductionJavaSource(root.relativize(path)))
                    .forEach(result::add);
        }
        return List.copyOf(result);
    }

    private static boolean isProductionJavaSource(Path relativePath) {
        boolean inMainJava = false;
        int names = relativePath.getNameCount();
        for (int i = 0; i < names; i++) {
            String name = relativePath.getName(i).toString();
            if (name.equals(".git") || name.equals(".worktrees") || name.equals("build")
                    || name.equals("generated") || name.equals("generated-sources")) {
                return false;
            }
            if (i + 3 < names
                    && name.equals("src")
                    && relativePath.getName(i + 1).toString().equals("main")
                    && relativePath.getName(i + 2).toString().equals("java")) {
                inMainJava = true;
            }
        }
        return inMainJava;
    }

    private static String stripComments(String src) {
        String s = src.replaceAll("(?s)/\\*.*?\\*/", " ");
        return s.replaceAll("(?m)//.*$", " ");
    }

    private static RenderExecutionCoverage coverage() {
        return new RenderExecutionCoverage(
                MediaTime.ofMillis(0), MediaTime.ofMillis(10000), FrameRate.of(25, 1));
    }
}
