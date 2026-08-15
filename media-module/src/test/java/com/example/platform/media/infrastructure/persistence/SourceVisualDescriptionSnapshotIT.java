package com.example.platform.media.infrastructure.persistence;

import com.example.platform.colorimage.AlphaDescription;
import com.example.platform.colorimage.ChromaSubsampling;
import com.example.platform.colorimage.Chromaticity;
import com.example.platform.colorimage.ColorDescription;
import com.example.platform.colorimage.ColorPrimaries;
import com.example.platform.colorimage.ColorProfileContentDigest;
import com.example.platform.colorimage.ContentLightMetadata;
import com.example.platform.colorimage.EncodedRasterExtent;
import com.example.platform.colorimage.MasteringDisplayMetadata;
import com.example.platform.colorimage.MatrixCoefficients;
import com.example.platform.colorimage.PixelAspectRatio;
import com.example.platform.colorimage.ProfileFormat;
import com.example.platform.colorimage.RasterSampleDescription;
import com.example.platform.colorimage.Rational;
import com.example.platform.colorimage.ScanDescription;
import com.example.platform.colorimage.SignalRange;
import com.example.platform.colorimage.SourceOrientation;
import com.example.platform.colorimage.SourceVisualDescription;
import com.example.platform.colorimage.StaticHdrMetadata;
import com.example.platform.colorimage.TransferCharacteristic;
import com.example.platform.media.domain.identity.MediaAssetId;
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
 * ROADMAP_18 CIP2: real-PostgreSQL durable snapshot tests — exact roundtrip,
 * historical reload never invokes normalizer/provider, immutable content
 * binding, profile/UNSPECIFIED-UNKNOWN/Rational preservation, transaction
 * rollback, non-visual streams.
 */
@Testcontainers
class SourceVisualDescriptionSnapshotIT {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DSLContext dsl;
    private static JooqSourceVisualDescriptionSnapshotRepository repo;
    private static final MediaAssetId ASSET = new MediaAssetId("asset-1");
    private static final MediaStreamId STREAM = new MediaStreamId("stream-1");
    private static final ArtifactId ARTIFACT_V1 = new ArtifactId("artifact-v1");

