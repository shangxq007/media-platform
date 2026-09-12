package com.example.platform.remoterender.app;

import com.example.platform.providerplugin.*;
import com.example.platform.workerfabric.domain.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** Host deployment composition only; never creates assignments or chooses a provider for a task. */
@Configuration
public class RemoteWorkerRuntimeConfiguration {
    @Bean(initMethod = "loadAndStart", destroyMethod = "close")
    ProviderPluginHost providerPluginHost(Environment environment) {
        return ProviderPluginHost.open(Path.of(environment.getRequiredProperty("app.remote-worker.plugins-directory")));
    }

    @Bean
    RemoteRenderService remoteRenderService(ProviderPluginHost host, Environment environment) throws java.io.IOException {
        var id = WorkerRuntimeId.of(environment.getRequiredProperty("app.remote-worker.runtime-id"));
        var incarnation = WorkerRuntimeIncarnationId.of(environment.getRequiredProperty("app.remote-worker.incarnation-id"));
        var kind = RuntimeLifecycleKind.valueOf(environment.getRequiredProperty("app.remote-worker.runtime-kind"));
        var runtime = kind == RuntimeLifecycleKind.REMOTE_RUNTIME ? WorkerRuntimeDescriptor.remote(id)
                : WorkerRuntimeDescriptor.local(id, kind, PhysicalHostId.of(environment.getRequiredProperty("app.remote-worker.physical-host-id")));
        Path root = Path.of(environment.getRequiredProperty("app.remote-worker.workspace-root")).toAbsolutePath().normalize();
        Files.createDirectories(root);
        Map<RuntimeSupportIdentifier, RuntimeSupportEvidence> support = new HashMap<>();
        Map<String, Path> executables = new HashMap<>();
        for (var contribution : host.catalog().contributions()) {
            Path executable = Path.of(environment.getRequiredProperty("app.remote-worker.executables." + contribution.pluginId())).toAbsolutePath().normalize();
            if (!Files.isExecutable(executable)) throw new IllegalStateException("Configured provider executable is unavailable");
            executables.put(contribution.pluginId(), executable);
            support.put(contribution.workerRuntimeSupportRequirement().supportIdentifier(),
                    new RuntimeSupportEvidence("provider-plugin", contribution.pluginId() + "@" + contribution.pluginVersion()));
        }
        var advertisement = new WorkerRuntimeSupportAdvertisement(id, kind, support);
        Duration timeout = Duration.parse(environment.getProperty("app.remote-worker.timeout", "PT1M"));
        long captureBytes = Long.parseLong(environment.getProperty("app.remote-worker.capture-bytes", "67108864"));
        return new RemoteRenderService(runtime, incarnation, advertisement, host.catalog(), (contribution, cancellation) -> {
            try {
                Path executionRoot = Files.createTempDirectory(root, "execution-");
                return new ProviderPluginRuntimeContext(executables.get(contribution.pluginId()), executionRoot,
                        timeout, captureBytes, cancellation);
            } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot create bounded runtime workspace", failure); }
        }, root);
    }
}
