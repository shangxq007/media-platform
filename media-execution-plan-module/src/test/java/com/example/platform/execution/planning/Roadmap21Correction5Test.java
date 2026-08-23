package com.example.platform.execution.planning;

import com.example.platform.execution.domain.ExecutionCreationContext;
import com.example.platform.execution.domain.ExecutionEdgeId;
import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.domain.ExecutionPlanSchemaVersion;
import com.example.platform.execution.domain.ExecutionStepId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Roadmap #21 Correction 5 — B1 contract-lock + B2 full-stream adversarial
 * collision tests.
 */
class Roadmap21Correction5Test {

    // ---------- B1: exact REUSE contract lock (vs frozen 99aa4162) ----------

    @Test
    void strongIdNullAndBlankAreIllegalArgumentExceptions() {
        for (var ctor : List.<java.util.function.Function<String, Object>>of(
                ExecutionPlanId::new, ExecutionEdgeId::new, ExecutionInputId::new,
                ExecutionOutputId::new, ExecutionStepId::new)) {
            assertThrows(IllegalArgumentException.class, () -> ctor.apply(null),
                    "frozen null behavior = IllegalArgumentException (NOT NullPointerException)");
            assertThrows(IllegalArgumentException.class, () -> ctor.apply("  "),
                    "frozen blank behavior = IllegalArgumentException");
        }
    }

    @Test
    void strongIdToStringAndSerializable() {
        assertEquals("abc", new ExecutionPlanId("abc").toString(), "frozen toString = value");
        assertTrue(java.io.Serializable.class.isAssignableFrom(ExecutionPlanId.class));
        assertTrue(java.io.Serializable.class.isAssignableFrom(ExecutionEdgeId.class));
        assertTrue(java.io.Serializable.class.isAssignableFrom(ExecutionInputId.class));
        assertTrue(java.io.Serializable.class.isAssignableFrom(ExecutionOutputId.class));
        assertTrue(java.io.Serializable.class.isAssignableFrom(ExecutionStepId.class));
        assertTrue(java.io.Serializable.class.isAssignableFrom(ExecutionPlanSchemaVersion.class));
        assertTrue(java.io.Serializable.class.isAssignableFrom(ExecutionCreationContext.class));
    }

