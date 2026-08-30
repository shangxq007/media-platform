package com.example.platform.distribution;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.providerplugin.EmbeddedPluginExtractor;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlatformDistributionLauncherContainmentTest {

    @Test
    void externalWritableAndUnlistedPluginDirectoriesCannotLoadOrStart(@TempDir Path temp)
            throws Exception {
        Path writablePlugins = Files.createDirectories(temp.resolve("writable-plugins"));
        Path arbitraryJar = writablePlugins.resolve("arbitrary-external.jar");
        Files.writeString(arbitraryJar, "not a platform-bundled plugin");

        Path externalDirectory = Files.createDirectories(temp.resolve("external-plugins"));
        Path allInOne = Path.of(System.getProperty("distribution.executable.jar"));
        Path unlistedBundledBytes = EmbeddedPluginExtractor.extractSingle(
                allInOne, "embedded-plugins/", temp.resolve("unlisted-bundled-bytes"))
                .pluginJar().getParent();

        for (Path rejected : java.util.List.of(
                writablePlugins, externalDirectory, unlistedBundledBytes)) {
            assertThatThrownBy(() -> PlatformDistributionLauncher.main(new String[] {
                    "--plugins-dir=" + rejected
            })).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("external plugin directory loading is disabled");
        }

        org.assertj.core.api.Assertions.assertThat(Files.exists(arbitraryJar)).isTrue();
    }
}
