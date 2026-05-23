package com.example.platform.render.infrastructure.natron;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NatronBatchScriptGeneratorTest {

    @Test
    void generatesScriptWithAbsolutePaths(@TempDir Path tempDir) throws Exception {
        Path input = tempDir.resolve("in.mp4");
        Files.writeString(input, "x");
        Path output = tempDir.resolve("out.mp4");

        NatronPocJob job = new NatronPocJob(
                "video.natron_vignette",
                input.toString(),
                output.toString(),
                java.util.Map.of("intensity", 0.8));

        NatronBatchScriptGenerator generator = new NatronBatchScriptGenerator();
        Path script = generator.generate(job, tempDir.resolve("natron"));

        String content = Files.readString(script);
        assertTrue(content.contains(input.toString().replace("\\", "\\\\"))
                || content.contains(input.toString()));
        assertTrue(content.contains("MyReader"));
        assertTrue(content.contains("MyWriter"));
    }
}
