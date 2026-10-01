package com.example.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.coverimage.CoverImageActivitiesImpl;
import com.example.platform.coverimage.CoverImageExecutionBackend;
import com.example.platform.coverimage.CoverImageWorkflowImpl;
import com.example.platform.thumbnail.ThumbnailActivitiesImpl;
import com.example.platform.thumbnail.ThumbnailExecutionBackend;
import com.example.platform.thumbnail.ThumbnailWorkflowImpl;
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
                .contains("com.example.platform.coverimage")
                .contains("com.example.platform.thumbnail")
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
    void bothCapabilitiesShareTheCanonicalActivityContractAndQueue() {
        for (Class<?> workerBean : new Class<?>[] {
                CoverImageActivitiesImpl.class, ThumbnailActivitiesImpl.class,
                CoverImageExecutionBackend.class, ThumbnailExecutionBackend.class}) {
            assertThat(workerBean.getAnnotation(
                    org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class).havingValue())
                    .as(workerBean.getSimpleName() + " is worker-role only")
                    .isEqualTo("WORKER");
        }
        assertThat(CoverImageActivitiesImpl.class.getAnnotation(
                io.temporal.spring.boot.ActivityImpl.class).taskQueues())
                .containsExactly("media-platform-tasks");
        assertThat(ThumbnailActivitiesImpl.class.getAnnotation(
                io.temporal.spring.boot.ActivityImpl.class).taskQueues())
                .containsExactly("media-platform-tasks");
        assertThat(CoverImageWorkflowImpl.class.getAnnotation(
                io.temporal.spring.boot.WorkflowImpl.class).taskQueues())
                .containsExactly("media-platform-tasks");
        assertThat(ThumbnailWorkflowImpl.class.getAnnotation(
                io.temporal.spring.boot.WorkflowImpl.class).taskQueues())
                .containsExactly("media-platform-tasks");
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
