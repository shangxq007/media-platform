package com.example.platform.render.infrastructure.bento4;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.render.infrastructure.gpac.PackagingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class Bento4PackagingProviderTest {

    private ProcessToolRunner mockRunner;
    private Bento4PackagingProvider provider;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        mockRunner = mock(ProcessToolRunner.class);
        Bento4CommandFactory factory = new Bento4CommandFactory();
        Bento4PackagingProviderProperties props = new Bento4PackagingProviderProperties();
        props.setMp4fragmentBin("mp4fragment");
        props.setMp4dashBin("mp4dash");
        provider = new Bento4PackagingProvider(mockRunner, factory, props);
    }

    @Test
    void packageDashReturnsManifestOnSuccess(@TempDir Path tempDir) throws Exception {
        Path input = tempDir.resolve("input.mp4");
        Files.write(input, new byte[] {0, 0, 0, 8});
        Path outBase = tempDir.resolve("packaged");
        Files.createDirectories(outBase);
        Files.write(outBase.resolve("stream.mpd"), "<MPD/>".getBytes());

        Instant now = Instant.now();
        when(mockRunner.execute(any(ToolExecutionRequest.class)))
                .thenReturn(ToolExecutionResult.success(0, "ok", "", now, now.plusMillis(50)));

        var result = provider.packageMedia(PackagingRequest.dash(input.toString(), outBase.toString(), 4));

        assertTrue(result.success());
        assertTrue(result.manifestUri().endsWith("stream.mpd"));
        assertEquals("dash", result.format());
        verify(mockRunner, times(2)).execute(any());
    }

    @Test
    void packageFailsWhenFragmentFails(@TempDir Path tempDir) throws Exception {
        Path input = tempDir.resolve("input.mp4");
        Files.write(input, new byte[] {0, 0, 0, 8});
        Instant now = Instant.now();
        when(mockRunner.execute(any(ToolExecutionRequest.class)))
                .thenReturn(ToolExecutionResult.failed(1, "", "fragment error", now, now.plusMillis(10)));

        var result = provider.packageMedia(PackagingRequest.dash(input.toString(), tempDir.toString(), 4));

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("mp4fragment"));
    }

    @Test
    void supportedFormatsIncludeDashDrm() {
        List<String> formats = provider.getSupportedFormats();
        assertTrue(formats.contains("dash"));
        assertTrue(formats.contains("dash_drm"));
    }
}
