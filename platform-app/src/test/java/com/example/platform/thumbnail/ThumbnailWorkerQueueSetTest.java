package com.example.platform.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.workflow.temporal.TemporalEnablementConfiguration;
import io.temporal.spring.boot.autoconfigure.NonRootNamespaceAutoConfiguration;
import io.temporal.spring.boot.autoconfigure.RootNamespaceAutoConfiguration;
import io.temporal.spring.boot.autoconfigure.ServiceStubsAutoConfiguration;
import io.temporal.worker.WorkerFactory;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Queue-set baseline for the thumbnail worker role, mirroring the cover-image worker proof
 * ({@code CoverImageWorkerQueueSetTest}) so both workers carry the same regression net before they
 * are unified.
 *
 * <p>The <em>real</em> profile resources are loaded ({@code application-temporal.yml} plus
 * {@code application-thumbnail-worker.yml}) so the profile-precedence override of the shared worker
 * list is exercised, not a synthetic property set. The thumbnail worker must own exactly the
 * canonical shared queue {@code media-platform-tasks} and must never poll the workflow engine's
 * {@code workflow-process} queue.
 *
 * <p><b>Profile order matters and is asserted here.</b> The shared base file defines two workers
 * ({@code render-worker} on {@code media-platform-tasks}, {@code workflow-process-worker} on
 * {@code workflow-process}); the thumbnail worker profile narrows that list to a single canonical
 * worker. Spring Boot resolves profile-specific property sources last-wins, so the worker profile
 * must be applied <em>last</em> for its single-queue list to win. The deployed configuration does
 * exactly that ({@code SPRING_PROFILES_ACTIVE=dev,temporal,thumbnail-worker} in
 * {@code infra/docker/Dockerfile.thumbnail-activity-worker} and
 * {@code infra/runtime/vm-native-thumbnail-worker/build.sh}), and the runbook documents
 * {@code temporal,thumbnail-worker}. The positive case below uses that deployed order; the
 * negative cases pin the precedence rule itself so a future reordering cannot silently widen the
 * poller set.
 */
class ThumbnailWorkerQueueSetTest {

    /** The deployed/documented worker profile order: worker profile last so its list wins. */
    private static final String DEPLOYED_PROFILE_ORDER = "temporal,thumbnail-worker";

    @Test
    void baseTemporalProfileAloneRegistersTheWorkflowEngineQueue() {
        runner("temporal").run(context -> {
            assertThat(context).hasNotFailed();
            WorkerFactory factory = context.getBean(WorkerFactory.class);
            // Negative control: without the worker profile the base list owns two queues, so the
            // single-queue assertion below is a real narrowing and not a vacuous one.
            assertThat(registeredQueues(factory)).contains("workflow-process", "media-platform-tasks");
        });
    }

    @Test
    void thumbnailWorkerProfileAppliedLastRegistersExactlyTheCanonicalQueue() {
        runner(DEPLOYED_PROFILE_ORDER).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(WorkerFactory.class);
            WorkerFactory factory = context.getBean(WorkerFactory.class);
            assertThat(registeredQueues(factory)).containsExactly("media-platform-tasks");
            assertThat(registeredQueues(factory)).doesNotContain("workflow-process");
            assertThat(factory.getWorker("media-platform-tasks")).isNotNull();
            assertThat(factory.tryGetWorker("workflow-process")).isNull();
        });
    }

    @Test
    void workerProfileAppliedBeforeTemporalDoesNotNarrowTheBaseList() {
        // Locks the last-wins precedence: with the worker profile first, the base temporal list wins
        // and the poller set widens back to the workflow engine queue. This is the exact hazard that
        // makes the deployed worker-last order load-bearing (and is pinned so a reorder is caught).
        runner("thumbnail-worker,temporal").run(context -> {
            assertThat(context).hasNotFailed();
            WorkerFactory factory = context.getBean(WorkerFactory.class);
            assertThat(registeredQueues(factory)).contains("workflow-process", "media-platform-tasks");
        });
    }

    private static ApplicationContextRunner runner(String profiles) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(
                        ServiceStubsAutoConfiguration.class,
                        RootNamespaceAutoConfiguration.class,
                        NonRootNamespaceAutoConfiguration.class))
                .withUserConfiguration(TemporalEnablementConfiguration.class)
                .withPropertyValues(
                        "spring.profiles.active=" + profiles,
                        "spring.temporal.connection.target=127.0.0.1:7233",
                        "spring.temporal.namespace=media-platform-dev");
    }

    /** Reflects the real {@code WorkerFactory.workers} registration map — no proxy, no fake. */
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
