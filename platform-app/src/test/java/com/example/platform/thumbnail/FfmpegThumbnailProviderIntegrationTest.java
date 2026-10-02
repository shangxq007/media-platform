package com.example.platform.thumbnail;

import com.example.platform.contract.media.ThumbnailContracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.frameextract.FfmpegCpuProvider;
import com.example.platform.frameextract.FrameExtractExecutionAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

class FfmpegThumbnailProviderIntegrationTest {
    @TempDir Path temp;

    @Test
    @Tag("render-integration")
    void realSandboxedFfmpegExtractionProducesReadableImage() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/usr/bin/ffmpeg")));
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/usr/bin/ffprobe")));
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/usr/bin/bwrap")));
        Path source = temp.resolve("source.mp4");
        new ProcessBuilder("/usr/bin/ffmpeg", "-y", "-f", "lavfi", "-i", "color=c=blue:s=320x180:d=1", "-pix_fmt", "yuv420p", source.toString()).redirectErrorStream(true).start().waitFor();
        var request = new ThumbnailContracts.Request("tenant", "project", "asset", .25, "jpeg", 160, 80, "real-provider");
        var provider = new FfmpegCpuProvider("/usr/bin/ffmpeg", "/usr/bin/ffprobe");
        var result = FrameExtractExecutionAdapter.of(provider, temp)
                .extract(ThumbnailContracts.CAPABILITY, request, Files.readAllBytes(source),
                        new AtomicBoolean(false)::get);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.bytes()).startsWith((byte) 0xff, (byte) 0xd8, (byte) 0xff);
    }
}
