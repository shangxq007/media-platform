package com.example.platform.timeline.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalProfile;
import com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.timeline.semantics.clip.TimelineSourceBinding;
import com.example.platform.timeline.semantics.clip.TimelineSourceBindingCanonicalSemantics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * R5-B (CHECKPOINT_A Round 5): TimelineCandidate carries ONE typed source
 * binding authority — no independent flat semantic state. Flat wire input is
 * canonicalized immediately into the typed binding; partial/invalid binding
 * intent FAILS CLOSED (never catch→null narrowing, never MediaAssetId-only
 * fallback, no SourceBindingV2 dual track).
 */
class CheckpointARound5SourceBindingClosureTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DIGEST_HEX = "a".repeat(64);
    private static final String TENANT = "tenant-r5b";

    private static String internalPayload(boolean nestedBinding, boolean flatBinding) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", "1.0");
        root.put("id", "tl-1");
        ObjectNode composition = MAPPER.createObjectNode();
        ObjectNode track = MAPPER.createObjectNode();
        track.put("id", "v1");
        track.put("type", "VIDEO");
        ObjectNode clip = MAPPER.createObjectNode();
        clip.put("id", "c1");
        clip.put("assetId", "asset-1");
        ObjectNode rate = MAPPER.createObjectNode();
        rate.put("num", 30L);
        rate.put("den", 1L);
        ObjectNode start = MAPPER.createObjectNode();
        start.put("frame", 0L);
        start.set("rate", rate);
        ObjectNode duration = MAPPER.createObjectNode();
        duration.put("frame", 30L);
        duration.set("rate", rate);
        ObjectNode range = MAPPER.createObjectNode();
        range.set("start", start);
        range.set("duration", duration);
        clip.set("timelineRange", range);
        clip.set("sourceRange", range);
        if (nestedBinding) {
            ObjectNode sb = MAPPER.createObjectNode();
            sb.put("sourceKind", "MEDIA_STREAM");
            sb.put("mediaAssetId", "asset-1");
            sb.put("mediaStreamId", "stream-1");
            sb.put("artifactId", "art-1");
            ObjectNode digestNode = MAPPER.createObjectNode();
            digestNode.put("algorithm", "SHA256");
            digestNode.put("value", DIGEST_HEX);
            sb.set("contentDigest", digestNode);
            sb.put("sourceRangeStart", "0/1");
            sb.put("sourceRangeEnd", "1/1");
            clip.set("sourceBinding", sb);
        } else if (flatBinding) {
            clip.put("sourceKind", "MEDIA_STREAM");
            clip.put("mediaStreamId", "stream-1");
            clip.put("artifactId", "art-1");
            clip.put("contentDigest", DIGEST_HEX);
        }
        track.set("clips", MAPPER.createArrayNode().add(clip));
        composition.set("tracks", MAPPER.createArrayNode().add(track));
        root.set("composition", composition);
        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static TimelineCandidate map(String payload) {
        com.example.platform.shared.web.TenantContext.set(TENANT);
        try {
            return InternalTimelineCandidateAdapter.map("prod-1", payload);
        } finally {
            com.example.platform.shared.web.TenantContext.clear();
        }
    }

    @Test
    void nestedTypedBindingSurvivesCandidateConstruction() {
        TimelineCandidate candidate = map(internalPayload(true, false));
        TimelineSourceBinding binding = candidate.tracks().get(0).clips().get(0).sourceBinding();
        assertNotNull(binding, "typed binding must be constructed from nested sourceBinding");
        assertTrue(binding instanceof MediaStreamSourceBinding, "MEDIA_STREAM typed binding");
        MediaStreamSourceBinding m = (MediaStreamSourceBinding) binding;
        assertEquals("art-1", m.artifactId().value());
        assertEquals("stream-1", m.mediaStreamId().value());
        assertEquals(DIGEST_HEX, m.contentDigest().value());
        assertEquals(MediaTime.ofTicks(0, 1), m.sourceRange().start());
        assertEquals(MediaTime.ofTicks(1, 1), m.sourceRange().end());
    }

    @Test
    void flatWireFieldsCanonicalizedImmediatelyToTypedBinding() {
        TimelineCandidate candidate = map(internalPayload(false, true));
        TimelineSourceBinding binding = candidate.tracks().get(0).clips().get(0).sourceBinding();
        assertNotNull(binding, "flat wire fields must be canonicalized immediately into the typed binding");
        assertTrue(binding instanceof MediaStreamSourceBinding, "typed MEDIA_STREAM binding");
        MediaStreamSourceBinding m = (MediaStreamSourceBinding) binding;
        assertEquals("art-1", m.artifactId().value(), "flat artifactId canonicalized");
        assertEquals("stream-1", m.mediaStreamId().value(), "flat mediaStreamId canonicalized");
        assertEquals(DIGEST_HEX, m.contentDigest().value(), "flat digest canonicalized");
    }

    @Test
    void noSourceBindingIntentYieldsNullBinding() {
        TimelineCandidate candidate = map(internalPayload(false, false));
        assertNull(candidate.tracks().get(0).clips().get(0).sourceBinding(),
                "no authored binding intent → null binding is valid");
    }

    @Test
    void partialFlatBindingFailsClosed() {
        // artifactId present but mediaStreamId/digest missing → partial intent
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", "1.0");
        root.put("id", "tl-1");
        ObjectNode composition = MAPPER.createObjectNode();
        ObjectNode track = MAPPER.createObjectNode();
        track.put("id", "v1");
        track.put("type", "VIDEO");
        ObjectNode clip = MAPPER.createObjectNode();
        clip.put("id", "c1");
        clip.put("assetId", "asset-1");
        clip.put("sourceKind", "MEDIA_STREAM");
        clip.put("artifactId", "art-1"); // partial: no stream, no digest
        ObjectNode rate = MAPPER.createObjectNode();
        rate.put("num", 30L);
        rate.put("den", 1L);
        ObjectNode start = MAPPER.createObjectNode();
        start.put("frame", 0L);
        start.set("rate", rate);
        ObjectNode duration = MAPPER.createObjectNode();
        duration.put("frame", 30L);
        duration.set("rate", rate);
        ObjectNode range = MAPPER.createObjectNode();
        range.set("start", start);
        range.set("duration", duration);
        clip.set("timelineRange", range);
        clip.set("sourceRange", range);
        track.set("clips", MAPPER.createArrayNode().add(clip));
        composition.set("tracks", MAPPER.createArrayNode().add(track));
        root.set("composition", composition);
        assertThrows(TimelineCanonicalRejectionException.class,
                () -> map(MAPPER.writeValueAsString(root)),
                "partial flat binding must fail closed (no silent null)");
    }

    @Test
    void unknownSourceKindFailsClosed() {
        String payload = internalPayload(true, false)
                .replace("\"sourceKind\":\"MEDIA_STREAM\"", "\"sourceKind\":\"SCENE\"");
        assertThrows(TimelineCanonicalRejectionException.class, () -> map(payload),
                "unknown sourceKind must fail closed");
    }

    @Test
    void malformedDigestFailsClosed() {
        String payload = internalPayload(true, false)
                .replace("\"value\":\"" + DIGEST_HEX + "\"", "\"value\":\"not-a-digest\"");
        assertThrows(TimelineCanonicalRejectionException.class, () -> map(payload),
                "malformed digest must fail closed");
    }

    @Test
    void missingArtifactIdFailsClosed() {
        try {
            ObjectNode root = (ObjectNode) MAPPER.readTree(internalPayload(true, false));
            ((ObjectNode) root.path("composition").path("tracks").get(0).path("clips").get(0)
                    .path("sourceBinding")).remove("artifactId");
            assertThrows(TimelineCanonicalRejectionException.class,
                    () -> map(MAPPER.writeValueAsString(root)),
                    "missing artifactId must fail closed");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void missingMediaStreamIdFailsClosed() {
        try {
            ObjectNode root = (ObjectNode) MAPPER.readTree(internalPayload(true, false));
            ((ObjectNode) root.path("composition").path("tracks").get(0).path("clips").get(0)
                    .path("sourceBinding")).remove("mediaStreamId");
            assertThrows(TimelineCanonicalRejectionException.class,
                    () -> map(MAPPER.writeValueAsString(root)),
                    "missing mediaStreamId must fail closed");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void missingSourceRangeFailsClosed() {
        try {
            ObjectNode root = (ObjectNode) MAPPER.readTree(internalPayload(true, false));
            ((ObjectNode) root.path("composition").path("tracks").get(0).path("clips").get(0)
                    .path("sourceBinding")).remove("sourceRangeStart");
            assertThrows(TimelineCanonicalRejectionException.class,
                    () -> map(MAPPER.writeValueAsString(root)),
                    "missing source range must fail closed (no zero-range synthesis)");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void sourceKindAloneIsNotBindingIntent() {
        // A legacy clip carrying ONLY sourceKind (no stream/artifact/digest,
        // no binding-specific fields) is not a binding intent — null binding,
        // NOT fail-closed.
        try {
            ObjectNode root = (ObjectNode) MAPPER.readTree(internalPayload(true, false));
            ((ObjectNode) root.path("composition").path("tracks").get(0).path("clips").get(0))
                    .remove("sourceBinding");
            TimelineCandidate candidate = map(MAPPER.writeValueAsString(root));
            assertNull(candidate.tracks().get(0).clips().get(0).sourceBinding(),
                    "lone sourceKind is a legacy projection, not binding intent");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void exactDigestAlgorithmAndValuePreserved() {
        TimelineCandidate candidate = map(internalPayload(true, false));
        MediaStreamSourceBinding m = (MediaStreamSourceBinding) candidate.tracks().get(0).clips().get(0).sourceBinding();
        assertEquals("SHA_256", m.contentDigest().algorithm().name(), "algorithm preserved");
        assertEquals(DIGEST_HEX, m.contentDigest().value(), "exact value preserved");
        // canonical round-trip through the authority
        JsonNode canonical = TimelineSourceBindingCanonicalSemantics.canonicalValue(m);
        TimelineSourceBinding decoded = TimelineSourceBindingCanonicalSemantics.fromCanonicalValue(canonical);
        assertEquals(m, decoded, "typed binding canonical round-trip is lossless");
    }

    @Test
    void candidateHasSingleTypedSourceAuthority() {
        // R5-B: TimelineCandidate.Clip no longer carries flat sourceKind/
        // mediaAssetId/mediaStreamId/artifactId/contentDigest fields — the
        // typed binding is the ONLY source-semantics representation.
        TimelineCandidate candidate = map(internalPayload(true, false));
        var clip = candidate.tracks().get(0).clips().get(0);
        // Compile-time proof: Clip has sourceBinding() (typed) and temporalMapping().
        assertNotNull(clip.sourceBinding());
        // No flat source accessors exist anymore — the record components are
        // clipId, sourceRef, timelineStart, sourceStart, duration, rate,
        // effects, unsupportedConstructs, temporalMapping, sourceBinding.
        assertEquals(10, TimelineCandidate.Clip.class.getRecordComponents().length,
                "Clip must carry exactly 10 components (no flat source fields)");
        List<String> names = java.util.Arrays.stream(TimelineCandidate.Clip.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();
        assertTrue(!names.contains("sourceKind") && !names.contains("mediaAssetId")
                        && !names.contains("mediaStreamId") && !names.contains("artifactId")
                        && !names.contains("contentDigest"),
                "no flat source semantic fields may exist on TimelineCandidate.Clip: " + names);
    }

    @Test
    void noMediaAssetIdOnlyFallbackInFlatPath() {
        // flat intent with ONLY mediaAssetId (no stream/artifact/digest) must
        // fail closed — never a MediaAssetId-only binding.
        String payload = internalPayload(true, false)
                .replace("\"artifactId\":\"art-1\",", "")
                .replace("\"mediaStreamId\":\"stream-1\",", "")
                .replace("\"contentDigest\":{\"algorithm\":\"SHA256\",\"value\":\"" + DIGEST_HEX + "\"},", "")
                .replace("\"sourceBinding\":{\"sourceKind\":\"MEDIA_STREAM\",\"mediaAssetId\":\"asset-1\",\"mediaStreamId\":\"stream-1\",\"artifactId\":\"art-1\",\"contentDigest\":{\"algorithm\":\"SHA256\",\"value\":\"" + DIGEST_HEX + "\"},\"sourceRangeStart\":\"0/1\",\"sourceRangeEnd\":\"1/1\"},", "");
        // Now clip has NO binding fields at all → no intent. Construct a clip
        // with ONLY mediaAssetId intent via flat assetId:
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", "1.0");
        root.put("id", "tl-1");
        ObjectNode composition = MAPPER.createObjectNode();
        ObjectNode track = MAPPER.createObjectNode();
        track.put("id", "v1");
        track.put("type", "VIDEO");
        ObjectNode clip = MAPPER.createObjectNode();
        clip.put("id", "c1");
        clip.put("assetId", "asset-1");
        clip.put("sourceKind", "MEDIA_STREAM");
        clip.put("mediaAssetId", "asset-1"); // asset-only intent
        ObjectNode rate = MAPPER.createObjectNode();
        rate.put("num", 30L);
        rate.put("den", 1L);
        ObjectNode start = MAPPER.createObjectNode();
        start.put("frame", 0L);
        start.set("rate", rate);
        ObjectNode duration = MAPPER.createObjectNode();
        duration.put("frame", 30L);
        duration.set("rate", rate);
        ObjectNode range = MAPPER.createObjectNode();
        range.set("start", start);
        range.set("duration", duration);
        clip.set("timelineRange", range);
        clip.set("sourceRange", range);
        track.set("clips", MAPPER.createArrayNode().add(clip));
        composition.set("tracks", MAPPER.createArrayNode().add(track));
        root.set("composition", composition);
        assertThrows(TimelineCanonicalRejectionException.class,
                () -> map(MAPPER.writeValueAsString(root)),
                "asset-only flat binding intent must fail closed (no MediaAssetId-only fallback)");
    }
}
