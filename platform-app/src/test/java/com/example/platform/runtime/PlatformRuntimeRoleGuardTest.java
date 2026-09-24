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
    void workerRoleRequiresNoneWebAndCanonicalThumbnailDiscovery() {
        MockEnvironment env = base("WORKER")
                .withProperty("spring.temporal.start-workers", "true")
                .withProperty("spring.main.web-application-type", "none")
                .withProperty("spring.temporal.workers-auto-discovery.packages", "com.example.platform.thumbnail")
                .withProperty("platform.thumbnail.sandbox.bwrap", "/bin/true")
                .withProperty("platform.thumbnail.sandbox.ffmpeg", "/bin/true")
                .withProperty("platform.thumbnail.sandbox.ffprobe", "/bin/true");
        assertThatCode(() -> new PlatformRuntimeRoleGuard(env)).doesNotThrowAnyException();
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

    private static MockEnvironment base(String role) {
        return new MockEnvironment()
                .withProperty("platform.runtime.role", role)
                .withProperty("app.temporal.enabled", "true")
                .withProperty("app.temporal.task-queue", "media-platform-tasks");
    }
}
