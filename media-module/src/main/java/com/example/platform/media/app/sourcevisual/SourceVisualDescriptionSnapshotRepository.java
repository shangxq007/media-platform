package com.example.platform.media.app.sourcevisual;

import com.example.platform.colorimage.SourceVisualDescription;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.shared.identity.ArtifactId;

import java.util.Optional;

/**
 * ROADMAP_18 CIP2 (CIP2A/CIP2B/CIP2C): durable Media-owned snapshot of the
 * canonical SourceVisualDescription, bound to immutable source content
 * (artifact content version). Historical reload reads the persisted snapshot;
 * it NEVER re-runs provider probe or current normalizer.
 */
public interface SourceVisualDescriptionSnapshotRepository {

    /**
     * Append exact content-version snapshot (CIP2F F2: one logical stream may
     * be interpreted from multiple immutable artifact content versions).
     * Conflicting duplicate for the same (stream, artifact) key fails closed;
     * identical payload for the same key is accepted idempotently.
     */
    void save(MediaAssetId mediaAssetId, MediaStreamId streamId, ArtifactId artifactId,
              SourceVisualDescription description);

    /**
     * Load the exact persisted canonical snapshot for one exact content
     * version. MUST NOT invoke provider probe or normalizer. Empty =
     * non-visual stream or that content version not yet ingested.
     */
    Optional<SourceVisualDescription> findByStreamAndArtifact(MediaStreamId streamId, ArtifactId artifactId);
}
