package com.example.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Phase 2 acceptance P2-A/P2-B (new COMBINED semantics) plus P2-D/P2-E regression guards
 * (API and WORKER semantics unchanged).
 */
class PlatformRuntimeRoleCombinedTest {

    @Test
    void combinedRoleParses() {
        // P2-A
        assertThat(PlatformRuntimeRole.parse("COMBINED")).isEqualTo(PlatformRuntimeRole.COMBINED);
        assertThat(PlatformRuntimeRole.parse(" combined ")).isEqualTo(PlatformRuntimeRole.COMBINED);
    }

    @Test
    void combinedAllowsHttpAndTemporalWorker() {
        // P2-B: embedded HTTP (servlet) + Temporal worker coexist.
        MockEnvironment env = combinedBase()
                .withProperty("spring.main.web-application-type", "servlet");
        assertThatCode(() -> new PlatformRuntimeRoleGuard(env)).doesNotThrowAnyException();
    }

    @Test
    void combinedAllowsDefaultWebApplicationType() {
        // P2-B: no explicit web-application-type (the normal HTTP process shape) is allowed.
        assertThatCode(() -> new PlatformRuntimeRoleGuard(combinedBase())).doesNotThrowAnyException();
    }

    @Test
    void combinedStillRequiresATemporalWorker() {
        MockEnvironment env = combinedBase().withProperty("spring.temporal.start-workers", "false");
        assertThatThrownBy(() -> new PlatformRuntimeRoleGuard(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMBINED role requires");
    }

    @Test
    void combinedRejectsNonFfmpegDiscoveryPackages() {
        MockEnvironment env = combinedBase()
                .withProperty("spring.temporal.workers-auto-discovery.packages", "com.example.platform.audio");
        assertThatThrownBy(() -> new PlatformRuntimeRoleGuard(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMBINED role must discover only");
    }

    @Test
    void apiRoleStillForbidsWorkers() {
        // P2-D: API semantics unchanged.
        MockEnvironment env = base("API")
                .withProperty("spring.temporal.start-workers", "true")
                .withProperty("spring.temporal.workers-auto-discovery.packages",
                        "com.example.platform.thumbnail");
        assertThatThrownBy(() -> new PlatformRuntimeRoleGuard(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API role");
    }

    @Test
    void workerRoleStillForbidsHttp() {
        // P2-E: WORKER semantics unchanged.
        MockEnvironment env = base("WORKER")
                .withProperty("spring.temporal.start-workers", "true")
                .withProperty("spring.main.web-application-type", "servlet")
                .withProperty("spring.temporal.workers-auto-discovery.packages",
                        "com.example.platform.thumbnail")
                .withProperty("spring.temporal.workers[0].task-queue", "media-platform-tasks")
                .withProperty("platform.ffmpeg-worker.sandbox.bwrap", "/bin/true")
                .withProperty("platform.ffmpeg-worker.sandbox.ffmpeg", "/bin/true")
                .withProperty("platform.ffmpeg-worker.sandbox.ffprobe", "/bin/true");
        assertThatThrownBy(() -> new PlatformRuntimeRoleGuard(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP");
    }

    private static MockEnvironment combinedBase() {
        return base("COMBINED")
                .withProperty("spring.temporal.start-workers", "true")
                .withProperty("spring.temporal.workers[0].task-queue", "media-platform-tasks")
                .withProperty("spring.temporal.workers-auto-discovery.packages",
                        "com.example.platform.thumbnail")
                .withProperty("platform.ffmpeg-worker.sandbox.bwrap", "/bin/true")
                .withProperty("platform.ffmpeg-worker.sandbox.ffmpeg", "/bin/true")
                .withProperty("platform.ffmpeg-worker.sandbox.ffprobe", "/bin/true");
    }

    private static MockEnvironment base(String role) {
        return new MockEnvironment()
                .withProperty("platform.runtime.role", role)
                .withProperty("app.temporal.enabled", "true")
                .withProperty("app.temporal.task-queue", "media-platform-tasks");
    }
}
