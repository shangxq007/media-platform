package com.example.platform.runtime;

import io.temporal.spring.boot.autoconfigure.properties.WorkerProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 *
 * <p>COVER-THUMBNAIL-REBUILD-001 (action 3): the guard is runtime-grouped, not
 * capability-hardcoded. A worker is the single {@code platform-ffmpeg-worker} that may host one or
 * more ffmpeg capability packages ({@link #WORKER_CAPABILITY_PACKAGES}); any other discovery package
 * fails closed, and the sandbox executables are read from the unified
 * {@code platform.ffmpeg-worker.sandbox.*} keys instead of a per-capability key.
 */
@Component
@ConditionalOnProperty(prefix = "app.temporal", name = "enabled", havingValue = "true")
public final class PlatformRuntimeRoleGuard {
    static final String CANONICAL_QUEUE = "media-platform-tasks";

    /**
     * The capability packages the platform ffmpeg worker may discover. The worker is grouped by
     * runtime (one ffmpeg runtime hosting several capabilities), never by a single capability.
     */
    static final Set<String> WORKER_CAPABILITY_PACKAGES = Set.of(
            "com.example.platform.coverimage",
            "com.example.platform.thumbnail",
            // P2-5b-2b-2a: the media task activity adapter carries the whole-graph execution
            // capability of this same ffmpeg runtime (WORKER-PACKAGE-TAXONOMY backlog: revisit the
            // capability-vs-runtime naming of this set).
            "com.example.platform.runtime.mediatask");

    /** Unified ffmpeg-worker sandbox configuration keys. */
    static final String SANDBOX_BWRAP_KEY = "platform.ffmpeg-worker.sandbox.bwrap";
    static final String SANDBOX_FFMPEG_KEY = "platform.ffmpeg-worker.sandbox.ffmpeg";
    static final String SANDBOX_FFPROBE_KEY = "platform.ffmpeg-worker.sandbox.ffprobe";

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
        boolean workerCapabilityPackages = !packages.isEmpty()
                && new HashSet<>(packages).size() == packages.size()
                && WORKER_CAPABILITY_PACKAGES.containsAll(packages);
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
        if (!workerCapabilityPackages) {
            throw new IllegalStateException("WORKER role must discover only the platform ffmpeg worker "
                    + "capability packages " + WORKER_CAPABILITY_PACKAGES);
        }
        if (workers.size() != 1 || !CANONICAL_QUEUE.equals(workers.get(0).getTaskQueue())) {
            throw new IllegalStateException("WORKER role must register exactly the canonical queue");
        }
        requireExecutable(environment.getProperty(SANDBOX_BWRAP_KEY, "/usr/bin/bwrap"));
        requireExecutable(environment.getProperty(SANDBOX_FFMPEG_KEY, "/usr/bin/ffmpeg"));
        requireExecutable(environment.getProperty(SANDBOX_FFPROBE_KEY, "/usr/bin/ffprobe"));
    }

    private static void requireExecutable(String configured) {
        Path path = Path.of(configured);
        if (!Files.isRegularFile(path) || !Files.isExecutable(path)) {
            throw new IllegalStateException("required ffmpeg worker sandbox executable is unavailable: " + path);
        }
    }
}
