package com.example.platform.media.infrastructure.persistence;

import com.example.platform.colorimage.AlphaDescription;
import com.example.platform.colorimage.ChromaSubsampling;
import com.example.platform.colorimage.ColorDescription;
import com.example.platform.colorimage.ColorPrimaries;
import com.example.platform.colorimage.EncodedRasterExtent;
import com.example.platform.colorimage.MatrixCoefficients;
import com.example.platform.colorimage.PixelAspectRatio;
import com.example.platform.colorimage.RasterSampleDescription;
import com.example.platform.colorimage.ScanDescription;
import com.example.platform.colorimage.SignalRange;
import com.example.platform.colorimage.SourceOrientation;
import com.example.platform.colorimage.SourceVisualDescription;
import com.example.platform.colorimage.TransferCharacteristic;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStreamId;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ROADMAP_18 CIP2D: PostgreSQL-level relational ownership enforcement — the
 * DATABASE itself must reject cross-asset / cross-stream / unlinked bindings
 * via direct SQL (bypassing any application validation).
 */
@Testcontainers
class SourceVisualOwnershipIntegrityIT {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DSLContext dsl;
    private static final MediaAssetId ASSET_A = new MediaAssetId("asset-A");
    private static final MediaAssetId ASSET_B = new MediaAssetId("asset-B");
    private static final MediaStreamId STREAM_A = new MediaStreamId("stream-A");
    private static final MediaStreamId STREAM_B = new MediaStreamId("stream-B");

