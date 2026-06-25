package com.example.platform.render.domain.xmp;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class XmpSidecarSerializationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void shouldSerializeXmpSidecarToJson() throws Exception {
        XmpAssetMetadata assetMeta = new XmpAssetMetadata(
                "asset_123", "video", "v7", "user_001", "proj_abc",
                "sha256:abc123", "s3://bucket/key.mp4",
                "asset://asset_123?v=v7", Instant.now(), Instant.now());

        XmpGovernanceMetadata gov = new XmpGovernanceMetadata(
                "internal", "enterprise-owned", "Org Inc.",
                List.of("edit", "publish"), "30-day", "L2",
                false, true, false, "reviewer_001");

        XmpSidecar sidecar = new XmpSidecar(XmpSidecar.SCHEMA_V1, assetMeta, null, null, gov);

        String json = MAPPER.writeValueAsString(sidecar);

        assertNotNull(json);
        assertTrue(json.contains("asset_123"));
        assertTrue(json.contains("internal"));
        assertTrue(json.contains("enterprise-owned"));
    }

    @Test
    void shouldCreateMinimalSidecar() {
        XmpAssetMetadata assetMeta = new XmpAssetMetadata(
                "asset_456", "audio", "v1", null, null,
                null, "file:///tmp/audio.wav",
                null, null, null);

        XmpSidecar sidecar = XmpSidecar.of(assetMeta);

        assertEquals(XmpSidecar.SCHEMA_V1, sidecar.schemaVersion());
        assertEquals("asset_456", sidecar.asset().assetId());
        assertNull(sidecar.ai());
        assertNull(sidecar.lineage());
        assertNull(sidecar.governance());
    }

    @Test
    void shouldCreateAiMetadata() {
        XmpAiMetadata ai = new XmpAiMetadata(
                "whisper-large-v3", "v3.1", "transcribe", null,
                "42", "beam", 7.5, "asr", 0.98, "approved");

        assertEquals("whisper-large-v3", ai.model());
        assertEquals("asr", ai.taskType());
        assertEquals(0.98, ai.confidence());
    }

    @Test
    void shouldCreateLineageMetadata() {
        Instant now = Instant.now();
        XmpLineageMetadata lineage = new XmpLineageMetadata(
                "asset_source", List.of("asset_dep_1", "asset_dep_2"),
                "transcode", "wf_123", "run_456",
                "user_001", "sha256:params", now);

        assertEquals("asset_source", lineage.sourceAsset());
        assertEquals(2, lineage.derivedFrom().size());
        assertEquals("wf_123", lineage.workflowId());
    }
}
