package com.example.platform.distribution;

import com.example.platform.extension.app.PluginDescriptorValidator;
import com.example.platform.extension.app.PluginHealthRegistry;
import com.example.platform.extension.app.PluginRegistryImpl;
import com.example.platform.providerplugin.EmbeddedPluginExtractor;
import com.example.platform.providerplugin.ProviderPluginHost;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Executable modular/all-in-one launcher that always converges on the same PF4J host. */
public final class PlatformDistributionLauncher {

    private PlatformDistributionLauncher() {}

    public static void main(String[] args) throws Exception {
        try (PluginDirectoryResolution resolution = resolvePluginDirectory(args)) {
            PluginRegistryImpl registry = new PluginRegistryImpl(
                    new PluginDescriptorValidator(), new PluginHealthRegistry());
            try (ProviderPluginHost host = new ProviderPluginHost(resolution.pluginDirectory(), registry)) {
                var contributions = host.loadAndStart().contributions();
                if (contributions.isEmpty()) {
                    throw new IllegalStateException("No typed provider plugin contribution loaded");
                }
                contributions.forEach(contribution -> System.out.println(
                        "PROVIDER_PLUGIN=" + contribution.pluginId() + "@" + contribution.pluginVersion()
                                + " PROVIDER=" + contribution.providerBindingPin().providerId().value()
                                + " IMPLEMENTATION="
                                + contribution.providerBindingPin().providerImplementationId().value()));
            }
        }
    }

    private static PluginDirectoryResolution resolvePluginDirectory(String[] args) throws Exception {
        for (String arg : args) {
            if (arg.startsWith("--plugins-dir=")) {
                throw new IllegalArgumentException(
                        "external plugin directory loading is disabled until immutable digest authority exists");
            }
        }
        Path outer = launcherPath();
        Path controlled = Files.createTempDirectory("media-platform-embedded-plugins-");
        try {
            Path directory = EmbeddedPluginExtractor.extractSingle(
                    outer, "embedded-plugins/", controlled).pluginJar().getParent();
            return new PluginDirectoryResolution(directory, controlled);
        } catch (Exception failure) {
            deleteTree(controlled);
            throw failure;
        }
    }

    private static Path launcherPath() throws URISyntaxException {
        String classPath = System.getProperty("java.class.path", "");
        if (!classPath.isBlank()) {
            Path firstEntry = Path.of(classPath.split(
                    java.util.regex.Pattern.quote(java.io.File.pathSeparator), 2)[0])
                    .toAbsolutePath().normalize();
            if (Files.isRegularFile(firstEntry)) {
                return firstEntry;
            }
        }
        return Path.of(PlatformDistributionLauncher.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
    }

    private static void deleteTree(Path root) throws Exception {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record PluginDirectoryResolution(Path pluginDirectory, Path cleanupRoot)
            implements AutoCloseable {
        @Override
        public void close() throws Exception {
            deleteTree(cleanupRoot);
        }
    }
}
