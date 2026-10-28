package com.example.platform.runtime;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.providerplugin.ProviderPluginCatalog;
import com.example.platform.providerplugin.ProviderPluginContribution;
import com.example.platform.providerplugin.ProviderPluginHost;
import com.example.platform.providerplugin.InProcessProviderPlugins;
import com.example.platform.providerplugin.bmf.BmfProviderExtension;
import com.example.platform.providerplugin.ProviderPluginRuntimeContext;
import com.example.platform.sandbox.SandboxCancellation;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeRuntimeBinding;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * P2-5b-2b-1a: worker-scoped provider plugin wiring.
 *
 * <p>Loads the typed PF4J provider contributions into the canonical
 * {@link ProviderPluginHost}/{@link ProviderPluginCatalog} and exposes the catalog-derived
 * {@code Map<ProviderBindingPin, ProviderNativeRuntimeBinding<?>>} the closed loop resolves its
 * runtime binding from. It mirrors the existing remote worker composition
 * ({@code RemoteWorkerRuntimeConfiguration}) and the worker-profile bean style of
 * {@code ArtifactMaterializationConfiguration}: nothing is invented, every value comes from the
 * worker's own configuration, and the worker role is required.
 *
 * <p><b>Fail closed.</b> A missing plugin directory stops context startup. A plugin whose
 * contribution cannot produce a binding stops startup as well, and two contributions claiming one
 * {@link ProviderBindingPin} is rejected rather than silently overwritten. An empty catalog yields an
 * empty map — the orchestrator then fails closed at execution time for the missing exact binding.
 *
 * <p>The worker is runtime-grouped (one ffmpeg runtime), so every contribution's runtime context uses
 * the guard-validated {@code platform.ffmpeg-worker.sandbox.ffmpeg} executable. The binding map is
 * worker-scoped, its immutable bindings are copied with each execution's cancellation signal; the default remains
 * {@link SandboxCancellation#never()} and never carries another execution's mutable cancellation state.
 */
@Configuration
@ConditionalOnExpression(
        "'${platform.runtime.role:}' == 'WORKER' or '${platform.runtime.role:}' == 'COMBINED'")
public class FfmpegWorkerPluginConfiguration {

    @Bean(initMethod = "loadAndStart", destroyMethod = "close")
    ProviderPluginHost providerPluginHost(
            @Value("${platform.ffmpeg-worker.plugins-directory}") String pluginsDirectory,
            org.springframework.beans.factory.ObjectProvider<com.example.platform.extension.api.port.PluginRegistrationPort> registries,
            org.springframework.core.env.Environment environment) {
        Path directory = Path.of(pluginsDirectory).toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException(
                    "ffmpeg worker plugin directory is unavailable: " + directory);
        }
        // COMBINED publishes discovery through the same platform registry that its HTTP
        // composition API reads. A headless WORKER keeps the canonical standalone registry.
        var registry = "COMBINED".equals(environment.getProperty("platform.runtime.role"))
                ? registries.getObject()
                : registries.getIfAvailable(com.example.platform.extension.api.port.PluginRegistries::standalone);
        return new ProviderPluginHost(directory, registry);
    }

    @Bean
    BmfProviderExtension bmfProviderExtension() {
        return new BmfProviderExtension();
    }

    /**
     * The catalog is the host's own instance plus the in-process infrastructure contributions (BMF
     * is an embedded framework, not a plugin JAR), so the worker keeps exactly one catalog.
     */
    @Bean
    ProviderPluginCatalog providerPluginCatalog(
            ProviderPluginHost providerPluginHost, BmfProviderExtension bmfProviderExtension) {
        return InProcessProviderPlugins.catalogWith(
                providerPluginHost.catalog(), List.of(bmfProviderExtension));
    }

    @Bean
    Map<ProviderBindingPin, ProviderNativeRuntimeBinding<?>> providerNativeRuntimeBindings(
            ProviderPluginCatalog providerPluginCatalog,
            @Value("${platform.ffmpeg-worker.sandbox.ffmpeg:/usr/bin/ffmpeg}") String ffmpeg,
            @Value("${platform.ffmpeg-worker.workspace-root}") String workspaceRoot,
            @Value("${platform.ffmpeg-worker.plugin-timeout:PT1M}") String timeout,
            @Value("${platform.ffmpeg-worker.plugin-capture-bytes:67108864}") String captureBytes) {
        Path executable = Path.of(ffmpeg).toAbsolutePath().normalize();
        Path workspace = Path.of(workspaceRoot).toAbsolutePath().normalize();
        long captureBudget = Long.parseLong(captureBytes);
        Map<ProviderBindingPin, ProviderNativeRuntimeBinding<?>> bindings = new LinkedHashMap<>();
        for (ProviderPluginContribution contribution : providerPluginCatalog.contributions()) {
            // A contribution whose lowering is declared unsupported has no executable binding: it
            // stays registry-visible (the Stage-1 kernel refuses it) and contributes nothing here.
            if (contribution.providerStaticCompatibility().loweringSupport()
                    == com.example.platform.execution.compatibility.ProviderStaticCompatibility
                            .LoweringSupport.UNSUPPORTED) {
                continue;
            }
            ProviderNativeRuntimeBinding<?> binding = contribution.createRuntimeBinding(
                    new ProviderPluginRuntimeContext(executable, workspace, Duration.parse(timeout),
                            captureBudget, SandboxCancellation.never()));
            if (bindings.putIfAbsent(contribution.providerBindingPin(), binding) != null) {
                throw new IllegalStateException(
                        "duplicate provider binding pin: " + contribution.providerBindingPin());
            }
        }
        return Map.copyOf(bindings);
    }
}
