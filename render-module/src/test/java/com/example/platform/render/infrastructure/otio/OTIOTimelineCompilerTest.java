package com.example.platform.render.infrastructure.otio;

import com.example.platform.render.infrastructure.RenderJob;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OTIOTimelineCompilerTest {

    private final OTIOTimelineCompiler compiler = new OTIOTimelineCompiler();

    @Test
    void compilesMinimalOtio() {
        String otioJson = "{\"tracks\":[]}";
        Map<String, Object> metadata = Map.of("bluepulse", Map.of(
                "schemaVersion", "1.0.0",
                "projectId", "project-001",
                "timelineId", "timeline-001"
        ));

        OTIOTimelineSummary summary = compiler.compile(otioJson, metadata);
        assertNotNull(summary);
        assertEquals("1.0.0", summary.schemaVersion());
        assertEquals("project-001", summary.projectId());
    }

    @Test
    void warnsOnMissingSchemaVersion() {
        String otioJson = "{\"tracks\":[]}";
        Map<String, Object> metadata = Map.of("bluepulse", Map.of(
                "projectId", "project-001"
        ));

        OTIOTimelineSummary summary = compiler.compile(otioJson, metadata);
        assertNotNull(summary);
        assertEquals("1.0.0", summary.schemaVersion());
    }

    @Test
    void extractsCaptionRefs() {
        String otioJson = "{\"tracks\":[]}";
        Map<String, Object> metadata = Map.of("bluepulse", Map.of(
                "schemaVersion", "1.0.0",
                "projectId", "project-001",
                "captions", List.of(Map.of(
                        "id", "cap-001",
                        "assetRef", "caption-asset-001",
                        "startTime", 1.0,
                        "endTime", 5.0
                ))
        ));

        OTIOTimelineSummary summary = compiler.compile(otioJson, metadata);
        assertNotNull(summary.captionRefs());
        assertEquals(1, summary.captionRefs().size());
        assertEquals("cap-001", summary.captionRefs().getFirst().captionId());
    }

    @Test
    void extractsFontRefs() {
        String otioJson = "{\"tracks\":[]}";
        Map<String, Object> metadata = Map.of("bluepulse", Map.of(
                "schemaVersion", "1.0.0",
                "projectId", "project-001",
                "fonts", List.of(Map.of(
                        "refId", "font-ref-001",
                        "assetId", "font-asset-001",
                        "fontFamily", "NotoSansCJK",
                        "fontWeight", "700",
                        "fontStyle", "bold"
                ))
        ));

        OTIOTimelineSummary summary = compiler.compile(otioJson, metadata);
        assertNotNull(summary.fontRefs());
        assertEquals(1, summary.fontRefs().size());
        assertEquals("NotoSansCJK", summary.fontRefs().getFirst().fontFamily());
    }

    @Test
    void generatesRenderJob() {
        String otioJson = "{\"tracks\":[]}";
        Map<String, Object> metadata = Map.of("bluepulse", Map.of(
                "schemaVersion", "1.0.0",
                "projectId", "project-001",
                "timelineId", "timeline-001",
                "captions", List.of(Map.of(
                        "id", "cap-001",
                        "assetRef", "caption-asset-001",
                        "startTime", 1.0,
                        "endTime", 5.0
                )),
                "renderHints", Map.of(
                        "outputFormat", "mp4",
                        "outputWidth", 1920,
                        "outputHeight", 1080,
                        "outputFps", 30
                )
        ));

        OTIOTimelineSummary summary = compiler.compile(otioJson, metadata);
        assertNotNull(summary);

        RenderJob job = compiler.generateRenderJob(summary, "production");
        assertNotNull(job);
        assertEquals("captioned_video_export", job.jobType());
        assertTrue(job.requiredCapabilities().contains("caption_effects"));
    }

    @Test
    void doesNotGenerateProviderCommands() {
        String otioJson = "{\"tracks\":[]}";
        Map<String, Object> metadata = Map.of("bluepulse", Map.of(
                "schemaVersion", "1.0.0",
                "projectId", "project-001"
        ));

        OTIOTimelineSummary summary = compiler.compile(otioJson, metadata);
        assertNotNull(summary);
        assertTrue(summary.videoTracks().getFirst().clips().isEmpty());
    }
}
