package com.example.platform.media.infrastructure.persistence;

import static com.example.platform.typedschema.jooq.generated.tables.MediaProbeObservation.MEDIA_PROBE_OBSERVATION;

import com.example.platform.media.app.MediaProbeObservationRepository;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.probe.MediaProbeObservation;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/**
 * jOOQ implementation over the raw media_probe_observation table.
 * Provider-specific raw payload is stored opaque (raw_payload) and is NOT
 * canonical media authority.
 */
@Repository
public class JooqMediaProbeObservationRepository implements MediaProbeObservationRepository {

    private final DSLContext dsl;

    public JooqMediaProbeObservationRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void save(MediaAssetId mediaAssetId, String tenantId, String projectId, MediaProbeObservation observation) {
        String id = "mpo-" + System.nanoTime() + "-" + mediaAssetId.value().hashCode();
        dsl.insertInto(MEDIA_PROBE_OBSERVATION)
                .columns(MEDIA_PROBE_OBSERVATION.ID, MEDIA_PROBE_OBSERVATION.TENANT_ID,
                        MEDIA_PROBE_OBSERVATION.PROJECT_ID, MEDIA_PROBE_OBSERVATION.MEDIA_ASSET_ID,
                        MEDIA_PROBE_OBSERVATION.PROVIDER, MEDIA_PROBE_OBSERVATION.RAW_PAYLOAD,
                        MEDIA_PROBE_OBSERVATION.VALID, MEDIA_PROBE_OBSERVATION.CLIENT_EXPORT_COMPATIBLE,
                        MEDIA_PROBE_OBSERVATION.NORMALIZE_REQUIRED, MEDIA_PROBE_OBSERVATION.WARNINGS,
                        MEDIA_PROBE_OBSERVATION.ERROR_MESSAGE, MEDIA_PROBE_OBSERVATION.PROBED_AT)
                .values(id, tenantId, projectId, mediaAssetId.value(),
                        observation.provider(), observation.rawPayload(),
                        observation.valid(), observation.clientExportCompatible(),
                        observation.normalizeRequired(),
                        observation.warnings() != null && !observation.warnings().isEmpty()
                                ? String.join("|", observation.warnings()) : null,
                        observation.error(), LocalDateTime.now(ZoneOffset.UTC))
                .execute();
    }

    @Override
    public Optional<MediaProbeObservation> findLatest(MediaAssetId mediaAssetId) {
        var row = dsl.selectFrom(MEDIA_PROBE_OBSERVATION)
                .where(MEDIA_PROBE_OBSERVATION.MEDIA_ASSET_ID.eq(mediaAssetId.value()))
                .orderBy(MEDIA_PROBE_OBSERVATION.PROBED_AT.desc())
                .limit(1)
                .fetchOne();
        if (row == null) {
            return Optional.empty();
        }
        String warnings = row.get(MEDIA_PROBE_OBSERVATION.WARNINGS);
        return Optional.of(new MediaProbeObservation(
                row.get(MEDIA_PROBE_OBSERVATION.PROVIDER),
                row.get(MEDIA_PROBE_OBSERVATION.RAW_PAYLOAD),
                Boolean.TRUE.equals(row.get(MEDIA_PROBE_OBSERVATION.VALID)),
                Boolean.TRUE.equals(row.get(MEDIA_PROBE_OBSERVATION.CLIENT_EXPORT_COMPATIBLE)),
                Boolean.TRUE.equals(row.get(MEDIA_PROBE_OBSERVATION.NORMALIZE_REQUIRED)),
                warnings != null && !warnings.isBlank() ? List.of(warnings.split("\\|")) : List.of(),
                row.get(MEDIA_PROBE_OBSERVATION.ERROR_MESSAGE)));
    }
}
