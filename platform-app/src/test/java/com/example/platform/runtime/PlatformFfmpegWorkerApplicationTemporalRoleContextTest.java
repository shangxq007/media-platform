package com.example.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.coverimage.CoverImageCapabilityProvider;
import com.example.platform.coverimage.CoverImageExecutionBackend;
import com.example.platform.coverimage.CoverImageMaterializationConfiguration;
import com.example.platform.frameextract.FfmpegCpuProvider;
import com.example.platform.frameextract.FrameExtractExecutionAdapter;
import com.example.platform.frameextract.FrameExtractPlatformRegistration;
import com.example.platform.sandbox.execution.ExecutionBackend;
import com.example.platform.sandbox.execution.ExecutionBackendRegistry;
import com.example.platform.sandbox.execution.TaskCapability;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.storage.contract.provider.StorageProvider;
import com.example.platform.thumbnail.ThumbnailCapabilityProvider;
import com.example.platform.thumbnail.ThumbnailExecutionBackend;
import com.example.platform.workerfabric.reuse.ArtifactMaterializerPort;
import io.temporal.client.WorkflowClient;
import io.temporal.spring.boot.autoconfigure.template.WorkersTemplate;
import io.temporal.worker.WorkerFactory;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RestController;

/**
 * Real worker context baseline for the runtime-grouped ffmpeg worker
 * (COVER-THUMBNAIL-REBUILD-001, action 3). Merges the retired
 * {@code CoverImageWorkerApplicationTemporalRoleContextTest} and
 * {@code ThumbnailWorkerApplicationTemporalRoleContextTest}: one worker process hosts both ffmpeg
 * capabilities, exposes no controller bean, is web-disabled, owns exactly one {@link WorkerFactory}
 * on the canonical shared queue and never polls {@code workflow-process}.
 *
 * <p>The worker role also imports {@code PlatformRuntimeRoleGuard}, which fails closed unless the
 * discovery packages are the ffmpeg capability packages, the queue stays canonical, HTTP is disabled
 * and the sandbox executables exist — so a successful boot here is itself the role-guard proof.
 *
 * <p>Gated on {@code FFMPEG_WORKER_CONTEXT=true}: booting this role requires a reachable Temporal
 * endpoint, so the test is skipped (not silently passing) in the ordinary suite.
 */
@EnabledIfEnvironmentVariable(named = "FFMPEG_WORKER_CONTEXT", matches = "true")
@SpringBootTest(
        classes = PlatformFfmpegWorkerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"test", "temporal", "ffmpeg-worker"})
class PlatformFfmpegWorkerApplicationTemporalRoleContextTest extends PostgresTestContainerSupport {

    @Autowired ApplicationContext context;

    @Test
    void workerContextHasNoControllerBeansAndOnlyTheCanonicalQueue() {
        assertThat(context.getBeansWithAnnotation(RestController.class)).isEmpty();
        assertThat(context.getBeansWithAnnotation(Controller.class)).isEmpty();
        assertThat(context.getBeansOfType(WorkflowClient.class)).hasSize(1);
        assertThat(context.getBeansOfType(WorkerFactory.class)).hasSize(1);
        assertThat(context.getBeansOfType(WorkersTemplate.class)).hasSize(1);
        assertThat(context.getEnvironment().getProperty("spring.main.web-application-type"))
                .isEqualTo("none");

        WorkerFactory factory = context.getBean(WorkerFactory.class);
        assertThat(registeredQueues(factory)).containsExactly("media-platform-tasks");
        assertThat(registeredQueues(factory)).doesNotContain("workflow-process");
        assertThat(factory.tryGetWorker("workflow-process")).isNull();
    }

    @Test
    void workerContextOwnsBothCapabilitiesAndTheirExecutionBackends() {
        // One capability-neutral provider serves both capabilities in the same process.
        assertThat(context.getBeansOfType(CoverImageCapabilityProvider.class)).hasSize(1);
        assertThat(context.getBean(FfmpegCpuProvider.class)).isNotNull();
        assertThat(context.getBeansOfType(ThumbnailCapabilityProvider.class)).hasSize(1);
        assertThat(context.getBeansOfType(FfmpegCpuProvider.class)).hasSize(1);
        // One capability-neutral worker-side execution adapter behind the platform registration.
        assertThat(context.getBean(FrameExtractExecutionAdapter.class)).isNotNull();

        var backends = context.getBean(ExecutionBackendRegistry.class);
        assertThat(backends.resolve(TaskCapability.COVER_IMAGE))
                .isPresent().get()
                .extracting(ExecutionBackend::backendId)
                .isEqualTo("cover-image-sandbox");
        assertThat(backends.resolve(TaskCapability.THUMBNAIL))
                .isPresent().get()
                .extracting(ExecutionBackend::backendId)
                .isEqualTo("thumbnail-worker-runtime.local-process");
        assertThat(context.getBeansOfType(ThumbnailExecutionBackend.class)).hasSize(1);
        assertThat(context.getBeansOfType(CoverImageExecutionBackend.class)).hasSize(1);
    }

    @Test
    void workerContextOwnsTheMaterializerAndNoPlatformRegistry() {
        assertThat(context.getBeansOfType(StorageProvider.class)).hasSize(1);
        assertThat(context.getBean(CoverImageMaterializationConfiguration.class)).isNotNull();
        assertThat(context.getBeansOfType(ArtifactMaterializerPort.class)).hasSize(1);
        // Platform capability registration is the platform (API) process's authority.
        assertThat(context.getBeansOfType(
                com.example.platform.extension.api.port.PluginRegistrationPort.class)).isEmpty();
        assertThat(context.getBean(FrameExtractPlatformRegistration.class).registered()).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static Set<String> registeredQueues(WorkerFactory factory) {
        try {
            Field workers = WorkerFactory.class.getDeclaredField("workers");
            workers.setAccessible(true);
            return ((Map<String, ?>) workers.get(factory)).keySet().stream()
                    .collect(Collectors.toUnmodifiableSet());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to inspect the real WorkerFactory registration graph", e);
        }
    }
}
