package com.example.platform;

import com.example.platform.entitlement.app.EntitlementPolicyService;
import com.example.platform.identity.api.TenantProjectController;
import com.example.platform.identity.api.dto.CreateProjectRequest;
import com.example.platform.identity.api.dto.CreateTenantRequest;
import com.example.platform.render.api.RenderController;
import com.example.platform.render.app.RenderOrchestratorService;
import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.app.planner.PipelinePlanPersistenceService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E: multi-track timeline triggers pipeline DAG path and persists execution plan.
 */
@Tag("render-integration")
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.ai.openai.api-key=test-key",
        "app.security.enabled=false",
        "app.identity.api-key-auth-enabled=false",
        "app.outbox.dispatch-interval-ms=999999999",
        "render.pipeline.dag.enabled=true",
        "render.providers.mlt.enabled=false"
})
class RenderPipelineDagIT {

    @Autowired
    private TenantProjectController tenantProjectController;
    @Autowired
    private RenderController renderController;
    @Autowired
    private RenderOrchestratorService orchestratorService;
    @Autowired
    private TimelineSnapshotService timelineSnapshotService;
    @Autowired
    private EntitlementPolicyService entitlementPolicyService;
    @Autowired
    private PipelinePlanPersistenceService pipelinePlanPersistence;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    static boolean ffmpegAvailable() {
        return Files.isExecutable(Path.of("/usr/bin/ffmpeg"))
                || Files.isExecutable(Path.of("/bin/ffmpeg"));
    }

    @BeforeEach
    void prepareMedia() throws Exception {
        if (!ffmpegAvailable()) {
            return;
        }
        Path input = Path.of(storageRoot, "artifacts", "dag-e2e", "source.mp4");
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
    void multiTrackTimeline_executesDagAndPersistsPlan() throws Exception {
        var tenant = tenantProjectController.createTenant(new CreateTenantRequest("DAG E2E Tenant"));
        entitlementPolicyService.setTier(tenant.id(), "TEAM");
        var project = tenantProjectController.createProject(tenant.id(),
                new CreateProjectRequest("DAG Project", "E2E"));

        String sourcePath = Path.of(storageRoot, "artifacts", "dag-e2e", "source.mp4").toAbsolutePath().toString();
        String timelineJson = """
                {
                  "id": "tl-dag-e2e",
                  "finalComposer": "ffmpeg",
                  "tracks": [
                    {
                      "type": "VIDEO",
                      "clips": [{
                        "media_reference": "file://%s",
                        "clipDuration": 2, "timelineStart": 0, "assetInPoint": 0, "assetOutPoint": 2
                      }]
                    },
                    {
                      "type": "VIDEO",
                      "clips": [{
                        "media_reference": "file://%s",
                        "clipDuration": 2, "timelineStart": 0, "assetInPoint": 0, "assetOutPoint": 2
                      }]
                    }
                  ],
                  "outputSpec": {
                    "format": "mp4", "resolution": "1280x720", "frameRate": 24, "width": 1280, "height": 720
                  }
                }
                """.formatted(sourcePath, sourcePath);

        String snapshotId = timelineSnapshotService.save(project.id(), tenant.id(), timelineJson, "2.0.0");
        RenderJobResponse job = renderController.createRenderJob(tenant.id(), project.id(), 
                new CreateRenderJobRequest(project.id(), snapshotId, "default_720p"));
        orchestratorService.executeExistingRenderJob(tenant.id(), job.id());

        RenderJobResponse completed = renderController.getRenderJob(tenant.id(), project.id(), job.id());
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(pipelinePlanPersistence.loadPlan(job.id())).isPresent();
        var execution = pipelinePlanPersistence.loadExecutionState(job.id());
        assertThat(execution).isPresent();
        assertThat(execution.get()).containsKey("pipelineSuccess");
    }
}
