package com.example.platform.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.SpringBootConfiguration;

class ThumbnailWorkerApplicationArchitectureTest {
    @Test
    void workerLauncherIsASeparateNonWebEntryPoint() throws Exception {
        assertThat(ThumbnailWorkerApplication.class.getAnnotation(SpringBootConfiguration.class)).isNotNull();
        assertThat(ThumbnailWorkerApplication.class.getAnnotation(SpringBootApplication.class)).isNull();
        String source = Files.readString(Path.of("src/main/java/com/example/platform/thumbnail/ThumbnailWorkerApplication.java"));
        assertThat(source).contains("WebApplicationType.NONE")
                .contains("com.example.platform.thumbnail")
                .doesNotContain("com.example.platform.web");
    }

    @Test
    void workerUsesCanonicalActivityContractAndQueue() throws Exception {
        assertThat(ThumbnailActivitiesImpl.class.getAnnotation(
                org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class).havingValue())
                .isEqualTo("WORKER");
        assertThat(ThumbnailExecutionBackend.class.getAnnotation(
                org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class).havingValue())
                .isEqualTo("WORKER");
        assertThat(ThumbnailActivitiesImpl.class.getAnnotation(io.temporal.spring.boot.ActivityImpl.class).taskQueues())
                .containsExactly("media-platform-tasks");
        assertThat(ThumbnailWorkflowImpl.class.getAnnotation(io.temporal.spring.boot.WorkflowImpl.class).taskQueues())
                .containsExactly("media-platform-tasks");
        String dockerfile = Files.readString(Path.of("../infra/docker/Dockerfile.thumbnail-activity-worker"));
        assertThat(dockerfile).contains("platform-thumbnail-worker.jar")
                .contains("USER spring:spring")
                .contains("SPRING_MAIN_WEB_APPLICATION_TYPE=none")
                .doesNotContain("--privileged")
                .doesNotContain("seccomp=unconfined");
    }
}
