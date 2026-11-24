package com.example.platform.media.infrastructure.persistence;

import com.example.platform.shared.test.TestDatabaseConnectionProvider;
import com.example.platform.shared.test.TestDatabaseSchema;

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
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.shared.identity.ArtifactId;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ROADMAP_18 CIP2: real-PostgreSQL durable snapshot tests — exact roundtrip,
 * historical reload never invokes normalizer/provider, immutable content
 * binding, profile/UNSPECIFIED-UNKNOWN/Rational preservation, transaction
 * rollback, non-visual streams.
 */
class SourceVisualDescriptionSnapshotIT {

    private static TestDatabaseSchema database;

    private static DSLContext dsl;
    private static JooqSourceVisualDescriptionSnapshotRepository repo;
    private static final MediaStreamId STREAM = new MediaStreamId("stream-1");
    private static final ArtifactId ARTIFACT_V1 = new ArtifactId("artifact-v1");

    @BeforeAll
    static void setup() throws Exception {
        database=TestDatabaseSchema.open(TestDatabaseConnectionProvider.fromControlledLaunch());
        dsl = database.dsl();
        database.migrateApplication();
        dsl.execute("insert into identity.tenant(id,name,created_at) values ('media-test','Media test',now())");
        for (String artifact : java.util.List.of("artifact-v1", "artifact-v2")) {
            dsl.execute("""
                    insert into artifacts.artifact(id, tenant_id, content_digest, byte_length, media_type,
                        artifact_kind, state, created_at)
                    values (?, 'media-test', repeat('a', 64), 1, 'VIDEO', 'SOURCE_MEDIA', 'AVAILABLE', now())
                    """, artifact);
        }
        dsl.execute("insert into media.media_stream (id, artifact_id, stream_index, stream_kind, timebase_num, timebase_den) "
                + "values ('stream-1', 'artifact-v1', 0, 'VIDEO', 1, 1000)");
        dsl.execute("insert into media.media_stream (id, artifact_id, stream_index, stream_kind, timebase_num, timebase_den) "
                + "values ('stream-audio', 'artifact-v1', 1, 'AUDIO', 1, 1000)");
        repo = new JooqSourceVisualDescriptionSnapshotRepository(dsl);
    }

    @AfterAll
    static void teardown() throws Exception {
        if (database!=null) database.close();
    }

    @BeforeEach
    void resetState() {
        dsl.execute("delete from media.source_visual_description_snapshot");
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
        repo.save(STREAM, ARTIFACT_V1, s1);
        Optional<SourceVisualDescription> loaded = repo.findByStreamAndArtifact(STREAM, ARTIFACT_V1);
        assertTrue(loaded.isPresent());
        assertEquals(s1, loaded.get(), "S1 == S2 exact semantic equality");
    }

    @Test
    void historicalReloadDoesNotInvokeNormalizerOrProvider() {
        SourceVisualDescription s1 = sample();
        repo.save(STREAM, ARTIFACT_V1, s1);
        Optional<SourceVisualDescription> loaded = repo.findByStreamAndArtifact(STREAM, ARTIFACT_V1);
        assertEquals(s1, loaded.get());
        // structural proof: the snapshot repository has zero probe/normalizer/ffprobe refs
        String src = loadRepoSource();
        assertFalse(src.contains("ffprobe") || src.contains("Probe") || src.contains("Normalizer"),
                "historical load path must not reference provider/normalizer");
    }

    @Test
    void immutableContentBinding() {
        repo.save(STREAM, ARTIFACT_V1, sample());
        ArtifactId artifactV2 = new ArtifactId("artifact-v2");
        SourceVisualDescription s2 = new SourceVisualDescription(
                new EncodedRasterExtent(1280, 720), PixelAspectRatio.square(),
                RasterSampleDescription.rgb(8, false),
                new ColorDescription.ParametricColorDescription(ColorPrimaries.WellKnown.BT709,
                        TransferCharacteristic.BT709, MatrixCoefficients.BT709, SignalRange.LIMITED),
                AlphaDescription.NO_ALPHA, SourceOrientation.NORMAL,
                new ScanDescription.Progressive(), Optional.empty());
        repo.save(STREAM, artifactV2, s2);
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
        repo.save(STREAM, ARTIFACT_V1, s);
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
        repo.save(STREAM, ARTIFACT_V1, unspecified);
        assertEquals(unspecified, repo.findByStreamAndArtifact(STREAM, ARTIFACT_V1).orElseThrow());
        assertThrows(IllegalStateException.class,
                () -> repo.save(STREAM, ARTIFACT_V1, unknown),
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
        repo.save(STREAM, ARTIFACT_V1, s);
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
        repo.save(STREAM, ARTIFACT_V1, noHdr);
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
        repo.save(STREAM, artifactV2, clOnly);
        SourceVisualDescription loaded = repo.findByStreamAndArtifact(STREAM, artifactV2).orElseThrow();
        assertTrue(loaded.staticHdrMetadata().isPresent());
        assertTrue(loaded.staticHdrMetadata().get().contentLight().isPresent());
        assertTrue(loaded.staticHdrMetadata().get().masteringDisplay().isEmpty());
    }

    @Test
    void transactionRollbackLeavesNoOrphan() {
        MediaStreamId ghost = new MediaStreamId("stream-ghost");
        var failure=assertThrows(org.jooq.exception.DataAccessException.class, () -> dsl.transaction(tx -> {
            tx.dsl().execute("insert into media.source_visual_description_snapshot "
                    + "(media_stream_id, artifact_id, canonical_payload) "
                    + "values (?, ?, ?)", ghost.value(), "artifact-v1", "x");
        }));
        assertEquals("23503",failure.sqlState(),"rollback must exercise the actual stream FK, not an obsolete-column error");
        Long count = dsl.fetchOne("select count(*) from media.source_visual_description_snapshot "
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
