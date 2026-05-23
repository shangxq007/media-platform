package com.example.platform.render.infrastructure.shaka;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShakaPackagingProviderTest {

    private ProcessToolRunner mockRunner;
    private ShakaPackagingProvider provider;

    @BeforeEach
    void setUp() {
        mockRunner = mock(ProcessToolRunner.class);
        ShakaPackagingProviderProperties props = new ShakaPackagingProviderProperties();
        props.setStubOnMissingBinary(true);
        provider = new ShakaPackagingProvider(mockRunner, new ShakaCommandFactory(), props);
    }

    @Test
    void stubManifestWhenPackagerFails(@TempDir Path tempDir) throws Exception {
        Path input = tempDir.resolve("input.mp4");
        Files.write(input, new byte[] {0, 0, 0, 8});
        Path outBase = tempDir.resolve("packaged");
        when(mockRunner.execute(any(ToolExecutionRequest.class)))
                .thenReturn(ToolExecutionResult.failed(127, "", "packager not found",
                        Instant.now(), Instant.now()));

        var result = provider.packageMedia(PackagingRequest.dash(input.toString(), outBase.toString(), 4));

        assertTrue(result.success());
        assertTrue(Files.exists(outBase.resolve("stream.mpd")));
    }
}
