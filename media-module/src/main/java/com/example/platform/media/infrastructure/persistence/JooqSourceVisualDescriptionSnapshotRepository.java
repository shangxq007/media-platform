package com.example.platform.media.infrastructure.persistence;

import com.example.platform.colorimage.SourceVisualDescription;
import com.example.platform.media.app.sourcevisual.SourceVisualDescriptionCodec;
import com.example.platform.media.app.sourcevisual.SourceVisualDescriptionSnapshotRepository;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.shared.identity.ArtifactId;
import org.jooq.DSLContext;

import java.util.Optional;

/** ROADMAP_18 CIP2: jOOQ adapter for the durable canonical snapshot. */
public class JooqSourceVisualDescriptionSnapshotRepository
        implements SourceVisualDescriptionSnapshotRepository {

    private final DSLContext dsl;

    public JooqSourceVisualDescriptionSnapshotRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void save(MediaAssetId mediaAssetId, MediaStreamId streamId, ArtifactId artifactId,
                     SourceVisualDescription description) {
        String payload = SourceVisualDescriptionCodec.encode(description);
        dsl.execute("""
                insert into source_visual_description_snapshot
                    (media_stream_id, media_asset_id, artifact_id, canonical_payload)
                values (?, ?, ?, ?)
                on conflict (media_stream_id) do update set
                    media_asset_id = excluded.media_asset_id,
                    artifact_id = excluded.artifact_id,
                    canonical_payload = excluded.canonical_payload
                """, streamId.value(), mediaAssetId.value(), artifactId.value(), payload);
    }

    @Override
    public Optional<SourceVisualDescription> findByStreamId(MediaStreamId streamId) {
        var rec = dsl.fetchOne("select canonical_payload from source_visual_description_snapshot "
                + "where media_stream_id = ?", streamId.value());
        if (rec == null) {
            return Optional.empty();
        }
        return Optional.of(SourceVisualDescriptionCodec.decode(rec.get(0, String.class)));
    }
}
