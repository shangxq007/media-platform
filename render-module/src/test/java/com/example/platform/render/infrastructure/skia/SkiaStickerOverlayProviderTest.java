package com.example.platform.render.infrastructure.skia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.render.domain.interchange.TimelineScriptParser;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.domain.legacy.TimelineAssetRef;
import com.example.platform.render.domain.legacy.TimelineClip;
import com.example.platform.render.domain.legacy.TimelineStickerReader;
import com.example.platform.render.domain.legacy.TimelineTrack;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class SkiaStickerOverlayProviderTest {

    @Test
    void usesTimelineSourceAssetWhenTranscodeOutputIsAbsent(@TempDir Path storageRoot) throws Exception {
        String jobId = "job-with-source-only";
        String script = "{\"tracks\":[]}";
        String sourceUri = "localFsStorageProvider://assets/source.mp4";
        Path source = storageRoot.resolve("assets/source.mp4");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "source");

        TimelineSpec spec = new TimelineSpec(
                "timeline-1",
                "Source timeline",
                null,
                List.of(new TimelineTrack(
                        "video-1",
                        "Video",
                        TimelineTrack.TrackType.VIDEO,
                        0,
                        List.of(TimelineClip.of(
                                "clip-1", TimelineAssetRef.of("asset-1", sourceUri), 0, 0, 1)),
                        false,
                        false)),
                List.of(),
                null,
                1,
                Map.of());

        TimelineScriptParser parser = mock(TimelineScriptParser.class);
        TimelineStickerReader stickerReader = mock(TimelineStickerReader.class);
        StickerOverlayCompositor compositor = mock(StickerOverlayCompositor.class);
        when(parser.parse(script)).thenReturn(Optional.of(spec));
        when(parser.resolveLocalPath(sourceUri, storageRoot.toString())).thenReturn(source.toString());
        when(stickerReader.requiresSkiaOverlay(spec)).thenReturn(true);
        when(compositor.applyStickers(eq(source), any(Path.class), eq(spec)))
                .thenReturn(new StickerOverlayCompositor.ComposeResult(true, false, source, null));

        SkiaStickerOverlayProvider provider =
                new SkiaStickerOverlayProvider(parser, stickerReader, compositor);
        ReflectionTestUtils.setField(provider, "storageRoot", storageRoot.toString());

        assertFalse(Files.exists(
                storageRoot.resolve("artifacts").resolve(jobId).resolve("transcode-output.mp4")));

        provider.render(jobId, script, "default_1080p");

        verify(compositor).applyStickers(eq(source), any(Path.class), eq(spec));
        verify(parser).resolveLocalPath(sourceUri, storageRoot.toString());
        assertEquals("source", Files.readString(source));
    }
}
