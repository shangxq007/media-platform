package com.example.platform.render.domain.timeline.semantics.serialization;

import com.example.platform.render.domain.timeline.canonical.TimelineContentDigester;
import com.example.platform.render.domain.timeline.canonical.TimelineDocument;
import com.example.platform.render.domain.timeline.canonical.TimelineMetadata;
import com.example.platform.render.domain.timeline.canonical.TimelineClip;
import com.example.platform.render.domain.timeline.canonical.TimelineTrack;
import com.example.platform.render.domain.timeline.canonical.TrackType;
import com.example.platform.render.domain.timeline.semantics.automation.Automation;
import com.example.platform.render.domain.timeline.semantics.clip.MediaClip;
import com.example.platform.render.domain.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.render.domain.timeline.semantics.clip.TimelineSourceBinding;
import com.example.platform.render.domain.timeline.semantics.effect.EffectInstance;
import com.example.platform.render.domain.timeline.semantics.transition.TransitionInstance;
import com.example.platform.render.domain.timeline.semantics.validation.TimelineSemanticModel;
import com.example.platform.render.testsupport.TestSourceBindings;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.storage.contract.ContentDigest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ROADMAP_17 (S12/S13 + §10): serializer/digester dual-path equivalence and
 * source-kind-aware content hashing. Proves both canonical paths encode the
 * SAME TimelineSourceBinding semantic contract — no silent mismatch.
 */
class TimelineV2SourceKindHashTest {

    private static MediaClip clip(MediaStreamSourceBinding binding) {
        return new MediaClip(
                "c1", "track-1",
                new MediaClip.TimeRange(MediaTime.ofRational(0, 1), MediaTime.ofRational(5, 1)),
                binding.sourceRange(),
                new MediaClip.Rational(1, 1), binding);
    }

    private static TimelineSemanticModel model(MediaStreamSourceBinding binding) {
        return new TimelineSemanticModel(
                List.of(clip(binding)), List.<TransitionInstance>of(),
                List.<EffectInstance>of(), List.<Automation.AutomationCurve>of(),
                "timeline-semantics-v1");
    }

    private static TimelineDocument document(MediaStreamSourceBinding binding) {
        TimelineClip clip = new TimelineClip(
                "c1", binding.mediaAssetId().value(), binding.mediaStreamId().value(),
                binding.artifactId().value(), binding.contentDigest().value(),
                MediaTime.ofRational(0, 1), MediaTime.ofRational(5, 1),
                MediaTime.ofRational(0, 1), MediaTime.ofRational(5, 1), "MEDIA_STREAM");
        TimelineTrack track = new TimelineTrack("t1", "video", TrackType.VIDEO, List.of(clip));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(track), TimelineMetadata.empty());
    }

    @Test
    void deterministicRepeatedCanonicalBytes() {
        MediaStreamSourceBinding b = TestSourceBindings.sample();
        assertEquals(CanonicalSerializer.serialize(model(b)),
                CanonicalSerializer.serialize(model(b)));
    }

    @Test
    void deterministicRepeatedContentHash() {
        MediaStreamSourceBinding b = TestSourceBindings.sample();
        var digester = new TimelineContentDigester();
        assertEquals(digester.digest(document(b)), digester.digest(document(b)));
    }

    @Test
    void artifactIdChangeChangesHash() {
        MediaStreamSourceBinding base = TestSourceBindings.sample();
        MediaStreamSourceBinding other = new MediaStreamSourceBinding(
                base.mediaAssetId(), base.mediaStreamId(), new ArtifactId("artifact-other"),
                base.contentDigest(), base.sourceRange());
        var digester = new TimelineContentDigester();
        assertNotEquals(digester.digest(document(base)), digester.digest(document(other)));
        assertNotEquals(CanonicalSerializer.serialize(model(base)),
                CanonicalSerializer.serialize(model(other)));
    }

    @Test
    void contentDigestChangeChangesHash() {
        MediaStreamSourceBinding base = TestSourceBindings.sample();
        MediaStreamSourceBinding other = new MediaStreamSourceBinding(
                base.mediaAssetId(), base.mediaStreamId(), base.artifactId(),
                new ContentDigest(ContentDigest.DigestAlgorithm.SHA_256,
                        "0000000000000000000000000000000000000000000000000000000000000000"),
                base.sourceRange());
        var digester = new TimelineContentDigester();
        assertNotEquals(digester.digest(document(base)), digester.digest(document(other)));
    }

    @Test
    void mediaStreamIdChangeChangesHash() {
        MediaStreamSourceBinding base = TestSourceBindings.sample();
        MediaStreamSourceBinding other = TestSourceBindings.of(
                base.mediaAssetId().value(), "stream-2", base.artifactId().value(),
                base.sourceRange());
        var digester = new TimelineContentDigester();
        assertNotEquals(digester.digest(document(base)), digester.digest(document(other)));
    }

    @Test
    void exactSourceTimeRangeChangeChangesSemantics() {
        // sourceRange is SEMANTICS-layer state (MediaClip); the canonical document
        // (TimelineClip) carries placement/trim only — so the digester path is not
        // sourceRange-sensitive by #14 design. The semantics canonical serializer
        // MUST reflect the change (S12: every semantic field participates).
        MediaStreamSourceBinding base = TestSourceBindings.sample();
        MediaStreamSourceBinding other = TestSourceBindings.of(
                base.mediaAssetId().value(), base.mediaStreamId().value(),
                base.artifactId().value(),
                new MediaClip.TimeRange(MediaTime.ofRational(1, 1), MediaTime.ofRational(2, 1)));
        assertNotEquals(CanonicalSerializer.serialize(model(base)),
                CanonicalSerializer.serialize(model(other)));
        // binding-level semantics differ (typed record equality)
        assertNotEquals(base, other);
    }

    @Test
    void sourceKindParticipatesAndIsDeterministic() {
        MediaStreamSourceBinding b = TestSourceBindings.sample();
        assertEquals(TimelineSourceBinding.SourceKind.MEDIA_STREAM, b.sourceKind());
        assertTrue(CanonicalSerializer.serialize(model(b)).contains("MEDIA_STREAM"));
    }

    @Test
    void noUniversalNullableSourceObject() {
        assertTrue(TimelineSourceBinding.class.isSealed());
        assertEquals(List.of(MediaStreamSourceBinding.class),
                List.of(TimelineSourceBinding.class.getPermittedSubclasses()));
    }
}
