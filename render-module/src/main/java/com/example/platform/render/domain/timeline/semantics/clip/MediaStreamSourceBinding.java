package com.example.platform.render.domain.timeline.semantics.clip;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.storage.contract.ContentDigest;
import java.util.Objects;

/**
 * ROADMAP_17 (S2): canonical MediaStream source binding — the recorded-media
 * specialization of {@link TimelineSourceBinding}.
 *
 * <p>Preserves the FULL #14 TIMELINE_V2 source semantics:
 * <ul>
 *   <li>{@code mediaAssetId} — canonical media asset identity (semantic owner, stable across
 *       re-probe / metadata enrichment / storage relocation).</li>
 *   <li>{@code mediaStreamId} — canonical source stream identity (SOURCE_STREAM_IDENTITY_AUTHORITY_V1).</li>
 *   <li>{@code artifactId} — immutable content pin (the exact consumed content, NOT the mutable
 *       latest asset resolution).</li>
 *   <li>{@code contentDigest} — digest of the pinned content (content identity).</li>
 *   <li>{@code sourceRange} — exact source range in canonical MediaTime (exact rational, never double).</li>
 * </ul>
 *
 * <p>Content pinning is NOT weakened: a revision that binds this object stays semantically
 * immutable (T3). No media technical metadata is embedded (T4): duration/fps/codec/channels/
 * color live in the media domain, never here. The exact source TimeRange remains here until
 * the Temporal Mapping foundation lands (S10); it is NOT a TemporalMapping model.
 *
 * <p>Replaces the legacy {@code SourceBinding} name (S3): one canonical model, no V1/V2
 * dual track, no compatibility wrapper.
 */
public record MediaStreamSourceBinding(
        MediaAssetId mediaAssetId,
        MediaStreamId mediaStreamId,
        ArtifactId artifactId,
        ContentDigest contentDigest,
        MediaClip.TimeRange sourceRange) implements TimelineSourceBinding {

    public MediaStreamSourceBinding {
        Objects.requireNonNull(mediaAssetId, "mediaAssetId");
        Objects.requireNonNull(mediaStreamId, "mediaStreamId");
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(contentDigest, "contentDigest");
        Objects.requireNonNull(sourceRange, "sourceRange");
        // sourceRange itself validates exact start <= end via MediaClip.TimeRange invariants.
    }

    @Override
    public SourceKind sourceKind() {
        return SourceKind.MEDIA_STREAM;
    }
}
