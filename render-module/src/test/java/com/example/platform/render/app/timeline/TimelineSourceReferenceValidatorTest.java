package com.example.platform.render.app.timeline;

import com.example.platform.media.app.MediaAssetRepository;
import com.example.platform.media.app.MediaStreamRepository;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.media.MediaAsset;
import com.example.platform.media.domain.stream.MediaStream;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.media.domain.stream.StreamKind;
import com.example.platform.render.app.timeline.TimelineSourceReferenceValidator.ValidationResult;
import com.example.platform.timeline.semantics.clip.MediaClip.TimeRange;
import com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.storage.contract.ContentDigest;
import com.example.platform.storage.contract.ContentDigest.DigestAlgorithm;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TIMELINE_V2 test matrix J: reference validity (fail closed).
 */
class TimelineSourceReferenceValidatorTest {

    private static final String DIGEST_HEX =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static final MediaAssetId ASSET = MediaAssetId.of("asset-1");
    private static final MediaStreamId STREAM = MediaStreamId.of("stream-1");

    private static MediaStreamSourceBinding binding() {
        return new MediaStreamSourceBinding(ASSET, STREAM, new ArtifactId("artifact-1"),
                new ContentDigest(DigestAlgorithm.SHA_256, DIGEST_HEX),
                new TimeRange(MediaTime.ZERO, MediaTime.ofRational(10, 1)));
    }

    private static final MediaAssetRepository ASSETS = new MediaAssetRepository() {
        @Override public MediaAsset save(MediaAsset asset) { return asset; }
        @Override public Optional<MediaAsset> findById(MediaAssetId id) {
            return id.equals(ASSET) ? Optional.of(new MediaAsset(ASSET, "t", "p", "v1",
                    null, null, null, null, null, false, false, null,
                    java.time.Instant.EPOCH, java.time.Instant.EPOCH)) : Optional.empty();
        }
        @Override public boolean exists(MediaAssetId id) { return id.equals(ASSET); }
    };

    private static final MediaStreamRepository STREAMS = new MediaStreamRepository() {
        @Override public void saveAll(MediaAssetId mediaAssetId, List<MediaStream> streams) {}
        @Override public List<MediaStream> findByMediaAssetId(MediaAssetId mediaAssetId) {
            if (mediaAssetId.equals(ASSET)) {
                return List.of(new MediaStream(STREAM, 0, StreamKind.VIDEO, "h264",
                        new com.example.platform.media.domain.time.TimeBase(1, 1),
                        com.example.platform.shared.time.FrameRate.of(30, 1), false,
                        null, null, null, null));
            }
            return List.of();
        }
        @Override public void deleteByMediaAssetId(MediaAssetId mediaAssetId) {}
    };

    private final TimelineSourceReferenceValidator validator =
            new TimelineSourceReferenceValidator(ASSETS, STREAMS);

    @Test
    void validBindingPasses() {
        ValidationResult r = validator.validate(binding());
        assertTrue(r.valid(), r.violations().toString());
    }

    @Test
    void missingAssetFailsClosed() {
        MediaStreamSourceBinding b = new MediaStreamSourceBinding(MediaAssetId.of("missing"), STREAM,
                new ArtifactId("artifact-1"), binding().contentDigest(), binding().sourceRange());
        ValidationResult r = validator.validate(b);
        assertFalse(r.valid());
        assertTrue(r.violations().get(0).contains("does not exist"));
    }

    @Test
    void streamNotOwnedByAssetFailsClosed() {
        MediaStreamSourceBinding b = new MediaStreamSourceBinding(ASSET, MediaStreamId.of("other-stream"),
                new ArtifactId("artifact-1"), binding().contentDigest(), binding().sourceRange());
        ValidationResult r = validator.validate(b);
        assertFalse(r.valid());
        assertTrue(r.violations().get(0).contains("does not belong"));
    }
}
