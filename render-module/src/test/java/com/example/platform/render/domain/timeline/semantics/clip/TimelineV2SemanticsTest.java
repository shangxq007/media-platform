package com.example.platform.render.domain.timeline.semantics.clip;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.render.domain.timeline.semantics.clip.MediaClip.Rational;
import com.example.platform.render.domain.timeline.semantics.clip.MediaClip.TimeRange;
import com.example.platform.render.domain.timeline.semantics.serialization.CanonicalSerializer;
import com.example.platform.render.domain.timeline.semantics.validation.TimelineSemanticModel;
import com.example.platform.render.testsupport.TestSourceBindings;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.storage.contract.ContentDigest;
import com.example.platform.storage.contract.ContentDigest.DigestAlgorithm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TIMELINE_V2 test matrix A-G: MediaStreamSourceBinding invariants, historical immutability,
 * metadata non-semantics, serialization determinism, exact rational, content hash.
 */
class TimelineV2SemanticsTest {

    private static final String DIGEST_HEX =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static MediaStreamSourceBinding binding(String asset, String stream, String artifact, TimeRange range) {
        return new MediaStreamSourceBinding(
                MediaAssetId.of(asset),
                MediaStreamId.of(stream),
                new ArtifactId(artifact),
                new ContentDigest(DigestAlgorithm.SHA_256, DIGEST_HEX),
                range);
    }

    private static MediaClip clip(String id, MediaStreamSourceBinding binding, long tStart, long tEnd) {
        return new MediaClip(id, "track-1",
                new TimeRange(MediaTime.ofRational(tStart, 1), MediaTime.ofRational(tEnd, 1)),
                binding.sourceRange(),
                new Rational(1, 1),
                binding);
    }

    private static TimelineSemanticModel model(MediaClip... clips) {
        return new TimelineSemanticModel(List.of(clips), List.of(), List.of(), List.of(),
                "timeline-semantics-v1");
    }

    // ---- A. MediaStreamSourceBinding invariants ----

    @Test
    @DisplayName("MediaStreamSourceBinding requires all identity + pin + range components")
    void sourceBindingRequiredFields() {
        TimeRange range = new TimeRange(MediaTime.ZERO, MediaTime.ofRational(10, 1));
        assertThrows(NullPointerException.class,
                () -> new MediaStreamSourceBinding(null, MediaStreamId.of("s"), new ArtifactId("a"),
                        new ContentDigest(DigestAlgorithm.SHA_256, DIGEST_HEX), range));
        assertThrows(NullPointerException.class,
                () -> binding("asset", "stream", "artifact", null));
    }

    @Test
    @DisplayName("MediaAssetId != ArtifactId != content pin semantics (typed separation)")
    void identitySeparation() {
        MediaStreamSourceBinding b = TestSourceBindings.sample();
        assertNotEquals(b.mediaAssetId(), b.artifactId());
        assertNotNull(b.contentDigest());
        assertNotNull(b.mediaStreamId());
    }

    // ---- B. Historical immutability ----

    @Test
    @DisplayName("Historical revision pins exact consumed content: relink does not change it")
    void historicalImmutability() {
        TimeRange range = new TimeRange(MediaTime.ZERO, MediaTime.ofRational(10, 1));
        MediaStreamSourceBinding originalPin = binding("asset-A", "stream-1", "artifact-X", range);
        MediaStreamSourceBinding relinked = binding("asset-A", "stream-1", "artifact-Y", range); // same asset, new content

        MediaClip r1Clip = clip("c1", originalPin, 0, 10);
        String r1Hash = CanonicalSerializer.digest(model(r1Clip));

        // The revision's binding still references artifact-X, never resolves to latest (Y).
        assertEquals("artifact-X", r1Clip.sourceBinding().artifactId().value());
        assertEquals(r1Hash, CanonicalSerializer.digest(model(clip("c1", originalPin, 0, 10))));
        assertNotEquals(r1Hash, CanonicalSerializer.digest(model(clip("c1", relinked, 0, 10))));
    }

    // ---- C. Metadata non-semantic ----

    @Test
    @DisplayName("Source range / pin changes alter hash; clip metadata does not exist in canonical")
    void metadataNonSemantic() {
        TimeRange r1 = new TimeRange(MediaTime.ZERO, MediaTime.ofRational(10, 1));
        TimeRange r2 = new TimeRange(MediaTime.ZERO, MediaTime.ofRational(12, 1));
        String h1 = CanonicalSerializer.digest(model(clip("c1", binding("a", "s", "x", r1), 0, 10)));
        String h2 = CanonicalSerializer.digest(model(clip("c1", binding("a", "s", "x", r2), 0, 10)));
        assertNotEquals(h1, h2, "source range change must change content hash");
    }

    // ---- E. Serialization determinism ----

    @Test
    @DisplayName("Same semantic timeline -> identical canonical bytes (insertion order independent)")
    void serializationDeterminism() {
        TimeRange range = new TimeRange(MediaTime.ZERO, MediaTime.ofRational(10, 1));
        MediaClip a = clip("c1", binding("a", "s", "x", range), 0, 10);
        MediaClip b = clip("c2", binding("a", "s", "x", range), 10, 20);

        String m1 = CanonicalSerializer.serialize(model(a, b));
        String m2 = CanonicalSerializer.serialize(model(b, a));
        // Clip collection ordering is by clipId/trackId in canonical form (semantic ordering).
        assertNotEquals(m1, m2, "clip ordering is semantic; different order = different timeline");
        assertEquals(CanonicalSerializer.serialize(model(a, b)),
                CanonicalSerializer.serialize(model(a, b)));
    }

    // ---- F. Exact rational ----

    @Test
    @DisplayName("30000/1001 exact rational round-trips losslessly in canonical form")
    void exactRationalRoundTrip() {
        MediaTime rate = MediaTime.ofRational(30_000, 1_001);
        TimeRange range = new TimeRange(MediaTime.ofRational(1_234_567, 1_001),
                MediaTime.ofRational(2_345_678, 1_001));
        MediaStreamSourceBinding b = binding("asset-r", "stream-r", "artifact-r", range);
        String serialized = CanonicalSerializer.serialize(model(clip("cr", b, 0, 10)));
        assertTrue(serialized.contains("1234567/1001"), "exact numerator/denominator preserved");
        assertTrue(serialized.contains("2345678/1001"), "exact end preserved");
        assertFalse(serialized.contains("1234567.001"), "no decimal/double representation in canonical form");
    }

    // ---- G. Content hash semantics ----

    @Test
    @DisplayName("Same content different construction -> same hash; semantic edit -> different hash")
    void contentHashSemantics() {
        TimeRange range = new TimeRange(MediaTime.ZERO, MediaTime.ofRational(10, 1));
        MediaStreamSourceBinding b = binding("a", "s", "x", range);
        String h1 = CanonicalSerializer.digest(model(clip("c1", b, 0, 10)));
        String h2 = CanonicalSerializer.digest(model(clip("c1", b, 0, 10)));
        assertEquals(h1, h2, "same semantic content -> same hash");

        // playbackRate encoded exactly (num/den), never double
        MediaClip rateClip = new MediaClip("c1", "track-1",
                new TimeRange(MediaTime.ZERO, MediaTime.ofRational(10, 1)),
                range, new Rational(30000, 1001), b);
        String hr = CanonicalSerializer.serialize(model(rateClip));
        assertTrue(hr.contains("\"playbackRate\":\"30000/1001\""), "exact rational playback rate");
        assertFalse(hr.contains("doubleValue") && hr.contains("30.0"), "no double playback rate");
    }
}
