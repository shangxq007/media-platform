package com.example.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** P1-1A.6 Gate 3: the executable dependency probe classifies ELF linkage and fails closed. */
class ProviderExecutableDependencyProbeTest {

    private static final int PT_LOAD = 1;
    private static final int PT_DYNAMIC = 2;

    @TempDir Path temp;

    @Test
    void staticExecutableHasNoExternalRuntimeDependency() throws IOException {
        Path executable = write("static", elf64(PT_LOAD, PT_LOAD));

        var result = new ProviderExecutableDependencyProbe().probe(executable);

        assertThat(result.status())
                .isEqualTo(ProviderExecutableDependencyProbe.Status.STATIC_SELF_CONTAINED);
    }

    @Test
    void dynamicExecutableCannotBackAnEmptyDependencySet() throws IOException {
        Path executable = write("dynamic", elf64(PT_LOAD, PT_DYNAMIC));

        var result = new ProviderExecutableDependencyProbe().probe(executable);

        assertThat(result.status())
                .isEqualTo(ProviderExecutableDependencyProbe.Status.DYNAMIC_DEPENDENCIES_PRESENT);
    }

    @Test
    void nonElfAndMissingArtifactsAreUnknownNeverEmpty() throws IOException {
        Path notElf = write("not-elf", "just text".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(new ProviderExecutableDependencyProbe().probe(notElf).status())
                .isEqualTo(ProviderExecutableDependencyProbe.Status.UNKNOWN);
        assertThat(new ProviderExecutableDependencyProbe()
                        .probe(temp.resolve("missing"))
                        .status())
                .isEqualTo(ProviderExecutableDependencyProbe.Status.UNKNOWN);
    }

    @Test
    void classifiesTheRealVerifiedStaticFfmpegBuild() {
        Path ffmpeg = Path.of("/tmp/ffmpeg-x264/bin/ffmpeg");
        Assumptions.assumeTrue(Files.isExecutable(ffmpeg),
                "no isolated static ffmpeg in this environment; real-artifact check skipped");

        var result = new ProviderExecutableDependencyProbe().probe(ffmpeg);

        assertThat(result.status())
                .isEqualTo(ProviderExecutableDependencyProbe.Status.STATIC_SELF_CONTAINED);
    }

    @Test
    void classifiesTheSystemFfmpegAsDynamic() {
        Path ffmpeg = Path.of("/usr/bin/ffmpeg");
        Assumptions.assumeTrue(Files.isExecutable(ffmpeg),
                "no system ffmpeg in this environment; discriminating check skipped");

        var result = new ProviderExecutableDependencyProbe().probe(ffmpeg);

        assertThat(result.status())
                .isEqualTo(ProviderExecutableDependencyProbe.Status.DYNAMIC_DEPENDENCIES_PRESENT);
    }

    private Path write(String name, byte[] content) throws IOException {
        Path path = temp.resolve(name);
        Files.write(path, content);
        path.toFile().setExecutable(true);
        return path;
    }

    /** Minimal 64-bit little-endian ELF header plus program headers of the given types. */
    private static byte[] elf64(int... programHeaderTypes) {
        int headerSize = 64;
        int programHeaderSize = 56;
        int tableSize = programHeaderSize * programHeaderTypes.length;
        ByteBuffer buffer = ByteBuffer.allocate(headerSize + tableSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(0, (byte) 0x7F);
        buffer.put(1, (byte) 'E');
        buffer.put(2, (byte) 'L');
        buffer.put(3, (byte) 'F');
        buffer.put(4, (byte) 2); // ELFCLASS64
        buffer.put(5, (byte) 1); // little-endian
        buffer.putLong(0x20, headerSize); // e_phoff
        buffer.putShort(0x36, (short) programHeaderSize); // e_phentsize
        buffer.putShort(0x38, (short) programHeaderTypes.length); // e_phnum
        for (int index = 0; index < programHeaderTypes.length; index++) {
            buffer.putInt(headerSize + index * programHeaderSize, programHeaderTypes[index]);
        }
        return buffer.array();
    }
}