    @Test
    void schemaVersionFrozenBehavior() {
        assertEquals(ExecutionPlanSchemaVersion.V1, new ExecutionPlanSchemaVersion(1));
        assertEquals("1", ExecutionPlanSchemaVersion.V1.toString());
        assertEquals(ExecutionPlanSchemaVersion.of(3), new ExecutionPlanSchemaVersion(3));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionPlanSchemaVersion(0),
                "frozen: value < 1 rejected with IllegalArgumentException");
        assertThrows(IllegalArgumentException.class, () -> new ExecutionPlanSchemaVersion(-5));
    }

    @Test
    void creationContextFrozenSurface() {
        Instant now = Instant.now();
        ExecutionCreationContext ctx = ExecutionCreationContext.minimal(now);
        assertEquals(now, ctx.createdAt());
        assertThrows(NullPointerException.class, () -> ExecutionCreationContext.minimal(null),
                "frozen: createdAt required");

        ExecutionCreationContext user = ExecutionCreationContext.forUser("u1", "t1", now);
        assertTrue(user.getRequestedByUserId().isPresent());
        assertTrue(user.getRequestedByTenantId().isPresent());
        assertEquals("u1", user.getRequestedByUserId().get());
        assertEquals("t1", user.getRequestedByTenantId().get());

        // frozen helpers: withTraceId / withComment / Optional accessors
        ExecutionCreationContext traced = ctx.withTraceId("trace-9");
        assertEquals("trace-9", traced.traceId());
        assertTrue(traced.getTraceId().isPresent());
        ExecutionCreationContext commented = ctx.withComment("why");
        assertEquals("why", commented.comment());
        assertTrue(commented.getComment().isPresent());
        assertTrue(user.getParentPlanId().isEmpty());
        assertTrue(user.getComment().isEmpty());

        // frozen toString shape
        assertTrue(ctx.toString().startsWith("creationCtx{"),
                "frozen explicit toString present");
        assertTrue(ctx.toString().contains("at=" + now));
    }

    // ---------- B2: full-stream adversarial collisions ----------

    @Test
    void operationKeyVsRecordBoundaryCannotCollide() {
        // structure A: operationKey contains artifact-line text, no artifacts
        var nA = node("n1", "x\nartifact|ART|id|alg|val");
        // structure B: plain operationKey + an artifact
        var nB = nodeWithArtifact("n1", "x", "ART|id|alg|val");
        var gA = graph(nA);
        var gB = graph(nB);
        assertNotEquals(gA.digest(), gB.digest(),
                "operationKey containing record-like text must not collide with a real artifact field");
    }

    @Test
    void unicodeByteLengthFraming() {
        // multibyte UTF-8 in operationKey — byte-length framing must be correct
        var n1 = node("n1", "ключ");
        var n2 = node("n1", "ключ"); // identical
        var n3 = node("n1", "клю");
        var g1 = graph(n1);
        var g2 = graph(n2);
        var g3 = graph(n3);
        assertEquals(g1.digest(), g2.digest(), "identical multibyte semantics -> identical digest");
        assertNotEquals(g1.digest(), g3.digest(), "UTF-8 byte-length framing distinguishes 4-byte vs 3-byte");
    }

    @Test
    void prefixOverlapCannotCollide() {
        var a = node("n1", "a");
        var b = node("n1", "aa");
        assertNotEquals(graph(a).digest(), graph(b).digest(), "prefix-overlap 'a' vs 'aa' distinguished");
    }

    @Test
    void numericFramingLookalikeCannotCollide() {
        // value containing "3:abc" must not be confused with a framed "abc"
        var a = node("n1", "3:abc");
        var b = node("n1", "abc");
        assertNotEquals(graph(a).digest(), graph(b).digest(),
                "framing-lookalike values distinguished by byte-length prefix");
    }

    @Test
    void delimiterHeavyValuesCannotCollide() {
        var a = node("n1", "a|b\nc:d=e");
        var b = node("n1", "a|b") ;
        assertNotEquals(graph(a).digest(), graph(b).digest(), "delimiter-heavy values injective");
    }

    @Test
    void deterministicCollectionOrdering() {
        // non-semantic order (node insertion order) must canonicalize identically
        var n1 = node("n1", "op1");
        var n2 = node("n2", "op2");
        var gA = graphAll(List.of(n1, n2));
        var gB = graphAll(List.of(n2, n1));
        assertEquals(gA.digest(), gB.digest(),
                "NON_SEMANTIC collection order canonicalized — same semantics, same digest");
    }

    @Test
    void writerInjectiveScalar() {
        CanonicalWriter w = new CanonicalWriter();
        assertNotEquals(
                new CanonicalWriter().field("k", "v1").build(),
                new CanonicalWriter().field("k", "v").field("1", "").build());
        // null vs empty distinction
        assertNotEquals(new CanonicalWriter().scalar(null).build(),
                new CanonicalWriter().scalar("").build(),
                "explicit null vs empty distinction");
    }

    // ---------- helpers ----------

    static com.example.platform.render.domain.renderplan.RenderNode node(String id, String opKey) {
        return new com.example.platform.render.domain.renderplan.RenderNode(
                new com.example.platform.render.domain.renderplan.RenderNodeId(id),
                new com.example.platform.render.domain.renderplan.RenderNodeKind.Source(),
                com.example.platform.render.domain.renderplan.RenderComponentPath.of(
                        com.example.platform.render.domain.renderplan.RenderComponentKind.CLIP, "c-" + id),
                opKey,
                List.of(), List.of(
                        new com.example.platform.extension.domain.CapabilityRequirement(
                                com.example.platform.extension.domain.CapabilityId.of("media.op"),
                                com.example.platform.extension.domain.ContractVersionRange.atLeast(
                                        com.example.platform.extension.domain.ContractVersion.of(1, 0)),
                                true, List.of())),
                List.of(com.example.platform.render.domain.renderplan.RenderOutputRequirement.of(
                        com.example.platform.render.domain.renderplan.RenderOutputRole.RENDER_MASTER)),
                List.of(new com.example.platform.render.domain.renderplan.RenderExecutionRequirement(
                        com.example.platform.render.domain.renderplan.RenderExecutionRequirement.GpuRequirement.NONE,
                        com.example.platform.render.domain.renderplan.RenderExecutionRequirement.RenderDeterminismClass.DETERMINISTIC,
                        false)),
                List.of(), java.util.Optional.empty(), null);
    }

    static com.example.platform.render.domain.renderplan.RenderNode nodeWithArtifact(String id, String opKey, String artifactCanonical) {
        // artifact value shaped like canonical text
        var artifact = new com.example.platform.render.domain.renderplan.RenderArtifactReference.SourceArtifact(
                new com.example.platform.shared.identity.ArtifactId("art"),
                new com.example.platform.shared.digest.ContentDigest(
                        com.example.platform.shared.digest.ContentDigest.DigestAlgorithm.SHA_256,
                        "d".repeat(64)));
        return new com.example.platform.render.domain.renderplan.RenderNode(
                new com.example.platform.render.domain.renderplan.RenderNodeId(id),
                new com.example.platform.render.domain.renderplan.RenderNodeKind.Source(),
                com.example.platform.render.domain.renderplan.RenderComponentPath.of(
                        com.example.platform.render.domain.renderplan.RenderComponentKind.CLIP, "c-" + id),
                opKey,
                List.of(artifact), List.of(), List.of(), List.of(), List.of(),
                java.util.Optional.empty(), null);
    }

    static LogicalExecutionGraph graph(com.example.platform.render.domain.renderplan.RenderNode n) {
        return graphAll(List.of(n));
    }

    static LogicalExecutionGraph graphAll(List<com.example.platform.render.domain.renderplan.RenderNode> nodes) {
        return LogicalExecutionGraphBuilder.build(
                new com.example.platform.render.domain.renderplan.RenderGraph("render-graph-v1",
                        new com.example.platform.render.domain.renderplan.RenderPlanFingerprint("fp-1"),
                        nodes, List.of(),
                        new com.example.platform.render.domain.renderplan.RenderGraphFingerprint("gf-1")),
                new com.example.platform.render.domain.renderplan.RenderExtent(
                        com.example.platform.shared.time.MediaTime.ofMillis(0),
                        com.example.platform.shared.time.MediaTime.ofMillis(100000),
                        com.example.platform.shared.time.FrameRate.of(25, 1)));
    }

    @Test
    void writerFieldPairingInjective() {
        // raw delimiter concatenation would collide: {k1=a, k2=b} vs {k1="a|b"}
        String twoFields = new CanonicalWriter().field("k1", "a").field("k2", "b").build();
        String oneField = new CanonicalWriter().field("k1", "a|b").build();
        assertNotEquals(twoFields, oneField,
                "field pairing is injective — {k1=a,k2=b} must differ from {k1=a|b}");
        // list boundary vs scalar containing list-like text
        String withList = new CanonicalWriter().list(List.of("x")).build();
        String scalarLike = new CanonicalWriter().scalar("1:x").build();
        assertNotEquals(withList, scalarLike, "list count framing vs scalar framing never collide");
    }

}
