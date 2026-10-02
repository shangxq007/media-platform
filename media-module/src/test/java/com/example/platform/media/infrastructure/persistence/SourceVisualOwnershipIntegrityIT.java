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
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.shared.identity.ArtifactId;
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
 * ROADMAP_18 CIP2D: PostgreSQL-level relational ownership enforcement under the
 * consolidated Artifact-only identity. The DATABASE itself must reject
 * cross-stream / nonexistent bindings via direct SQL (bypassing any application
 * validation), so no retired media authority is needed to prove ownership.
 */
@Testcontainers
class SourceVisualOwnershipIntegrityIT {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DSLContext dsl;
    private static final ArtifactId ARTIFACT_X = new ArtifactId("artifact-X");
    private static final ArtifactId ARTIFACT_Z = new ArtifactId("artifact-Z");
    private static final MediaStreamId STREAM_A = new MediaStreamId("stream-A");
    private static final MediaStreamId STREAM_B = new MediaStreamId("stream-B");

    @BeforeAll
    static void setup() {
        PG.start();
        dsl = DSL.using(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        // production-equivalent Artifact-keyed DDL (consolidated V1 shape)
        dsl.execute("create table artifact (id varchar(64) primary key)");
        dsl.execute("create table media_stream (id varchar(64) primary key, "
                + "artifact_id varchar(64) not null, stream_index int not null, stream_kind varchar(16) not null, "
                + "constraint fk_ms_artifact foreign key (artifact_id) references artifact(id) on delete restrict, "
                + "constraint uq_ms_id_artifact unique (id, artifact_id))");
        dsl.execute("create table source_visual_description_snapshot (media_stream_id varchar(64) not null, "
                + "artifact_id varchar(64) not null, canonical_payload text not null, "
                + "created_at timestamp not null default current_timestamp, "
                + "constraint pk_svd_stream_artifact primary key (media_stream_id, artifact_id), "
                + "constraint fk_svd_artifact foreign key (artifact_id) references artifact(id), "
                + "constraint fk_source_visual_snapshot_stream foreign key (media_stream_id) references media_stream(id), "
                + "constraint fk_svd_stream_artifact foreign key (media_stream_id, artifact_id) "
                + "references media_stream (id, artifact_id))");
        dsl.execute("""
                create or replace function trg_fn_svd_snapshot_immutable() returns trigger as $$
                begin
                    if new.media_stream_id is distinct from old.media_stream_id
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

        dsl.execute("insert into artifact (id) values ('artifact-X'), ('artifact-Z')");
        dsl.execute("insert into media_stream (id, artifact_id, stream_index, stream_kind) "
                + "values ('stream-A', 'artifact-X', 0, 'VIDEO'), ('stream-B', 'artifact-Z', 0, 'VIDEO')");
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
                + "(media_stream_id, artifact_id, canonical_payload) values (?, ?, ?)",
                STREAM_A.value(), "artifact-X", payload());
        // direct SQL rebind S from artifact-X to artifact-Z must be rejected by the
        // immutability trigger (CIP2F: no historical rebind)
        org.jooq.exception.DataAccessException ex = assertThrows(
                org.jooq.exception.DataAccessException.class,
                () -> dsl.execute("update source_visual_description_snapshot "
                        + "set artifact_id = ? where media_stream_id = ?",
                        "artifact-Z", STREAM_A.value()));
        assertTrue(ex.getMessage().contains("SOURCE_VISUAL_SNAPSHOT_IMMUTABLE"),
                "trigger must reject semantic rebind, got: " + ex.getMessage());
    }

    @Test
    void v7DirectSqlPayloadRewriteRejected() {
        dsl.execute("insert into source_visual_description_snapshot "
                + "(media_stream_id, artifact_id, canonical_payload) values (?, ?, ?)",
                STREAM_A.value(), "artifact-X", payload());
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
    void independentStreamSnapshotsCoexist() {
        dsl.execute("insert into source_visual_description_snapshot "
                + "(media_stream_id, artifact_id, canonical_payload) values (?, ?, ?)",
                STREAM_A.value(), "artifact-X", payload());
        String other = "format=source-visual-v1\nextent=3840x2160\npar=1/1\n"
                + "sample=RGB|INTERLEAVED|10|NONE|UNSPECIFIED|false\n"
                + "color=parametric|wellknown:BT2020|PQ|BT2020_NCL|LIMITED\nalpha=NO_ALPHA\n"
                + "orient=NORMAL\nscan=progressive\nhdr=absent\n";
        dsl.execute("insert into source_visual_description_snapshot "
                + "(media_stream_id, artifact_id, canonical_payload) values (?, ?, ?)",
                STREAM_B.value(), "artifact-Z", other);
        assertEquals(2, dsl.fetchOne("select count(*) from source_visual_description_snapshot")
                .get(0, Long.class), "each stream keeps its own immutable snapshot");
        String x = dsl.fetchOne("select canonical_payload from source_visual_description_snapshot "
                + "where media_stream_id = ? and artifact_id = ?", STREAM_A.value(), "artifact-X")
                .get(0, String.class);
        assertEquals(payload(), x, "snapshot X unchanged after the second stream insert");
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
    void d1CrossStreamArtifactMismatchRejected() {
        // stream-A belongs to artifact-X; declaring artifact-Z (stream-B's artifact) must be rejected
        expectReject(() -> dsl.execute("insert into source_visual_description_snapshot "
                + "(media_stream_id, artifact_id, canonical_payload) values (?, ?, ?)",
                STREAM_A.value(), "artifact-Z", payload()), "D1");
    }

    @Test
    void d2NonexistentArtifactRejected() {
        expectReject(() -> dsl.execute("insert into source_visual_description_snapshot "
                + "(media_stream_id, artifact_id, canonical_payload) values (?, ?, ?)",
                STREAM_A.value(), "artifact-ghost", payload()), "D2");
    }

    @Test
    void d3NonexistentStreamRejected() {
        expectReject(() -> dsl.execute("insert into source_visual_description_snapshot "
                + "(media_stream_id, artifact_id, canonical_payload) values (?, ?, ?)",
                "stream-ghost", "artifact-X", payload()), "D3");
    }

    @Test
    void d4ValidOwnershipInsertSucceeds() {
        dsl.execute("insert into source_visual_description_snapshot "
                + "(media_stream_id, artifact_id, canonical_payload) values (?, ?, ?)",
                STREAM_A.value(), "artifact-X", payload());
        assertEquals(1, dsl.fetchOne("select count(*) from source_visual_description_snapshot "
                + "where media_stream_id = ?", STREAM_A.value()).get(0, Long.class),
                "valid artifact-keyed ownership insert must succeed");
        dsl.execute("insert into source_visual_description_snapshot "
                + "(media_stream_id, artifact_id, canonical_payload) values (?, ?, ?)",
                STREAM_B.value(), "artifact-Z", payload());
        assertEquals(2, dsl.fetchOne("select count(*) from source_visual_description_snapshot")
                .get(0, Long.class));
    }

    @Test
    void validSnapshotRoundtripThroughRepositoryStillPasses() {
        JooqSourceVisualDescriptionSnapshotRepository repo =
                new JooqSourceVisualDescriptionSnapshotRepository(dsl);
        SourceVisualDescription s1 = new SourceVisualDescription(
                new EncodedRasterExtent(1920, 1080), PixelAspectRatio.of(1, 1),
                RasterSampleDescription.ycbcr(10, ChromaSubsampling.SAMPLE_420),
                new ColorDescription.ParametricColorDescription(ColorPrimaries.WellKnown.BT2020,
                        TransferCharacteristic.PQ, MatrixCoefficients.BT2020_NCL, SignalRange.LIMITED),
                AlphaDescription.NO_ALPHA, SourceOrientation.NORMAL,
                new ScanDescription.Progressive(), Optional.empty());
        repo.save(STREAM_A, ARTIFACT_X, s1);
        assertEquals(s1, repo.findByStreamAndArtifact(STREAM_A, ARTIFACT_X).orElseThrow());
    }
}
