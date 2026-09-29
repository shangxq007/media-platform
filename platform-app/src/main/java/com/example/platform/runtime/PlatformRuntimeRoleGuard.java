package com.example.platform.runtime;

import io.temporal.spring.boot.autoconfigure.properties.WorkerProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fails closed before a Temporal-enabled process can accidentally run both roles.
 * This guard is intentionally configuration-only; it does not create a second worker
 * lifecycle or alter queue ownership.
 *
 * <p>The Temporal discovery and worker lists are read through Spring's {@link Binder}
 * rather than a raw {@code getProperty(key, String[].class)} lookup. Profile YAML lists
 * are flattened into indexed keys ({@code packages[0]}, ...), so the parent key does not
 * exist and a plain lookup returns {@code null} and fails the worker role closed. The
 * binder resolves both the YAML-list and the comma-separated scalar forms, so the worker
 * profile may declare its discovery package either way without tripping the guard.
 */
@Component
@ConditionalOnProperty(prefix = "app.temporal", name = "enabled", havingValue = "true")
public final class PlatformRuntimeRoleGuard {
    static final String CANONICAL_QUEUE = "media-platform-tasks";

    public PlatformRuntimeRoleGuard(Environment environment) {
        PlatformRuntimeRole role = PlatformRuntimeRole.parse(environment.getProperty("platform.runtime.role"));
        String queue = environment.getProperty("app.temporal.task-queue", "");
        if (!CANONICAL_QUEUE.equals(queue)) {
            throw new IllegalStateException("app.temporal.task-queue must remain " + CANONICAL_QUEUE);
        }

        boolean startWorkers = environment.getProperty("spring.temporal.start-workers", Boolean.class, false);
        Binder binder = Binder.get(environment);
        List<String> packages = binder
                .bind("spring.temporal.workers-auto-discovery.packages", Bindable.listOf(String.class))
                .orElse(List.of());
        BindResult<List<WorkerProperties>> workersResult =
                binder.bind("spring.temporal.workers", Bindable.listOf(WorkerProperties.class));
        List<WorkerProperties> workers = workersResult.orElse(List.of());
        boolean workerListConfigured = workersResult.isBound();
        boolean thumbnailPackageOnly = packages.size() == 1
                && "com.example.platform.thumbnail".equals(packages.get(0));
        boolean web = !"none".equalsIgnoreCase(environment.getProperty("spring.main.web-application-type", ""));

        if (role == PlatformRuntimeRole.API) {
            if (startWorkers || workerListConfigured || !packages.isEmpty()) {
                throw new IllegalStateException("API role must not register Temporal workers");
            }
            return;
        }

        if (!startWorkers) {
            throw new IllegalStateException("WORKER role requires spring.temporal.start-workers=true");
        }
        if (web) {
            throw new IllegalStateException("WORKER role must disable the embedded HTTP server");
        }
        if (!thumbnailPackageOnly) {
            throw new IllegalStateException("WORKER role must discover only com.example.platform.thumbnail");
        }
        if (workers.size() != 1 || !CANONICAL_QUEUE.equals(workers.get(0).getTaskQueue())) {
            throw new IllegalStateException("WORKER role must register exactly the canonical queue");
        }
        requireExecutable(environment.getProperty("platform.thumbnail.sandbox.bwrap", "/usr/bin/bwrap"));
        requireExecutable(environment.getProperty("platform.thumbnail.sandbox.ffmpeg", "/usr/bin/ffmpeg"));
        requireExecutable(environment.getProperty("platform.thumbnail.sandbox.ffprobe", "/usr/bin/ffprobe"));
    }

    private static void requireExecutable(String configured) {
        Path path = Path.of(configured);
        if (!Files.isRegularFile(path) || !Files.isExecutable(path)) {
            throw new IllegalStateException("required thumbnail sandbox executable is unavailable: " + path);
        }
    }
}
