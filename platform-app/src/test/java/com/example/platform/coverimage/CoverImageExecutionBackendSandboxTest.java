package com.example.platform.coverimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.example.platform.sandbox.execution.ExecutionRequest;
import com.example.platform.sandbox.execution.TaskCapability;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Real bubblewrap verification for the cover sandbox profile (COVER-PROVIDER-001 defect 3).
 *
 * <p>Executes the exact profile shape of {@link CoverImageExecutionBackend} against the repository's
 * video fixture and asserts that the produced cover bytes are visible to the parent process, non-empty
 * and a real PNG. Before the fix the profile bound only {@code /} read-only plus a private
 * {@code /tmp} tmpfs, so ffmpeg encoded the frame and then failed to hand any output back.
 *
 * <p>Requires the pinned host toolchain ({@code /usr/bin/bwrap} + {@code /usr/bin/ffmpeg}); when it is
 * absent the test is skipped explicitly rather than silently passing.
 */
class CoverImageExecutionBackendSandboxTest {

    private static final Path BWRAP = Path.of("/usr/bin/bwrap");
    private static final Path FFMPEG = Path.of("/usr/bin/ffmpeg");

    @Test
    void bwrapProfileExecutesFfmpegAndHandsTheCoverBytesBack(@TempDir Path temp) throws Exception {
        assumeTrue(Files.isExecutable(BWRAP), "bwrap is not installed on this host");
        assumeTrue(Files.isExecutable(FFMPEG), "ffmpeg is not installed on this host");

        Path input = Path.of(getClass().getResource("/render-output-fixture.mp4").toURI());
        Path workRoot = temp.resolve("cover-image-work");
        Path output = workRoot.resolve("cover-key-sandbox").resolve("provider-output").resolve("cover.png");
        Files.createDirectories(output.getParent());

        var backend = new CoverImageExecutionBackend(
                BWRAP.toString(), FFMPEG.toString(), workRoot.toString());

        var result = backend.execute(ExecutionRequest.of(
                "cover-image:cover-key-sandbox",
                "cover-key-sandbox",
                TaskCapability.COVER_IMAGE,
                List.of("-hide_banner", "-nostdin", "-y",
                        "-ss", "0",
                        "-i", input.toString(),
                        "-frames:v", "1",
                        "-vf", "scale=320:-2",
                        output.toString()),
                120));

        assertThat(result.success())
                .as("sandbox execution must succeed; stderr=%s", result.stderr())
                .isTrue();
        assertThat(result.outputFiles()).contains(output.toString());
        assertThat(Files.isRegularFile(output)).isTrue();
        byte[] bytes = Files.readAllBytes(output);
        assertThat(bytes).hasSizeGreaterThan(0);
        assertThat(new byte[] {bytes[0], bytes[1], bytes[2], bytes[3]})
                .containsExactly((byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47);
    }
}
