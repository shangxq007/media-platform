package com.example.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.sandbox.BubblewrapSandboxCapabilityDetector;
import com.example.platform.sandbox.SandboxFailureCode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * P1-1A.7: the production bounded start probe really launches the configured executable inside the
 * sandbox. Process start only — never a media-capability or complete-dependency proof.
 */
class BubblewrapSandboxedExecutableStartProbeTest {

    private static final List<String> VERSION_ARGUMENTS = List.of("-version");
    private static final int PT_INTERP = 3;

    @TempDir Path temp;

    @Test
    void staticFfmpegStartsInTheProductionSandbox() {
        Path ffmpeg = Path.of("/tmp/ffmpeg-x264/bin/ffmpeg");
        requireSandbox();
        Assumptions.assumeTrue(Files.isExecutable(ffmpeg),
                "no isolated static ffmpeg in this environment; static start check skipped");

        var result = probe().probe(ffmpeg, VERSION_ARGUMENTS);

        assertThat(result.status()).isEqualTo(SandboxedExecutableStartProbe.Status.STARTED);
        assertThat(result.stdout()).contains("ffmpeg version ");
    }

    @Test
    void aDynamicallyLinkedExecutableStartsInTheProductionSandbox() {
        Path dynamic = Path.of("/usr/bin/true"); // dynamically linked against the bound system roots
        requireSandbox();
        Assumptions.assumeTrue(Files.isExecutable(dynamic), "no /usr/bin/true in this environment");

        var result = probe().probe(dynamic, VERSION_ARGUMENTS);

        assertThat(result.status()).isEqualTo(SandboxedExecutableStartProbe.Status.STARTED);
    }

    @Test
    void aDynamicallyLinkedFfmpegStartsInTheProductionSandbox() {
        Path ffmpeg = Path.of("/usr/bin/ffmpeg");
        requireSandbox();
        Assumptions.assumeTrue(Files.isExecutable(ffmpeg),
                "no system ffmpeg in this environment; dynamic start check skipped");

        var result = probe().probe(ffmpeg, VERSION_ARGUMENTS);

        assertThat(result.status()).isEqualTo(SandboxedExecutableStartProbe.Status.STARTED);
        assertThat(result.stdout()).contains("ffmpeg version ");
    }

    @Test
    void anExecutableReachedThroughASymlinkedDirectoryIsRejected() {
        // /bin is a symlink to usr/bin on this host; the production filesystem policy rejects it.
        requireSandbox();
        Assumptions.assumeTrue(Files.isSymbolicLink(Path.of("/bin")),
                "/bin is not a symlink here; policy rejection cannot be exercised");

        var result = probe().probe(Path.of("/bin/true"), VERSION_ARGUMENTS);

        assertThat(result.status()).isNotEqualTo(SandboxedExecutableStartProbe.Status.STARTED);
    }

    @Test
    void aNonExecutableArtifactDoesNotStart() throws IOException {
        requireSandbox();
        Path notAnExecutable = temp.resolve("text-artifact");
        Files.write(notAnExecutable, "not an executable".getBytes(StandardCharsets.UTF_8));
        notAnExecutable.toFile().setExecutable(true);

        var result = probe().probe(notAnExecutable, VERSION_ARGUMENTS);

        assertThat(result.status()).isNotEqualTo(SandboxedExecutableStartProbe.Status.STARTED);
    }

    @Test
    void aMissingExecutableDoesNotStart() {
        requireSandbox();

        var result = probe().probe(temp.resolve("absent-executable"), VERSION_ARGUMENTS);

        assertThat(result.status()).isNotEqualTo(SandboxedExecutableStartProbe.Status.STARTED);
    }

    @Test
    void aProbeThatExceedsItsTimeoutIsTerminatedAndNeverReportedAsStarted() {
        requireSandbox();
        Path sleep = Path.of("/usr/bin/sleep");
        Assumptions.assumeTrue(Files.isExecutable(sleep), "no /usr/bin/sleep in this environment");
        // A marker slow enough that only wall-clock termination can end the probe, and unique enough
        // that a residue search cannot match an unrelated process.
        long markerSeconds = 200_000L + ThreadLocalRandom.current().nextInt(700_000);
        var oneSecondProbe = new BubblewrapSandboxedExecutableStartProbe(
                temp.resolve("timeout-workspace"), Duration.ofSeconds(1), 262_144);

        long began = System.nanoTime();
        var result = oneSecondProbe.probe(sleep, List.of(Long.toString(markerSeconds)));
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - began).toMillis();

