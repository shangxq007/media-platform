package com.example.platform.runtime;

import com.example.platform.artifact.domain.ArtifactCommitService;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.execution.binding.BoundGraphInputStore;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.runtime.mediatask.MediaTaskActivity;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.provider.StorageProvider;
import com.example.platform.workerfabric.domain.AtomicAssignmentGrantBoundary;
import com.example.platform.workerfabric.domain.CompletionAuthorityPort;
import com.example.platform.workerfabric.domain.NativePullAdmissionPort;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeRuntimeBinding;
import com.example.platform.workerfabric.reuse.ArtifactMaterializerPort;
import com.example.platform.workerfabric.reuse.ArtifactOutputCommitOrchestrator;
import com.example.platform.workerfabric.reuse.ArtifactReuseIndexPort;
import com.example.platform.workerfabric.reuse.ArtifactReuseResolver;
import com.example.platform.workerfabric.reuse.FencedReuseCompletionOrchestrator;
import com.example.platform.workerfabric.reuse.OutputStagingArea;
import com.example.platform.workerfabric.reuse.Phase16RuntimeMetrics;
import com.example.platform.workerfabric.reuse.RuntimeClosedLoopOrchestrator;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * P2-5b-2b-1b: worker-scoped assembly of the runtime closed loop and the media task activity.
 *
 * <p>Every collaborator is built from an existing bean of this worker context — nothing is stubbed
 * and no value is invented:
 * <ul>
 *   <li>the reuse authorities ({@code JooqArtifactReuseIndex}, {@code ArtifactQueryService},
 *       {@code ArtifactCommitService}, {@code JooqExecutionAuthorityBoundary}) are the worker's
 *       existing repositories;</li>
 *   <li>the artifact materializer is the worker's existing {@link ArtifactMaterializerPort} bean
 *       (assembled by the capability materialization configuration over the same storage providers),
 *       so the process keeps exactly one materializer;</li>
 *   <li>the runtime binding map is the catalog-derived bean from
 *       {@link FfmpegWorkerPluginConfiguration};</li>
 *   <li>the staging root and the storage-provider map are worker configuration.</li>
 * </ul>
 *
 * <p><b>Fail closed.</b> Two storage providers claiming one {@link StorageProviderId} stop context
 * startup, and a missing {@link ArtifactMaterializerPort} (i.e. a worker that cannot materialize
 * inputs) fails the orchestrator bean rather than silently running without materialization. Every
 * bean is worker-role scoped.
 */
@Configuration
@ConditionalOnProperty(name = "platform.runtime.role", havingValue = "WORKER")
public class FfmpegWorkerRuntimeConfiguration {

    /**
     * The worker's storage providers keyed by the id they claim; a duplicate id is rejected instead
     * of silently shadowing a provider.
     */
    @Bean
    Map<StorageProviderId, StorageProvider> workerStorageProviders(
            List<StorageProvider> storageProviders) {
        Map<StorageProviderId, StorageProvider> providers = new LinkedHashMap<>();
        for (StorageProvider provider : storageProviders) {
            StorageProviderId id = provider.providerId();
            if (providers.putIfAbsent(id, provider) != null) {
                throw new IllegalStateException("duplicate storage provider id: " + id.value());
            }
        }
        if (providers.isEmpty()) {
            throw new IllegalStateException(
                    "the ffmpeg worker requires at least one registered StorageProvider");
        }
        return Map.copyOf(providers);
    }

    @Bean
    Phase16RuntimeMetrics phase16RuntimeMetrics(MeterRegistry meterRegistry) {
        return new Phase16RuntimeMetrics(meterRegistry);
    }

    @Bean
    ArtifactReuseResolver artifactReuseResolver(
            ArtifactReuseIndexPort artifactReuseIndex, ArtifactQueryService artifacts) {
        return new ArtifactReuseResolver(artifactReuseIndex, artifacts);
    }

    @Bean
    OutputStagingArea outputStagingArea(
            @Value("${platform.ffmpeg-worker.staging-root}") String stagingRoot) {
        return new OutputStagingArea(Path.of(stagingRoot).toAbsolutePath().normalize());
    }

    @Bean
    ArtifactOutputCommitOrchestrator artifactOutputCommitOrchestrator(
            ArtifactCommitService artifactCommitService,
            Map<StorageProviderId, StorageProvider> workerStorageProviders,
            Phase16RuntimeMetrics phase16RuntimeMetrics) {
        return new ArtifactOutputCommitOrchestrator(
                artifactCommitService, workerStorageProviders, phase16RuntimeMetrics);
    }

    @Bean
    FencedReuseCompletionOrchestrator fencedReuseCompletionOrchestrator(
            ArtifactReuseIndexPort artifactReuseIndex,
            CompletionAuthorityPort completionAuthority) {
        return new FencedReuseCompletionOrchestrator(artifactReuseIndex, completionAuthority);
    }

    @Bean
    RuntimeClosedLoopOrchestrator runtimeClosedLoopOrchestrator(
            ArtifactReuseResolver artifactReuseResolver,
            ArtifactMaterializerPort artifactMaterializerPort,
            OutputStagingArea outputStagingArea,
            ArtifactOutputCommitOrchestrator artifactOutputCommitOrchestrator,
            FencedReuseCompletionOrchestrator fencedReuseCompletionOrchestrator,
            // The binding map is one worker-scoped bean, not a map-of-beans injection.
            @Qualifier("providerNativeRuntimeBindings")
            Map<ProviderBindingPin, ProviderNativeRuntimeBinding<?>> providerNativeRuntimeBindings,
            Phase16RuntimeMetrics phase16RuntimeMetrics) {
        return new RuntimeClosedLoopOrchestrator(
                artifactReuseResolver,
                artifactMaterializerPort,
                outputStagingArea,
                artifactOutputCommitOrchestrator,
                fencedReuseCompletionOrchestrator,
                providerNativeRuntimeBindings,
                phase16RuntimeMetrics);
    }

    @Bean
    NativePullAdmissionPort nativePullAdmissionPort(
            AtomicAssignmentGrantBoundary atomicAssignmentGrantBoundary) {
        return new NativePullAdmissionPort(atomicAssignmentGrantBoundary);
    }

    @Bean
    MediaTaskActivity mediaTaskActivity(BoundGraphInputStore boundGraphInputStore) {
        return new MediaTaskActivity(boundGraphInputStore);
    }
}
