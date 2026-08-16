package com.example.platform.render.testsupport;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.timeline.semantics.clip.MediaClip.TimeRange;
import com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.storage.contract.ContentDigest;

/** Test factory for typed MediaStreamSourceBinding (TIMELINE_V2 test fixtures). */
public final class TestSourceBindings {

    private TestSourceBindings() {}

    public static MediaStreamSourceBinding sample() {
        return new MediaStreamSourceBinding(
                MediaAssetId.of("asset-1"),
                MediaStreamId.of("stream-1"),
                new ArtifactId("artifact-1"),
                new ContentDigest(ContentDigest.DigestAlgorithm.SHA_256,
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                new TimeRange(MediaTime.ZERO, MediaTime.ofNanos(5_000_000_000L)));
    }

    public static MediaStreamSourceBinding of(
            String mediaAssetId, String mediaStreamId, String artifactId, TimeRange range) {
        return new MediaStreamSourceBinding(
                MediaAssetId.of(mediaAssetId),
                MediaStreamId.of(mediaStreamId),
                new ArtifactId(artifactId),
                new ContentDigest(ContentDigest.DigestAlgorithm.SHA_256,
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                range);
    }
}