        // The wall-clock limit — not the ~200k-second sleep — ended the probe.
        assertThat(result.status()).isEqualTo(SandboxedExecutableStartProbe.Status.FAILED);
        assertThat(result.detail()).isEqualTo(SandboxFailureCode.PROCESS_TIMEOUT.name());
        assertThat(elapsedMillis).isLessThan(20_000L);
        // No residual process is left holding the marker: the launcher's containment terminated it.
        assertThat(awaitNoProcessWithMarker(Long.toString(markerSeconds), Duration.ofSeconds(5)))
                .as("timed-out probe left a residual child process")
                .isTrue();
    }

    @Test
    void anElfWithAnUnresolvableInterpreterDoesNotStart() throws IOException {
        requireSandbox();
        Path broken = temp.resolve("broken-dynamic");
        Files.write(broken, elfWithMissingInterpreter());
        broken.toFile().setExecutable(true);

        var result = probe().probe(broken, VERSION_ARGUMENTS);

        assertThat(result.status()).isNotEqualTo(SandboxedExecutableStartProbe.Status.STARTED);
    }

    private BubblewrapSandboxedExecutableStartProbe probe() {
        return new BubblewrapSandboxedExecutableStartProbe(
                temp.resolve("probe-workspace"), Duration.ofSeconds(20), 262_144);
    }

    private static void requireSandbox() {
        Assumptions.assumeTrue(
                BubblewrapSandboxCapabilityDetector.detect().launcher().isPresent(),
                "no working bubblewrap in this environment; sandbox start checks skipped");
    }

    private static boolean awaitNoProcessWithMarker(String marker, Duration budget) {
        long deadline = System.nanoTime() + budget.toNanos();
        while (hasProcessWithMarker(marker)) {
            if (System.nanoTime() > deadline) {
                return false;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return !hasProcessWithMarker(marker);
            }
        }
        return true;
    }

    private static boolean hasProcessWithMarker(String marker) {
        return ProcessHandle.allProcesses()
                .anyMatch(handle -> handle.info().commandLine().orElse("").contains(marker));
    }

    /** Minimal 64-bit ELF whose PT_INTERP names a loader that does not exist. */
    private static byte[] elfWithMissingInterpreter() {
        String interpreter = "/nonexistent/ld-sandbox-test.so";
        int headerSize = 64;
        int programHeaderSize = 56;
        int stringOffset = headerSize + programHeaderSize;
        ByteBuffer buffer = ByteBuffer
                .allocate(stringOffset + interpreter.length() + 1)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(0, (byte) 0x7F);
        buffer.put(1, (byte) 'E');
        buffer.put(2, (byte) 'L');
        buffer.put(3, (byte) 'F');
        buffer.put(4, (byte) 2); // ELFCLASS64
        buffer.put(5, (byte) 1); // little-endian
        buffer.putShort(0x10, (short) 2); // e_type = ET_EXEC
        buffer.putShort(0x12, (short) 62); // e_machine = EM_X86_64
        buffer.putInt(0x14, 1); // e_version
        buffer.putLong(0x20, headerSize); // e_phoff
        buffer.putShort(0x36, (short) programHeaderSize); // e_phentsize
        buffer.putShort(0x38, (short) 1); // e_phnum
        buffer.putInt(headerSize, PT_INTERP);
        buffer.putLong(headerSize + 8, stringOffset);
        buffer.putLong(headerSize + 32, interpreter.length() + 1); // p_filesz
        buffer.putLong(headerSize + 40, interpreter.length() + 1); // p_memsz
        buffer.position(stringOffset);
        buffer.put(interpreter.getBytes(StandardCharsets.UTF_8));
        buffer.put((byte) 0);
        return buffer.array();
    }
}
