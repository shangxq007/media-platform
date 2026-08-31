package com.example.platform.studio.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StudioArchitectureGuardTest {
    @Test
    void productionBuildAndDiffRemainInsideFrozenStudioBoundary() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("settings.gradle.kts"))) root = root.getParent();
        assertThat(root).as("repository root").isNotNull();
        var process = new ProcessBuilder("bash", "scripts/guards/studio-domain-boundary-guard.sh")
                .directory(root.toFile()).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
        assertThat(output).contains("STUDIO_ARCHITECTURE_GUARD=PASS");
        assertThat(output.lines().filter(line -> line.endsWith("_COUNT=0")).count()).isGreaterThanOrEqualTo(20);
    }
}
