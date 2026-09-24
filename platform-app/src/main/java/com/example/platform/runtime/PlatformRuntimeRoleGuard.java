package com.example.platform.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fails closed before a Temporal-enabled process can accidentally run both roles.
 * This guard is intentionally configuration-only; it does not create a second worker
 * lifecycle or alter queue ownership.
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
        String[] packages = environment.getProperty("spring.temporal.workers-auto-discovery.packages", String[].class);
        boolean thumbnailPackageOnly = packages != null
                && List.of(packages).size() == 1
                && "com.example.platform.thumbnail".equals(packages[0]);
        boolean web = !"none".equalsIgnoreCase(environment.getProperty("spring.main.web-application-type", ""));

        if (role == PlatformRuntimeRole.API) {
            if (startWorkers || (packages != null && packages.length > 0)) {
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
