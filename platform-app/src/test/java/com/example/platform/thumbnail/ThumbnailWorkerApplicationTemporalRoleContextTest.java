package com.example.platform.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.sandbox.execution.ExecutionBackendRegistry;
import com.example.platform.sandbox.execution.TaskCapability;
import com.example.platform.shared.test.PostgresTestContainerSupport;
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
 * Real worker context baseline for the thumbnail capability, mirroring
 * {@code CoverImageWorkerApplicationTemporalRoleContextTest} so both workers carry the same
 * regression net before they are unified into a single multi-capability worker.
 *
 * <p>Boots the actual {@link ThumbnailWorkerApplication} context and scans the real bean graph: the
 * worker process must expose no {@code @RestController}/{@code @Controller} bean (the API surface is
 * never loaded into the worker), must be web-disabled, must own exactly one {@link WorkerFactory} on
 * the canonical shared queue and must never poll the workflow engine's {@code workflow-process}
 * queue.
 *
 * <p>Profile order: {@code thumbnail-worker} is applied <em>last</em> (the deployed order — see
 * {@code infra/docker/Dockerfile.thumbnail-activity-worker}), so its single-queue worker list
 * overrides the base {@code application-temporal.yml} list by profile precedence.
 *
 * <p>The worker role also imports {@code PlatformRuntimeRoleGuard}, which fails closed unless the
 * role is {@code WORKER}, the queue stays canonical, the HTTP server is disabled, the Temporal
 * discovery package is exactly {@code com.example.platform.thumbnail} and the sandbox executables
 * exist — so a successful boot here is itself the role guard proof.
 *
 * <p>Gated on {@code THUMBNAIL_WORKER_CONTEXT=true} (mirroring the {@code COVER_E2E} convention in
 * {@code CoverImageEndToEndAcceptanceTest}): booting this role requires a reachable Temporal
 * endpoint ({@code spring.temporal.start-workers=true} is mandated by
 * {@code PlatformRuntimeRoleGuard}), so the test is skipped (not silently passing) in the ordinary
 * suite. Set {@code THUMBNAIL_WORKER_CONTEXT=true} with a live Temporal cluster to run it.
 */
@EnabledIfEnvironmentVariable(named = "THUMBNAIL_WORKER_CONTEXT", matches = "true")
@SpringBootTest(
        classes = ThumbnailWorkerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"test", "temporal", "thumbnail-worker"})
class ThumbnailWorkerApplicationTemporalRoleContextTest extends PostgresTestContainerSupport {

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
    void workerContextOwnsTheThumbnailCapabilityAndExecutionBackend() {
        // The thumbnail worker composes its own registered provider, capability registry and
        // execution backend; the capability is not owned by the API process.
        assertThat(context.getBeansOfType(ThumbnailCapabilityProvider.class))
                .as("worker role registers exactly the pinned thumbnail provider")
                .hasSize(1);
        assertThat(context.getBean(ThumbnailCapabilityRegistry.class))
                .as("worker role composes the thumbnail capability registry")
                .isNotNull();
        var backends = context.getBean(ExecutionBackendRegistry.class);
        assertThat(backends.resolve(TaskCapability.THUMBNAIL))
                .isPresent()
                .get()
                .extracting(com.example.platform.sandbox.execution.ExecutionBackend::backendId)
                .isEqualTo("thumbnail-worker-runtime.local-process");
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
