package com.example.platform.render.domain.timeline.semantics.clip;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.storage.contract.ContentDigest;
import java.util.Objects;

/**
 * TYPED SOURCE BINDING (TIMELINE_V2_BOUNDED_ARCHITECTURE_CONTRACT_V1, T2/T3/T4).
 *
 * <p>{@code SourceBinding = identity + exact consumed content + stream + exact source range}:
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
 * <p>Replaces the legacy {@code MediaClip.mediaReference} String authority. A revision that binds
 * this object stays semantically immutable: relink/replacement of the media asset does not change
 * what the historical revision actually consumed (T3). No media technical metadata is embedded
 * (T4): duration/fps/codec/channels/color live in the media domain, never here.
 */
public record SourceBinding(
        MediaAssetId mediaAssetId,
        MediaStreamId mediaStreamId,
        ArtifactId artifactId,
        ContentDigest contentDigest,
        MediaClip.TimeRange sourceRange) {

    public SourceBinding {
        Objects.requireNonNull(mediaAssetId, "mediaAssetId");
        Objects.requireNonNull(mediaStreamId, "mediaStreamId");
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(contentDigest, "contentDigest");
        Objects.requireNonNull(sourceRange, "sourceRange");
        // sourceRange itself validates exact start <= end via MediaClip.TimeRange invariants.
    }
}
