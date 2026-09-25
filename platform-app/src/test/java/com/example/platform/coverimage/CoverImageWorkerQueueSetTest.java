package com.example.platform.coverimage;

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
 * Queue-set proof for the cover-image worker role (Correction 6
 * {@code TemporalRoleBeanGraphTest.workerProfileHasOnlyCanonicalQueueWorker} pattern: reflect the
 * real {@link WorkerFactory} registration graph, assert exactly one canonical queue and assert the
 * workflow engine's queue is absent).
 *
 * <p>The real profile resources are loaded ({@code application-temporal.yml} plus
 * {@code application-cover-image-worker.yml}) so the profile-precedence override of the base worker
 * list is exercised, not a synthetic property set. The worker must own exactly the canonical shared
 * queue {@code media-platform-tasks} and must never poll {@code workflow-process}.
 */
class CoverImageWorkerQueueSetTest {

    @Test
    void baseTemporalProfileAloneRegistersTheWorkflowEngineQueue() {
        runner("temporal").run(context -> {
            assertThat(context).hasNotFailed();
            WorkerFactory factory = context.getBean(WorkerFactory.class);
            // Negative control: without the worker profile the base list owns two queues, so the
            // override asserted below is a real override and not a vacuous assertion.
            assertThat(registeredQueues(factory)).contains("workflow-process", "media-platform-tasks");
        });
    }

    @Test
    void coverImageWorkerProfileRegistersExactlyTheCanonicalQueue() {
        runner("temporal,cover-image-worker").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(WorkerFactory.class);
            WorkerFactory factory = context.getBean(WorkerFactory.class);
            assertThat(registeredQueues(factory)).containsExactly("media-platform-tasks");
            assertThat(registeredQueues(factory)).doesNotContain("workflow-process");
            assertThat(factory.getWorker("media-platform-tasks")).isNotNull();
            assertThat(factory.tryGetWorker("workflow-process")).isNull();
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
