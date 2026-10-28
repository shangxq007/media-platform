package com.example.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * COVER-THUMBNAIL-REBUILD-001 (action 3): the two per-capability worker entry points are merged into
 * the single runtime-grouped {@link PlatformFfmpegWorkerApplication} ({@code EXEC-CAN-009}).
 */
class PlatformFfmpegWorkerApplicationArchitectureTest {

    @Test
    void workerLauncherIsASeparateNonWebEntryPoint() throws Exception {
        assertThat(PlatformFfmpegWorkerApplication.class.getAnnotation(SpringBootConfiguration.class)).isNotNull();
        assertThat(PlatformFfmpegWorkerApplication.class.getAnnotation(SpringBootApplication.class)).isNull();
        String source = Files.readString(Path.of(
                "src/main/java/com/example/platform/runtime/PlatformFfmpegWorkerApplication.java"));
        assertThat(source).contains("WebApplicationType.NONE")
                .contains("com.example.platform.runtime")
                .doesNotContain("com.example.platform.coverimage", "com.example.platform.thumbnail")
                .doesNotContain("com.example.platform.web");
        assertThat(PlatformFfmpegWorkerApplication.WORKER_PROFILES).contains("temporal");
        assertThat(PlatformFfmpegWorkerApplication.WORKER_PROFILES.getLast()).isEqualTo("ffmpeg-worker");
    }

    @Test
    void retiredPerCapabilityWorkerApplicationsAreGone() {
        assertThat(Files.exists(Path.of(
                "src/main/java/com/example/platform/coverimage/CoverImageWorkerApplication.java")))
                .as("cover-image worker entry point is retired")
                .isFalse();
        assertThat(Files.exists(Path.of(
                "src/main/java/com/example/platform/thumbnail/ThumbnailWorkerApplication.java")))
                .as("thumbnail worker entry point is retired")
                .isFalse();
    }

    @Test
    void graphAndArtifactCapabilitiesShareTheFormalActivityRuntimeAndQueue() {
        for(Class<?> activity: new Class<?>[]{com.example.platform.runtime.mediatask.MediaTaskActivitiesAdapter.class,
                com.example.platform.runtime.mediatask.CapabilityExecutionActivity.class}) {
            assertThat(activity.getAnnotation(io.temporal.spring.boot.ActivityImpl.class).taskQueues()).containsExactly("media-platform-tasks");
            assertThat(activity.getAnnotation(org.springframework.boot.autoconfigure.condition.ConditionalOnExpression.class)).isNotNull();
        }
        for(Class<?> workflow: new Class<?>[]{com.example.platform.workflow.temporal.mediatask.MediaTaskWorkflowImpl.class,
                com.example.platform.workflow.temporal.mediatask.CapabilityExecutionWorkflowImpl.class}) {
            assertThat(workflow.getAnnotation(io.temporal.spring.boot.WorkflowImpl.class).taskQueues()).containsExactly("media-platform-tasks");
        }
    }

    @Test
    void workerImageIsHardenedAndWebDisabled() throws Exception {
        String dockerfile = Files.readString(Path.of("../infra/docker/Dockerfile.ffmpeg-worker"));
        assertThat(dockerfile).contains("platform-ffmpeg-worker.jar")
                .contains("USER spring:spring")
                .contains("SPRING_MAIN_WEB_APPLICATION_TYPE=none")
                .contains("ffmpeg-worker")
                .doesNotContain("--privileged")
                .doesNotContain("seccomp=unconfined");
    }
}
