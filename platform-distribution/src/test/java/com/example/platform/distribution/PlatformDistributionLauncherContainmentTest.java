package com.example.platform.distribution;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlatformDistributionLauncherContainmentTest {

    @Test
    void arbitraryExternalPluginDirectoryCannotLoadOrStart(@TempDir Path writablePlugins)
            throws Exception {
        Path arbitraryJar = writablePlugins.resolve("arbitrary-external.jar");
        Files.writeString(arbitraryJar, "not a platform-bundled plugin");

        assertThatThrownBy(() -> PlatformDistributionLauncher.main(new String[] {
                "--plugins-dir=" + writablePlugins
        })).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("external plugin directory loading is disabled");

        org.assertj.core.api.Assertions.assertThat(Files.exists(arbitraryJar)).isTrue();
    }
}
