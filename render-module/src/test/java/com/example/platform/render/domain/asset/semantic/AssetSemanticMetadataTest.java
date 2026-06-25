package com.example.platform.render.domain.asset.semantic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class AssetSemanticMetadataTest {

    @Test
    void emptyMetadataShouldHaveDefaults() {
        var meta = AssetSemanticMetadata.empty("asset_123", "v1");

        assertEquals("asset_123", meta.assetId());
        assertEquals("v1", meta.assetVersion());
        assertEquals(AssetSemanticMetadata.EnrichmentStatus.PENDING, meta.status());
        assertTrue(meta.transcripts().isEmpty());
        assertTrue(meta.scenes().isEmpty());
        assertTrue(meta.objects().isEmpty());
    }

    @Test
    void transcriptShouldCreateWithProvider() {
        var segment = new TranscriptSegment(0, 5000, "SPEAKER_1", "Hello world");
        var transcript = Transcript.of("whisper-v3", "en", "Hello world", 0.95, List.of(segment));

        assertEquals("whisper-v3", transcript.provider());
        assertEquals(0.95, transcript.confidence());
        assertEquals(1, transcript.segments().size());
    }

    @Test
    void sceneShouldHaveIdAndLabel() {
        var scene = Scene.of("opening", 0, 10000, 0.92);

        assertNotNull(scene.sceneId());
        assertEquals("opening", scene.label());
        assertEquals(0, 0, scene.startTimeMs());
        assertEquals(10000, scene.endTimeMs());
    }

    @Test
    void embeddingReferenceShouldNotStoreVector() {
        var emb = new EmbeddingReference("emb_1", "clip-vit", 512, "s3://vectors/emb_1.npy");

        assertEquals(512, emb.vectorDimension());
        assertNotNull(emb.storageUri());
    }
}