    @BeforeAll
    static void setup() {
        PG.start();
        dsl = DSL.using(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        // production-equivalent DDL (V1 + V5 + V6 shape)
        dsl.execute("create table media_asset (id varchar(64) primary key)");
        dsl.execute("create table media_stream (id varchar(64) primary key, "
                + "media_asset_id varchar(64) not null, stream_index int not null, stream_kind varchar(16) not null, "
                + "constraint fk_ms_media_asset foreign key (media_asset_id) references media_asset(id) on delete cascade)");
        dsl.execute("create table artifact (id varchar(64) primary key)");
        dsl.execute("create table media_asset_artifact (media_asset_id varchar(64) not null, "
                + "artifact_id varchar(64) not null, relationship varchar(16) not null, "
                + "constraint pk_maa primary key (media_asset_id, artifact_id, relationship), "
                + "constraint fk_maa_media_asset foreign key (media_asset_id) references media_asset(id), "
                + "constraint fk_maa_artifact foreign key (artifact_id) references artifact(id))");
        dsl.execute("create table source_visual_description_snapshot (media_stream_id varchar(64) not null, "
                + "media_asset_id varchar(64) not null, artifact_id varchar(64) not null, "
                + "canonical_payload text not null, created_at timestamp not null default current_timestamp, "
                + "constraint fk_source_visual_snapshot_stream foreign key (media_stream_id) references media_stream(id))");
        dsl.execute("alter table source_visual_description_snapshot "
                + "add constraint pk_svd_stream_artifact primary key (media_stream_id, artifact_id)");
        dsl.execute("""
                create or replace function trg_fn_svd_snapshot_immutable() returns trigger as $$
                begin
                    if new.media_stream_id is distinct from old.media_stream_id
                       or new.media_asset_id is distinct from old.media_asset_id
                       or new.artifact_id is distinct from old.artifact_id
                       or new.canonical_payload is distinct from old.canonical_payload then
                        raise exception 'SOURCE_VISUAL_SNAPSHOT_IMMUTABLE';
                    end if;
                    return new;
                end;
                $$ language plpgsql""");
        dsl.execute("create trigger trg_svd_snapshot_immutable before update on "
                + "source_visual_description_snapshot for each row "
                + "execute function trg_fn_svd_snapshot_immutable()");
        // V6 constraints
        dsl.execute("alter table media_stream add constraint uq_ms_id_asset unique (id, media_asset_id)");
        dsl.execute("alter table media_asset_artifact add constraint uq_maa_asset_artifact unique (media_asset_id, artifact_id)");
        dsl.execute("alter table source_visual_description_snapshot add constraint fk_svd_stream_asset "
                + "foreign key (media_stream_id, media_asset_id) references media_stream (id, media_asset_id)");
        dsl.execute("alter table source_visual_description_snapshot add constraint fk_svd_asset_artifact "
                + "foreign key (media_asset_id, artifact_id) references media_asset_artifact (media_asset_id, artifact_id)");

        dsl.execute("insert into media_asset (id) values ('asset-A'), ('asset-B')");
        dsl.execute("insert into media_stream (id, media_asset_id, stream_index, stream_kind) "
                + "values ('stream-A', 'asset-A', 0, 'VIDEO'), ('stream-B', 'asset-B', 0, 'VIDEO')");
        dsl.execute("insert into artifact (id) values ('artifact-X'), ('artifact-Y'), ('artifact-Z')");
        // asset-A owns artifact-X and artifact-Y (two content versions);
        // asset-B owns artifact-Z (used for cross-asset negative tests)
        dsl.execute("insert into media_asset_artifact (media_asset_id, artifact_id, relationship) "
                + "values ('asset-A', 'artifact-X', 'SOURCE_MEDIA'), ('asset-A', 'artifact-Y', 'SOURCE_MEDIA'), "
                + "('asset-B', 'artifact-Z', 'SOURCE_MEDIA')");
    }

    @AfterAll
    static void teardown() {
        if (PG != null) {
            PG.stop();
        }
    }

    @BeforeEach
    void resetState() {
        dsl.execute("delete from source_visual_description_snapshot");
    }

    @Test
    void v7DirectSqlArtifactRebindRejected() {
        dsl.execute("insert into source_visual_description_snapshot "
                + "(media_stream_id, media_asset_id, artifact_id, canonical_payload) values (?, ?, ?, ?)",
                STREAM_A.value(), "asset-A", "artifact-X", payload());
        // direct SQL rebind S from artifact-X to artifact-Y must be rejected by the
        // immutability trigger (CIP2F: no historical rebind)
        org.jooq.exception.DataAccessException ex = assertThrows(
                org.jooq.exception.DataAccessException.class,
                () -> dsl.execute("update source_visual_description_snapshot "
                        + "set artifact_id = ? where media_stream_id = ?",
                        "artifact-Y", STREAM_A.value()));
        assertTrue(ex.getMessage().contains("SOURCE_VISUAL_SNAPSHOT_IMMUTABLE"),
                "trigger must reject semantic rebind, got: " + ex.getMessage());
    }

    @Test
    void v7DirectSqlPayloadRewriteRejected() {
        dsl.execute("insert into source_visual_description_snapshot "
                + "(media_stream_id, media_asset_id, artifact_id, canonical_payload) values (?, ?, ?, ?)",
                STREAM_A.value(), "asset-A", "artifact-X", payload());
        String different = "format=source-visual-v1\nextent=640x480\npar=1/1\n"
                + "sample=RGB|INTERLEAVED|8|NONE|UNSPECIFIED|false\n"
                + "color=parametric|wellknown:BT709|BT709|BT709|LIMITED\nalpha=NO_ALPHA\n"
                + "orient=NORMAL\nscan=progressive\nhdr=absent\n";
        org.jooq.exception.DataAccessException ex = assertThrows(
                org.jooq.exception.DataAccessException.class,
                () -> dsl.execute("update source_visual_description_snapshot "
                        + "set canonical_payload = ? where media_stream_id = ?",
                        different, STREAM_A.value()));
        assertTrue(ex.getMessage().contains("SOURCE_VISUAL_SNAPSHOT_IMMUTABLE"),
                "trigger must reject payload rewrite, got: " + ex.getMessage());
    }

    @Test
    void v7MultiArtifactSnapshotsCoexist() {
        dsl.execute("insert into source_visual_description_snapshot "
                + "(media_stream_id, media_asset_id, artifact_id, canonical_payload) values (?, ?, ?, ?)",
                STREAM_A.value(), "asset-A", "artifact-X", payload());
        String other = "format=source-visual-v1\nextent=3840x2160\npar=1/1\n"
                + "sample=RGB|INTERLEAVED|10|NONE|UNSPECIFIED|false\n"
                + "color=parametric|wellknown:BT2020|PQ|BT2020_NCL|LIMITED\nalpha=NO_ALPHA\n"
                + "orient=NORMAL\nscan=progressive\nhdr=absent\n";
        // artifact-Y already linked to asset-A -> second content version snapshot
        dsl.execute("insert into source_visual_description_snapshot "
                + "(media_stream_id, media_asset_id, artifact_id, canonical_payload) values (?, ?, ?, ?)",
                STREAM_A.value(), "asset-A", "artifact-Y", other);
        assertEquals(2, dsl.fetchOne("select count(*) from source_visual_description_snapshot "
                + "where media_stream_id = ?", STREAM_A.value()).get(0, Long.class),
                "F2: two artifact snapshots for one stream must coexist");
        // X snapshot unchanged
        String x = dsl.fetchOne("select canonical_payload from source_visual_description_snapshot "
                + "where media_stream_id = ? and artifact_id = ?", STREAM_A.value(), "artifact-X")
                .get(0, String.class);
        assertEquals(payload(), x, "snapshot X unchanged after Y insert");
    }

    private static String payload() {
        return "format=source-visual-v1\nextent=1920x1080\npar=1/1\nsample=RGB|INTERLEAVED|8|NONE|UNSPECIFIED|false\n"
                + "color=parametric|wellknown:BT709|BT709|BT709|LIMITED\nalpha=NO_ALPHA\norient=NORMAL\nscan=progressive\nhdr=absent\n";
    }

    private static void expectReject(Runnable insert, String caseName) {
        org.jooq.exception.DataAccessException ex =
                assertThrows(org.jooq.exception.DataAccessException.class, insert::run,
                        caseName + " must be rejected by PostgreSQL");
        assertNotNull(ex);
    }

    @Test
    void d1CrossAssetStreamAssetMismatchRejected() {
        // stream-A belongs to asset-A; declaring asset-B must be rejected
        expectReject(() -> dsl.execute("insert into source_visual_description_snapshot "
                + "(media_stream_id, media_asset_id, artifact_id, canonical_payload) values (?, ?, ?, ?)",
                STREAM_A.value(), "asset-B", "artifact-X", payload()), "D1");
    }

    @Test
    void d2ArtifactOfAnotherAssetRejected() {
        // stream-A (asset-A) with artifact-Z (belongs to asset-B) must be rejected
        expectReject(() -> dsl.execute("insert into source_visual_description_snapshot "
                + "(media_stream_id, media_asset_id, artifact_id, canonical_payload) values (?, ?, ?, ?)",
                STREAM_A.value(), "asset-A", "artifact-Z", payload()), "D2");
    }

    @Test
    void d3UnlinkedArtifactRejected() {
        // artifact not linked to any asset must be rejected
        dsl.execute("insert into artifact (id) values ('artifact-orphan')");
        expectReject(() -> dsl.execute("insert into source_visual_description_snapshot "
                + "(media_stream_id, media_asset_id, artifact_id, canonical_payload) values (?, ?, ?, ?)",
                STREAM_A.value(), "asset-A", "artifact-orphan", payload()), "D3");
    }

    @Test
    void d4NonexistentStreamRejected() {
        expectReject(() -> dsl.execute("insert into source_visual_description_snapshot "
                + "(media_stream_id, media_asset_id, artifact_id, canonical_payload) values (?, ?, ?, ?)",
                "stream-ghost", "asset-A", "artifact-X", payload()), "D4");
    }

    @Test
    void d5NonexistentArtifactRejected() {
        expectReject(() -> dsl.execute("insert into source_visual_description_snapshot "
                + "(media_stream_id, media_asset_id, artifact_id, canonical_payload) values (?, ?, ?, ?)",
                STREAM_A.value(), "asset-A", "artifact-ghost", payload()), "D5");
    }

    @Test
    void d6ValidOwnershipInsertSucceeds() {
        dsl.execute("insert into source_visual_description_snapshot "
                + "(media_stream_id, media_asset_id, artifact_id, canonical_payload) values (?, ?, ?, ?)",
                STREAM_A.value(), "asset-A", "artifact-X", payload());
        Long count = dsl.fetchOne("select count(*) from source_visual_description_snapshot "
                + "where media_stream_id = ?", STREAM_A.value()).get(0, Long.class);
        assertEquals(1, count, "valid ownership insert must succeed");
        // artifact-Z for stream-B also valid
        dsl.execute("insert into source_visual_description_snapshot "
                + "(media_stream_id, media_asset_id, artifact_id, canonical_payload) values (?, ?, ?, ?)",
                STREAM_B.value(), "asset-B", "artifact-Z", payload());
        assertEquals(2, dsl.fetchOne("select count(*) from source_visual_description_snapshot")
                .get(0, Long.class));
    }

    @Test
    void validSnapshotRoundtripThroughRepositoryStillPasses() {
        // regression: the CIP2 repository roundtrip still works under V6 constraints
        JooqSourceVisualDescriptionSnapshotRepository repo =
                new JooqSourceVisualDescriptionSnapshotRepository(dsl);
        SourceVisualDescription s1 = new SourceVisualDescription(
                new EncodedRasterExtent(1920, 1080), PixelAspectRatio.of(1, 1),
                RasterSampleDescription.ycbcr(10, ChromaSubsampling.SAMPLE_420),
                new ColorDescription.ParametricColorDescription(ColorPrimaries.WellKnown.BT2020,
                        TransferCharacteristic.PQ, MatrixCoefficients.BT2020_NCL, SignalRange.LIMITED),
                AlphaDescription.NO_ALPHA, SourceOrientation.NORMAL,
                new ScanDescription.Progressive(), Optional.empty());
        repo.save(new MediaAssetId("asset-A"), STREAM_A, new com.example.platform.shared.identity.ArtifactId("artifact-X"), s1);
        assertEquals(s1, repo.findByStreamAndArtifact(STREAM_A, new com.example.platform.shared.identity.ArtifactId("artifact-X")).orElseThrow());
    }
}
