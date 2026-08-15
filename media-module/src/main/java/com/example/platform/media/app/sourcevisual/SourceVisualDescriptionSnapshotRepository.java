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
     * Persist canonical snapshot for one visual stream. artifactId is the
     * immutable content pin (new content version = new ArtifactId). Same
     * transaction boundary as Media source identity persistence.
     */
    void save(MediaAssetId mediaAssetId, MediaStreamId streamId, ArtifactId artifactId,
              SourceVisualDescription description);

    /**
     * Load the exact persisted canonical snapshot. MUST NOT invoke provider
     * probe or normalizer. Empty = non-visual stream or not yet ingested.
     */
    Optional<SourceVisualDescription> findByStreamId(MediaStreamId streamId);
}
