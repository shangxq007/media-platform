package com.example.platform.render.testsupport;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.render.domain.timeline.semantics.clip.MediaClip.TimeRange;
import com.example.platform.render.domain.timeline.semantics.clip.SourceBinding;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.storage.contract.ContentDigest;

/** Test factory for typed SourceBinding (TIMELINE_V2 test fixtures). */
public final class TestSourceBindings {

    private TestSourceBindings() {}

    public static SourceBinding sample() {
        return new SourceBinding(
                MediaAssetId.of("asset-1"),
                MediaStreamId.of("stream-1"),
                new ArtifactId("artifact-1"),
                new ContentDigest(ContentDigest.DigestAlgorithm.SHA_256,
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                new TimeRange(MediaTime.ZERO, MediaTime.ofNanos(5_000_000_000L)));
    }

    public static SourceBinding of(
            String mediaAssetId, String mediaStreamId, String artifactId, TimeRange range) {
        return new SourceBinding(
                MediaAssetId.of(mediaAssetId),
                MediaStreamId.of(mediaStreamId),
                new ArtifactId(artifactId),
                new ContentDigest(ContentDigest.DigestAlgorithm.SHA_256,
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                range);
    }
}
