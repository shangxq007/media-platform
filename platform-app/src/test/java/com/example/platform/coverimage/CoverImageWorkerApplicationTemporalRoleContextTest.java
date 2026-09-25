package com.example.platform.coverimage;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.provider.StorageProvider;
import io.temporal.client.WorkflowClient;
import io.temporal.spring.boot.autoconfigure.template.WorkersTemplate;
import io.temporal.worker.WorkerFactory;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RestController;

/**
 * Real worker context proof for the cover-image capability (Correction 6
 * {@code ThumbnailWorkerApplicationTemporalRoleContextTest} pattern).
 *
 * <p>Boots the actual {@link CoverImageWorkerApplication} context and scans the real bean graph:
 * the worker process must expose no {@code @RestController}/{@code @Controller} bean (the API
 * surface is never loaded into the worker), must be web-disabled, must own exactly one
 * {@link WorkerFactory} on the canonical shared queue and must never poll the workflow engine's
 * {@code workflow-process} queue.
 *
 * <p>Profile order matters: {@code cover-image-worker} is applied last so its worker list overrides
 * the base {@code application-temporal.yml} list by profile precedence.
 *
 * <p>Known runtime gap surfaced by this test (reported, not fixed here): the worker role's bean scan
 * registers no {@code StorageProvider}, while
 * {@code CoverImageMaterializationConfiguration} requires at least one
 * ({@code cover-image subject reads require a registered StorageProvider}). The test therefore
 * supplies a test-scoped {@link StorageProvider} stub so the real worker bean graph can be
 * assembled and scanned; the production worker process cannot start without one.
 */
@SpringBootTest(
        classes = CoverImageWorkerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        // No live Temporal cluster in the unit-test environment: the bean graph (WorkerFactory and
        // its registered queues) is still built and asserted; only the network start is suppressed.
        properties = {
            "spring.temporal.start-workers=false",
            "app.temporal.worker-required=false"
        })
@ActiveProfiles({"test", "temporal", "cover-image-worker"})
class CoverImageWorkerApplicationTemporalRoleContextTest extends PostgresTestContainerSupport {

    @Autowired ApplicationContext context;

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class WorkerStorageProviderStub {

        @org.springframework.context.annotation.Bean
        StorageProvider coverWorkerStorageProvider() {
            StorageProvider provider = org.mockito.Mockito.mock(StorageProvider.class);
            org.mockito.Mockito.when(provider.providerId())
                    .thenReturn(new StorageProviderId("local"));
            return provider;
        }

        @org.springframework.context.annotation.Bean
        com.example.platform.composition.app.CompositionResultRepository
                coverWorkerCompositionResultRepository() {
            return org.mockito.Mockito.mock(
                    com.example.platform.composition.app.CompositionResultRepository.class);
        }

        @org.springframework.context.annotation.Bean
        com.example.platform.extension.api.port.PluginRegistrationPort
                coverWorkerPluginRegistrationPort() {
            return org.mockito.Mockito.mock(
                    com.example.platform.extension.api.port.PluginRegistrationPort.class);
        }
    }

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
