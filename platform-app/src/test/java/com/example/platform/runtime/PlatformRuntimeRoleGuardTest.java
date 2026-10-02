package com.example.platform.runtime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PlatformRuntimeRoleGuardTest {
    @Test
    void apiRoleRetainsClientButCannotStartWorkers() {
        MockEnvironment env = base("API")
                .withProperty("spring.temporal.start-workers", "false");
        assertThatCode(() -> new PlatformRuntimeRoleGuard(env)).doesNotThrowAnyException();
    }

    @Test
    void apiRoleFailsClosedWhenWorkerDiscoveryIsEnabled() {
        MockEnvironment env = base("API")
                .withProperty("spring.temporal.start-workers", "true")
                .withProperty("spring.temporal.workers-auto-discovery.packages", "com.example.platform.thumbnail");
        assertThatThrownBy(() -> new PlatformRuntimeRoleGuard(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API role");
    }

    @Test
    void workerRoleRequiresNoneWebAndCanonicalCapabilityDiscovery() {
        // Scalar discovery form must keep working (backward compatibility with the previous
        // workaround): the binder resolves a comma-separated scalar into the same single-element list.
        MockEnvironment env = workerBase()
                .withProperty("spring.temporal.workers-auto-discovery.packages", "com.example.platform.thumbnail");
        assertThatCode(() -> new PlatformRuntimeRoleGuard(env)).doesNotThrowAnyException();
    }

    @Test
    void workerRoleAcceptsBothFfmpegCapabilityPackages() {
        // COVER-THUMBNAIL-REBUILD-001 (action 3): the runtime-grouped ffmpeg worker hosts both
        // capability packages in one process.
        MockEnvironment env = workerBase()
                .withProperty("spring.temporal.workers-auto-discovery.packages[0]", "com.example.platform.coverimage")
                .withProperty("spring.temporal.workers-auto-discovery.packages[1]", "com.example.platform.thumbnail");
        assertThatCode(() -> new PlatformRuntimeRoleGuard(env)).doesNotThrowAnyException();
    }

    @Test
    void workerRoleRejectsNonFfmpegDiscoveryPackages() {
        MockEnvironment env = workerBase()
                .withProperty("spring.temporal.workers-auto-discovery.packages[0]", "com.example.platform.thumbnail")
                .withProperty("spring.temporal.workers-auto-discovery.packages[1]", "com.example.platform.audio");
        assertThatThrownBy(() -> new PlatformRuntimeRoleGuard(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("discover only");
    }

    @Test
    void workerRoleAcceptsTheMediaTaskActivityPackage() {
        // P2-5b-2b-2a: the media task activity adapter runs on this same ffmpeg runtime.
        MockEnvironment env = workerBase()
                .withProperty("spring.temporal.workers-auto-discovery.packages[0]", "com.example.platform.coverimage")
                .withProperty("spring.temporal.workers-auto-discovery.packages[1]", "com.example.platform.thumbnail")
                .withProperty("spring.temporal.workers-auto-discovery.packages[2]",
                        "com.example.platform.runtime.mediatask");
        assertThatCode(() -> new PlatformRuntimeRoleGuard(env)).doesNotThrowAnyException();
    }

    @Test
    void workerRoleAcceptsYamlListDiscoveryPackages() {
        // Profile YAML lists flatten into indexed keys (packages[0], ...); the parent key does not
        // exist, which is exactly the shape a raw getProperty(key, String[].class) lookup could not
        // read. The binder must resolve it to the canonical single package.
        MockEnvironment env = workerBase()
                .withProperty("spring.temporal.workers-auto-discovery.packages[0]", "com.example.platform.thumbnail");
        assertThatCode(() -> new PlatformRuntimeRoleGuard(env)).doesNotThrowAnyException();
    }

    @Test
    void workerRoleRejectsEmptyDiscoveryPackages() {
        MockEnvironment env = workerBase()
                .withProperty("spring.temporal.workers-auto-discovery.packages", "");
        assertThatThrownBy(() -> new PlatformRuntimeRoleGuard(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("discover only");
    }

    @Test
    void workerRoleRejectsMultipleDiscoveryPackages() {
        MockEnvironment env = workerBase()
                .withProperty("spring.temporal.workers-auto-discovery.packages[0]", "com.example.platform.thumbnail")
                .withProperty("spring.temporal.workers-auto-discovery.packages[1]", "com.example.platform.workflow");
        assertThatThrownBy(() -> new PlatformRuntimeRoleGuard(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("discover only");
    }

    @Test
    void apiRoleFailsClosedWhenWorkerListIsConfigured() {
        // The binder workers binding must also gate the API role: any configured worker list fails
        // closed even when start-workers is false and the discovery list is empty.
        MockEnvironment env = base("API")
                .withProperty("spring.temporal.start-workers", "false")
                .withProperty("spring.temporal.workers[0].task-queue", "media-platform-tasks");
        assertThatThrownBy(() -> new PlatformRuntimeRoleGuard(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API role");
    }

    @Test
    void workerRoleRejectsEveryAdditionalOrDuplicateQueueEntry() {
        MockEnvironment third = yamlListPackage(workerBase())
                .withProperty("spring.temporal.workers[1].task-queue", "media-platform-tasks")
                .withProperty("spring.temporal.workers[2].task-queue", "media-platform-tasks");
        assertThatThrownBy(() -> new PlatformRuntimeRoleGuard(third))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly the canonical queue");

        MockEnvironment secondQueue = yamlListPackage(workerBase())
                .withProperty("spring.temporal.workers[1].task-queue", "workflow-process");
        assertThatThrownBy(() -> new PlatformRuntimeRoleGuard(secondQueue))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly the canonical queue");

        MockEnvironment duplicateCanonical = yamlListPackage(workerBase())
                .withProperty("spring.temporal.workers[1].task-queue", "media-platform-tasks");
        assertThatThrownBy(() -> new PlatformRuntimeRoleGuard(duplicateCanonical))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly the canonical queue");
    }

    @Test
    void workerRoleRejectsMissingWorkerList() {
        MockEnvironment env = base("WORKER")
                .withProperty("spring.temporal.start-workers", "true")
                .withProperty("spring.main.web-application-type", "none")
                .withProperty("spring.temporal.workers-auto-discovery.packages[0]", "com.example.platform.thumbnail")
                .withProperty("platform.ffmpeg-worker.sandbox.bwrap", "/bin/true")
                .withProperty("platform.ffmpeg-worker.sandbox.ffmpeg", "/bin/true")
                .withProperty("platform.ffmpeg-worker.sandbox.ffprobe", "/bin/true");
        assertThatThrownBy(() -> new PlatformRuntimeRoleGuard(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly the canonical queue");
    }

    @Test
    void missingRoleFailsClosed() {
        MockEnvironment env = base("")
                .withProperty("spring.temporal.start-workers", "false");
        assertThatThrownBy(() -> new PlatformRuntimeRoleGuard(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("role is required");
    }

    @Test
    void workerRoleRejectsWrongQueueAndHttp() {
        MockEnvironment wrongQueue = base("WORKER")
                .withProperty("app.temporal.task-queue", "other-queue")
                .withProperty("spring.temporal.start-workers", "true")
                .withProperty("spring.main.web-application-type", "none")
                .withProperty("spring.temporal.workers-auto-discovery.packages", "com.example.platform.thumbnail");
        assertThatThrownBy(() -> new PlatformRuntimeRoleGuard(wrongQueue))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must remain");

        MockEnvironment http = base("WORKER")
                .withProperty("spring.temporal.start-workers", "true")
                .withProperty("spring.main.web-application-type", "servlet")
                .withProperty("spring.temporal.workers-auto-discovery.packages", "com.example.platform.thumbnail");
        assertThatThrownBy(() -> new PlatformRuntimeRoleGuard(http))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP");
    }

    private static MockEnvironment workerBase() {
        return base("WORKER")
                .withProperty("spring.temporal.start-workers", "true")
                .withProperty("spring.main.web-application-type", "none")
                .withProperty("spring.temporal.workers[0].task-queue", "media-platform-tasks")
                .withProperty("platform.ffmpeg-worker.sandbox.bwrap", "/bin/true")
                .withProperty("platform.ffmpeg-worker.sandbox.ffmpeg", "/bin/true")
                .withProperty("platform.ffmpeg-worker.sandbox.ffprobe", "/bin/true");
    }

    /** The YAML-list discovery form, flattened exactly as Spring Boot flattens a profile list. */
    private static MockEnvironment yamlListPackage(MockEnvironment environment) {
        return environment.withProperty(
                "spring.temporal.workers-auto-discovery.packages[0]", "com.example.platform.thumbnail");
    }

    private static MockEnvironment base(String role) {
        return new MockEnvironment()
                .withProperty("platform.runtime.role", role)
                .withProperty("app.temporal.enabled", "true")
                .withProperty("app.temporal.task-queue", "media-platform-tasks");
    }
}