    @BeforeAll
    static void setup() {
        PG.start();
        dsl = DSL.using(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        dsl.execute("""
                create table media_stream (
                    id varchar(64) primary key,
                    media_asset_id varchar(64) not null,
                    stream_index int not null,
                    stream_kind varchar(16) not null
                )""");
        dsl.execute("""
                create table source_visual_description_snapshot (
                    media_stream_id varchar(64) not null,
                    media_asset_id   varchar(64) not null,
                    artifact_id      varchar(64) not null,
                    canonical_payload text not null,
                    created_at       timestamp not null default current_timestamp,
                    constraint fk_source_visual_snapshot_stream
                        foreign key (media_stream_id) references media_stream(id)
                )""");
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
        dsl.execute("create table artifact (id varchar(64) primary key)");
        dsl.execute("create table media_asset_artifact (media_asset_id varchar(64) not null, "
                + "artifact_id varchar(64) not null, relationship varchar(16) not null, "
                + "constraint pk_maa primary key (media_asset_id, artifact_id, relationship), "
                + "constraint fk_maa_artifact foreign key (artifact_id) references artifact(id))");
        dsl.execute("insert into artifact (id) values ('artifact-v1'), ('artifact-v2')");
        dsl.execute("insert into media_asset_artifact (media_asset_id, artifact_id, relationship) "
                + "values ('asset-1', 'artifact-v1', 'SOURCE_MEDIA'), ('asset-1', 'artifact-v2', 'DERIVED_MEDIA')");
        dsl.execute("insert into media_stream (id, media_asset_id, stream_index, stream_kind) "
                + "values ('stream-1', 'asset-1', 0, 'VIDEO')");
        dsl.execute("insert into media_stream (id, media_asset_id, stream_index, stream_kind) "
                + "values ('stream-audio', 'asset-1', 1, 'AUDIO')");
        repo = new JooqSourceVisualDescriptionSnapshotRepository(dsl);
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

    static SourceVisualDescription sample() {
        return new SourceVisualDescription(
                new EncodedRasterExtent(1920, 1080),
                PixelAspectRatio.of(1, 1),
                RasterSampleDescription.ycbcr(10, ChromaSubsampling.SAMPLE_420),
                new ColorDescription.ParametricColorDescription(
                        ColorPrimaries.WellKnown.BT2020, TransferCharacteristic.PQ,
                        MatrixCoefficients.BT2020_NCL, SignalRange.LIMITED),
                AlphaDescription.NO_ALPHA,
                SourceOrientation.NORMAL,
                new ScanDescription.Progressive(),
                Optional.of(StaticHdrMetadata.of(new MasteringDisplayMetadata(
                        Chromaticity.of("0.680", "0.320"), Chromaticity.of("0.265", "0.690"),
                        Chromaticity.of("0.150", "0.060"), Chromaticity.of("0.3127", "0.3290"),
                        Rational.of(1, 1000), Rational.of(1000, 1)))));
    }

    @Test
    void exactRoundtripS1EqualsS2() {
        SourceVisualDescription s1 = sample();
        repo.save(ASSET, STREAM, ARTIFACT_V1, s1);
        Optional<SourceVisualDescription> loaded = repo.findByStreamAndArtifact(STREAM, ARTIFACT_V1);
        assertTrue(loaded.isPresent());
        assertEquals(s1, loaded.get(), "S1 == S2 exact semantic equality");
    }

    @Test
    void historicalReloadDoesNotInvokeNormalizerOrProvider() {
        SourceVisualDescription s1 = sample();
        repo.save(ASSET, STREAM, ARTIFACT_V1, s1);
        Optional<SourceVisualDescription> loaded = repo.findByStreamAndArtifact(STREAM, ARTIFACT_V1);
        assertEquals(s1, loaded.get());
        // structural proof: the snapshot repository has zero probe/normalizer/ffprobe refs
        String src = loadRepoSource();
        assertFalse(src.contains("ffprobe") || src.contains("Probe") || src.contains("Normalizer"),
                "historical load path must not reference provider/normalizer");
    }

    @Test
    void immutableContentBinding() {
        repo.save(ASSET, STREAM, ARTIFACT_V1, sample());
        ArtifactId artifactV2 = new ArtifactId("artifact-v2");
        SourceVisualDescription s2 = new SourceVisualDescription(
                new EncodedRasterExtent(1280, 720), PixelAspectRatio.square(),
                RasterSampleDescription.rgb(8, false),
                new ColorDescription.ParametricColorDescription(ColorPrimaries.WellKnown.BT709,
                        TransferCharacteristic.BT709, MatrixCoefficients.BT709, SignalRange.LIMITED),
                AlphaDescription.NO_ALPHA, SourceOrientation.NORMAL,
                new ScanDescription.Progressive(), Optional.empty());
        repo.save(ASSET, STREAM, artifactV2, s2);
        assertEquals(s2, repo.findByStreamAndArtifact(STREAM, artifactV2).orElseThrow());
        // old snapshot for X must survive the Y insert (F2 coexistence)
        assertEquals(sample(), repo.findByStreamAndArtifact(STREAM, ARTIFACT_V1).orElseThrow());
    }

    @Test
    void profileDigestRoundtrip() {
        ColorDescription profile = new ColorDescription.ProfileBasedColorDescription(
                ProfileFormat.ICC, ColorProfileContentDigest.ofText("icc-profile-bytes-1"));
        SourceVisualDescription s = new SourceVisualDescription(
                new EncodedRasterExtent(640, 480), PixelAspectRatio.square(),
                RasterSampleDescription.rgb(8, false), profile,
                AlphaDescription.NO_ALPHA, SourceOrientation.NORMAL,
                new ScanDescription.Progressive(), Optional.empty());
        repo.save(ASSET, STREAM, ARTIFACT_V1, s);
        SourceVisualDescription loaded = repo.findByStreamAndArtifact(STREAM, ARTIFACT_V1).orElseThrow();
        assertEquals(profile, loaded.colorDescription());
        ColorDescription.ProfileBasedColorDescription p =
                (ColorDescription.ProfileBasedColorDescription) loaded.colorDescription();
        assertEquals(ProfileFormat.ICC, p.profileFormat());
        assertEquals(ColorProfileContentDigest.ofText("icc-profile-bytes-1").sha256Hex(),
                p.profileContentDigest().sha256Hex());
    }

    @Test
    void unspecifiedUnknownRoundtripDistinct() {
        SourceVisualDescription unspecified = new SourceVisualDescription(
                new EncodedRasterExtent(640, 480), PixelAspectRatio.square(),
                RasterSampleDescription.rgb(8, false),
                new ColorDescription.ParametricColorDescription(ColorPrimaries.WellKnown.UNSPECIFIED,
                        TransferCharacteristic.UNSPECIFIED, MatrixCoefficients.UNSPECIFIED, SignalRange.UNSPECIFIED),
                AlphaDescription.NO_ALPHA, SourceOrientation.NORMAL,
                new ScanDescription.Progressive(), Optional.empty());
        SourceVisualDescription unknown = new SourceVisualDescription(
                new EncodedRasterExtent(640, 480), PixelAspectRatio.square(),
                RasterSampleDescription.rgb(8, false),
                new ColorDescription.ParametricColorDescription(ColorPrimaries.WellKnown.UNKNOWN,
                        TransferCharacteristic.UNKNOWN, MatrixCoefficients.UNKNOWN, SignalRange.UNKNOWN),
                AlphaDescription.NO_ALPHA, SourceOrientation.NORMAL,
                new ScanDescription.Progressive(), Optional.empty());
        repo.save(ASSET, STREAM, ARTIFACT_V1, unspecified);
        assertEquals(unspecified, repo.findByStreamAndArtifact(STREAM, ARTIFACT_V1).orElseThrow());
        assertThrows(IllegalStateException.class,
                () -> repo.save(ASSET, STREAM, ARTIFACT_V1, unknown),
                "same exact content key with different description must fail closed");
        assertEquals(unspecified, repo.findByStreamAndArtifact(STREAM, ARTIFACT_V1).orElseThrow());
        assertNotEquals(unspecified, unknown, "UNSPECIFIED != UNKNOWN after roundtrip");
    }

    @Test
    void rationalExactnessPreserved() {
        SourceVisualDescription s = new SourceVisualDescription(
                new EncodedRasterExtent(1920, 1080),
                PixelAspectRatio.of(64, 45),
                RasterSampleDescription.ycbcr(8, ChromaSubsampling.SAMPLE_422),
                new ColorDescription.ParametricColorDescription(ColorPrimaries.WellKnown.BT709,
                        TransferCharacteristic.BT709, MatrixCoefficients.BT709, SignalRange.LIMITED),
                AlphaDescription.NO_ALPHA, SourceOrientation.NORMAL,
                new ScanDescription.Progressive(), Optional.empty());
        repo.save(ASSET, STREAM, ARTIFACT_V1, s);
        SourceVisualDescription loaded = repo.findByStreamAndArtifact(STREAM, ARTIFACT_V1).orElseThrow();
        assertEquals(PixelAspectRatio.of(64, 45), loaded.pixelAspectRatio());
        assertEquals(Rational.of(64, 45), loaded.pixelAspectRatio().value());
    }

    @Test
    void staticHdrOptionalityPreserved() {
        SourceVisualDescription noHdr = new SourceVisualDescription(
                new EncodedRasterExtent(640, 480), PixelAspectRatio.square(),
                RasterSampleDescription.rgb(8, false),
                new ColorDescription.ParametricColorDescription(ColorPrimaries.WellKnown.BT709,
                        TransferCharacteristic.BT709, MatrixCoefficients.BT709, SignalRange.LIMITED),
                AlphaDescription.NO_ALPHA, SourceOrientation.NORMAL,
                new ScanDescription.Progressive(), Optional.empty());
        repo.save(ASSET, STREAM, ARTIFACT_V1, noHdr);
        assertTrue(repo.findByStreamAndArtifact(STREAM, ARTIFACT_V1).orElseThrow().staticHdrMetadata().isEmpty());
        SourceVisualDescription clOnly = new SourceVisualDescription(
                new EncodedRasterExtent(640, 480), PixelAspectRatio.square(),
                RasterSampleDescription.rgb(8, false),
                new ColorDescription.ParametricColorDescription(ColorPrimaries.WellKnown.BT709,
                        TransferCharacteristic.PQ, MatrixCoefficients.BT709, SignalRange.LIMITED),
                AlphaDescription.NO_ALPHA, SourceOrientation.NORMAL,
                new ScanDescription.Progressive(),
                Optional.of(StaticHdrMetadata.of(new ContentLightMetadata(Rational.of(1000, 1), Rational.of(400, 1)))));
        com.example.platform.shared.identity.ArtifactId artifactV2 =
                new com.example.platform.shared.identity.ArtifactId("artifact-v2");
        repo.save(ASSET, STREAM, artifactV2, clOnly);
        SourceVisualDescription loaded = repo.findByStreamAndArtifact(STREAM, artifactV2).orElseThrow();
        assertTrue(loaded.staticHdrMetadata().isPresent());
        assertTrue(loaded.staticHdrMetadata().get().contentLight().isPresent());
        assertTrue(loaded.staticHdrMetadata().get().masteringDisplay().isEmpty());
    }

    @Test
    void transactionRollbackLeavesNoOrphan() {
        MediaStreamId ghost = new MediaStreamId("stream-ghost");
        assertThrows(org.jooq.exception.DataAccessException.class, () -> dsl.transaction(tx -> {
            tx.dsl().execute("insert into source_visual_description_snapshot "
                    + "(media_stream_id, media_asset_id, artifact_id, canonical_payload) "
                    + "values (?, ?, ?, ?)", ghost.value(), "asset-1", "artifact-v1", "x");
        }));
        Long count = dsl.fetchOne("select count(*) from source_visual_description_snapshot "
                + "where media_stream_id = ?", ghost.value()).get(0, Long.class);
        assertEquals(0, count, "no orphan snapshot row after failed transaction");
    }

    @Test
    void nonVisualStreamNoFakeDescription() {
        assertTrue(repo.findByStreamAndArtifact(new MediaStreamId("stream-audio"), new com.example.platform.shared.identity.ArtifactId("artifact-audio")).isEmpty());
    }

    private static String loadRepoSource() {
        try {
            String root = System.getProperty("project.root.dir", ".");
            return new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Path.of(root, "media-module/src/main/java/com/example/platform/media/"
                            + "infrastructure/persistence/JooqSourceVisualDescriptionSnapshotRepository.java")));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
