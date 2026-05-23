package com.example.platform;

import com.example.platform.identity.api.TenantProjectController;
import com.example.platform.identity.api.dto.CreateProjectRequest;
import com.example.platform.identity.api.dto.CreateTenantRequest;
import com.example.platform.render.api.RenderController;
import com.example.platform.render.app.RenderOrchestratorService;
import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end render using real ffmpeg/melt when installed on the host.
 * Skipped automatically when tools are missing.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.ai.openai.api-key=test-key",
        "app.security.enabled=false",
        "app.identity.api-key-auth-enabled=false",
        "app.outbox.dispatch-interval-ms=999999999",
        "render.providers.javacv.enabled=false",
        "render.providers.gstreamer.enabled=false",
        "render.providers.ffmpeg.enabled=true",
        "render.providers.mlt.enabled=true"
})
class RenderNativeToolsIT {

    @Autowired
    private TenantProjectController tenantProjectController;

    @Autowired
    private RenderController renderController;

    @Autowired
    private RenderOrchestratorService orchestratorService;

    @Autowired
    private TimelineSnapshotService timelineSnapshotService;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    @TempDir
    Path tempDir;

    static boolean ffmpegAvailable() {
        return Files.isExecutable(Path.of("/usr/bin/ffmpeg"))
                || Files.isExecutable(Path.of("/bin/ffmpeg"));
    }

    static boolean meltAvailable() {
        return Files.isExecutable(Path.of("/usr/bin/melt"))
                || Files.isExecutable(Path.of("/bin/melt"));
    }

    @BeforeEach
    void prepareMedia(@TempDir Path mediaDir) throws Exception {
        if (!ffmpegAvailable()) {
            return;
        }
        Path input = mediaDir.resolve("input.mp4");
        ProcessBuilder pb = new ProcessBuilder(
                "/usr/bin/ffmpeg", "-y",
                "-f", "lavfi", "-i", "testsrc=duration=2:size=320x240:rate=24",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=2",
                "-c:v", "libx264", "-c:a", "aac", "-shortest",
                input.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        int code = p.waitFor();
        assertThat(code).isZero();

        Path artifactDir = Path.of(storageRoot, "artifacts", "native-e2e");
        Files.createDirectories(artifactDir);
        Files.copy(input, artifactDir.resolve("source.mp4"), StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    @EnabledIf("ffmpegAvailable")
    void ffmpegRenderJob_withTimelineSnapshot_completes() throws Exception {
        var tenant = tenantProjectController.createTenant(new CreateTenantRequest("Native FFmpeg Tenant"));
        var project = tenantProjectController.createProject(tenant.id(),
                new CreateProjectRequest("Native Project", "E2E"));

        String editorJson = """
                {
                  "tracks": [{
                    "id": "v1", "name": "Video", "type": "video",
                    "clips": [{
                      "id": "tc1", "clipId": "c1", "start": 0, "duration": 2,
                      "clipStart": 0, "clipEnd": 2
                    }]
                  }],
                  "clips": [{
                    "id": "c1",
                    "name": "source",
                    "sourceUrl": "file://%s"
                  }]
                }
                """.formatted(Path.of(storageRoot, "artifacts", "native-e2e", "source.mp4").toAbsolutePath());

        String snapshotId = timelineSnapshotService.save(project.id(), tenant.id(), editorJson, "2.0.0");

        RenderJobResponse job = renderController.create(
                new CreateRenderJobRequest(project.id(), snapshotId, "default_720p"));

        orchestratorService.executeExistingRenderJob(tenant.id(), job.id());

        RenderJobResponse completed = renderController.getJob(job.id());
        assertThat(completed.status()).isEqualTo("COMPLETED");

        Path output = Path.of(storageRoot, "artifacts", job.id(), "output.mp4");
        assertThat(Files.exists(output)).isTrue();
        assertThat(Files.size(output)).isGreaterThan(1000);
    }

    @Test
    @EnabledIf("meltAvailable")
    void mltRenderJob_withTimelineSnapshot_completes() throws Exception {
        if (!ffmpegAvailable()) {
            return;
        }
        var tenant = tenantProjectController.createTenant(new CreateTenantRequest("Native MLT Tenant"));
        var project = tenantProjectController.createProject(tenant.id(),
                new CreateProjectRequest("MLT Project", "E2E"));

        String sourcePath = Path.of(storageRoot, "artifacts", "native-e2e", "source.mp4").toString();
        if (!Files.exists(Path.of(sourcePath))) {
            prepareMedia(tempDir);
        }

        String editorJson = """
                {
                  "tracks": [{
                    "id": "v1", "name": "Video", "type": "video",
                    "clips": [{
                      "id": "tc1", "clipId": "c1", "start": 0, "duration": 2,
                      "clipStart": 0, "clipEnd": 2
                    }]
                  }],
                  "clips": [{
                    "id": "c1",
                    "sourceUrl": "file://%s"
                  }]
                }
                """.formatted(sourcePath);

        String snapshotId = timelineSnapshotService.save(project.id(), tenant.id(), editorJson, "2.0.0");
        RenderJobResponse job = renderController.create(
                new CreateRenderJobRequest(project.id(), snapshotId, "default_720p"));

        orchestratorService.executeExistingRenderJob(tenant.id(), job.id());

        assertThat(renderController.getJob(job.id()).status()).isEqualTo("COMPLETED");
    }
}
