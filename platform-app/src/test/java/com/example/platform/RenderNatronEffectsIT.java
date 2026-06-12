package com.example.platform;

import com.example.platform.identity.api.TenantProjectController;
import com.example.platform.identity.api.dto.CreateProjectRequest;
import com.example.platform.identity.api.dto.CreateTenantRequest;
import com.example.platform.render.api.RenderController;
import com.example.platform.entitlement.app.EntitlementPolicyService;
import com.example.platform.render.app.RenderOrchestratorService;
import com.example.platform.render.app.RenderProfileResolver;
import com.example.platform.render.app.RenderWorkerQueueService;
import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
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
 * E2E: timeline with {@code video.natron_vignette} auto-selects Natron profile and completes
 * via FFmpeg fallback when NatronRenderer is not installed.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("render-integration")
@TestPropertySource(properties = {
        "spring.ai.openai.api-key=test-key",
        "app.security.enabled=false",
        "app.identity.api-key-auth-enabled=false",
        "app.outbox.dispatch-interval-ms=999999999",
        "render.providers.natron.enabled=true",
        "render.providers.natron.fallback-to-ffmpeg=true",
        "app.render.worker-queue.enabled=true"
})
class RenderNatronEffectsIT {

    @Autowired
    private TenantProjectController tenantProjectController;

    @Autowired
    private RenderController renderController;

    @Autowired
    private RenderOrchestratorService orchestratorService;

    @Autowired
    private TimelineSnapshotService timelineSnapshotService;

    @Autowired(required = false)
    private RenderWorkerQueueService renderWorkerQueueService;

    @Autowired
    private EntitlementPolicyService entitlementPolicyService;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    @TempDir
    Path tempDir;

    static boolean ffmpegAvailable() {
        return Files.isExecutable(Path.of("/usr/bin/ffmpeg"))
                || Files.isExecutable(Path.of("/bin/ffmpeg"));
    }

    @BeforeEach
    void prepareMedia() throws Exception {
        if (!ffmpegAvailable()) {
            return;
        }
        Path input = Path.of(storageRoot, "artifacts", "natron-e2e", "source.mp4");
        Files.createDirectories(input.getParent());
        if (!Files.exists(input)) {
            ProcessBuilder pb = new ProcessBuilder(
                    "/usr/bin/ffmpeg", "-y",
                    "-f", "lavfi", "-i", "testsrc=duration=2:size=320x240:rate=24",
                    "-f", "lavfi", "-i", "sine=frequency=440:duration=2",
                    "-c:v", "libx264", "-c:a", "aac", "-shortest",
                    input.toString());
            pb.redirectErrorStream(true);
            assertThat(pb.start().waitFor()).isZero();
        }
    }

    @Test
    @EnabledIf("ffmpegAvailable")
    void natronVignetteEffect_autoProfileAndRender_completes() throws Exception {
        var tenant = tenantProjectController.createTenant(new CreateTenantRequest("Natron E2E Tenant"));
        entitlementPolicyService.setTier(tenant.id(), "PRO");
        var project = tenantProjectController.createProject(tenant.id(),
                new CreateProjectRequest("Natron Project", "E2E"));

        String sourcePath = Path.of(storageRoot, "artifacts", "natron-e2e", "source.mp4").toAbsolutePath().toString();

        String timelineJson = """
                {
                  "tracks": [{
                    "type": "VIDEO",
                    "clips": [{
                      "media_reference": "file://%s",
                      "effects": [{
                        "effectKey": "video.natron_vignette",
                        "parameters": { "intensity": 0.55 }
                      }]
                    }]
                  }]
                }
                """.formatted(sourcePath);

        String snapshotId = timelineSnapshotService.save(project.id(), tenant.id(), timelineJson, "2.0.0");

        RenderJobResponse job = renderController.create(
                new CreateRenderJobRequest(project.id(), snapshotId, "default_1080p"));

        orchestratorService.executeExistingRenderJob(tenant.id(), job.id());

        RenderJobResponse completed = renderController.getJob(job.id());
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.profile()).isEqualTo(RenderProfileResolver.NATRON_POC_1080P);

        Path output = Path.of(storageRoot, "artifacts", job.id(), "output.mp4");
        assertThat(Files.exists(output)).isTrue();
        assertThat(Files.size(output)).isGreaterThan(500);

        if (renderWorkerQueueService != null) {
            assertThat(renderWorkerQueueService.natronDepth()).isGreaterThanOrEqualTo(0);
        }
    }
}
