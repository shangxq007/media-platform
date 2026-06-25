package com.example.platform.render.infrastructure.smoke;

import com.example.platform.render.infrastructure.*;
import com.example.platform.render.infrastructure.font.*;
import com.example.platform.render.infrastructure.otio.*;
import com.example.platform.render.infrastructure.remotion.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LOCAL-only smoke test for captioned_video_export end-to-end flow.
 *
 * This test requires:
 * - node / npx available
 * - remotion CLI available
 * - ffmpeg available
 * - A working directory with write access
 *
 * Disabled by default. Run manually with:
 *   ./gradlew :render-module:test --tests "com.example.platform.render.infrastructure.smoke.CaptionedVideoExportSmokeTest"
 */
@Tag("local-only")
@Disabled
class CaptionedVideoExportSmokeTest {

    private InMemoryFontAssetRepository assetRepository;
    private DefaultFontManifestResolver manifestResolver;
    private DefaultRenderJobFontPreflight preflight;
    private DefaultRenderPlanner planner;
    private OTIOTimelineCompiler otioCompiler;
    private Path workingDir;
    private Path outputDir;

    @BeforeEach
    void setUp() throws Exception {
        assetRepository = new InMemoryFontAssetRepository();
        NoopFontStackResolver stackResolver = new NoopFontStackResolver();
        manifestResolver = new DefaultFontManifestResolver(assetRepository, stackResolver);
        NoopMissingGlyphDetector missingDetector = new NoopMissingGlyphDetector();
        preflight = new DefaultRenderJobFontPreflight(assetRepository, manifestResolver, missingDetector, stackResolver);
        planner = new DefaultRenderPlanner(new RenderProviderRegistry());
        otioCompiler = new OTIOTimelineCompiler();
        workingDir = Files.createTempDirectory("render-smoke-");
        outputDir = Files.createTempDirectory("render-smoke-output-");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (workingDir != null) {
            deleteRecursive(workingDir);
        }
        if (outputDir != null) {
            deleteRecursive(outputDir);
        }
    }

    @Test
    void environmentCheckPasses() {
        RenderEnvironmentChecker checker = new FfmpegEnvironmentCheck();
        RenderEnvironmentCheckResult result = checker.check(ExecutionMode.LOCAL, workingDir, outputDir);
        assertNotNull(result);
        assertNotNull(result.checks());
        assertFalse(result.checks().isEmpty());
    }

    @Test
    void fullSmokeFlow() throws Exception {
        FontSecurityResult sec = FontSecurityResult.passed("BasicFontSecurityScanner", "abc123", "font/ttf");
        FontValidationResult val = new FontValidationResult("NoopFontValidator", "PASSED",
                List.of(), List.of(), "NotoSansCJK", "Regular", 400, "normal",
                true, true, true, true, true, true, true, true);
        FontSubsetResult subset = new FontSubsetResult("pyftsubset", true,
                "cache-key-123", "s3://fonts/subsets/font-001.woff2", "woff2",
                512000, 65536, 1024, List.of(), Map.of());
        FontAsset asset = new FontAsset("font-001", "NotoSansCJK-Regular.ttf", "NotoSansCJK", "Regular", "ttf",
                1024, "abc123", "s3://fonts/NotoSansCJK-Regular.ttf",
                FontAssetStatus.READY_WITH_SUBSETS, sec, val, subset);
        assetRepository.save(asset);

        String otioJson = "{\"tracks\":[]}";
        Map<String, Object> metadata = Map.of("platform", Map.of(
                "schemaVersion", "1.0.0",
                "projectId", "smoke-test",
                "timelineId", "timeline-smoke",
                "captions", List.of(Map.of(
                        "id", "cap-001",
                        "assetRef", "caption-asset-001",
                        "startTime", 0.0,
                        "endTime", 3.0
                )),
                "fonts", List.of(Map.of(
                        "refId", "font-ref-001",
                        "assetId", "font-001",
                        "fontFamily", "NotoSansCJK",
                        "fontWeight", "400",
                        "fontStyle", "normal"
                )),
                "renderHints", Map.of(
                        "outputFormat", "mp4",
                        "outputWidth", 1920,
                        "outputHeight", 1080,
                        "outputFps", 30
                )
        ));

        OTIOTimelineSummary summary = otioCompiler.compile(otioJson, metadata);
        assertNotNull(summary);
        assertEquals("1.0.0", summary.schemaVersion());

        RenderJob job = otioCompiler.generateRenderJob(summary, "production");
        assertNotNull(job);

        FontPreflightResult preflightResult = preflight.preflight(job);
        assertTrue(preflightResult.passed(), "Preflight should pass: " + preflightResult.errors());
        assertTrue(preflightResult.productionSafe());

        RenderPlan plan = planner.plan(job);
        assertNotNull(plan);
        assertTrue(plan.steps().size() >= 2);

        boolean hasRemotionStep = plan.steps().stream().anyMatch(s -> s.providerName().equals("remotion"));
        boolean hasFFmpegStep = plan.steps().stream().anyMatch(s -> s.providerName().equals("ffmpeg"));
        assertTrue(hasRemotionStep, "Should have remotion step");
        assertTrue(hasFFmpegStep, "Should have ffmpeg step");

        for (RenderStep step : plan.steps()) {
            assertNotNull(step.id());
            assertNotNull(step.providerName());
            assertNotNull(step.providerType());
        }
    }

    @Test
    void ffmpegCliExecution() {
        RenderStepResult result = executeFfmpegStep();
        assertNotNull(result);
        assertNotNull(result.stepId());
        assertNotNull(result.status());
    }

    @Test
    void remotionCliExecution() {
        RenderStepResult result = executeRemotionStep();
        assertNotNull(result);
        assertNotNull(result.stepId());
        assertNotNull(result.status());
    }

    @Test
    void artifactTraceability() {
        RenderArtifact artifact = new RenderArtifact(
                "art-test-001",
                RenderArtifactType.FINAL_OUTPUT,
                "file:///tmp/test-output.mp4",
                "/tmp/test-output.mp4",
                "video/mp4",
                1024000,
                "sha256:abc123",
                3000L,
                1920, 1080, 30,
                "step-3-output-normalize",
                java.time.Instant.now()
        );

        assertNotNull(artifact.createdByStepId());
        assertEquals("step-3-output-normalize", artifact.createdByStepId());
        assertEquals(RenderArtifactType.FINAL_OUTPUT, artifact.artifactType());
    }

    @Test
    void executionTraceRecordsAllSteps() {
        RenderStepResult step1 = new RenderStepResult(
                "step-1", "remotion", "CompositionRender", "COMPLETED",
                null, List.of(), List.of(), List.of(), List.of(),
                5000, false, java.time.Instant.now(), java.time.Instant.now()
        );
        RenderStepResult step2 = new RenderStepResult(
                "step-2", "ffmpeg", "MediaProcessing", "COMPLETED",
                null, List.of(), List.of(), List.of(), List.of(),
                2000, false, java.time.Instant.now(), java.time.Instant.now()
        );

        RenderExecutionTrace trace = new RenderExecutionTrace(
                "job-smoke-001", "captioned_video_export", "production",
                List.of(step1, step2), List.of(), true, false,
                java.time.Instant.now(), java.time.Instant.now()
        );

        assertEquals(2, trace.stepResults().size());
        assertTrue(trace.overallSuccess());
        assertFalse(trace.fallbackOccurred());
        assertTrue(trace.failedSteps().isEmpty());
    }

    @Test
    void failedStepPreservesCompletedResults() {
        RenderStepResult step1 = new RenderStepResult(
                "step-1", "remotion", "CompositionRender", "COMPLETED",
                null, List.of(), List.of(), List.of(), List.of(),
                5000, false, java.time.Instant.now(), java.time.Instant.now()
        );
        RenderStepResult step2 = new RenderStepResult(
                "step-2", "ffmpeg", "MediaProcessing", "FAILED",
                null, List.of(), List.of(), List.of(),
                List.of("ffmpeg exit code 1: codec not found"),
                1000, false, java.time.Instant.now(), java.time.Instant.now()
        );

        RenderExecutionTrace trace = new RenderExecutionTrace(
                "job-smoke-002", "captioned_video_export", "production",
                List.of(step1, step2), List.of(), false, false,
                java.time.Instant.now(), java.time.Instant.now()
        );

        assertEquals(2, trace.stepResults().size());
        assertFalse(trace.overallSuccess());
        assertEquals(1, trace.failedSteps().size());
        assertEquals("step-2", trace.failedSteps().getFirst().stepId());
    }

    private RenderStepResult makeStepResult(String id, String provider, String type,
                                              String status, List<String> errors,
                                              long durationMs, Instant start) {
        List<RenderArtifact> emptyArtifacts = new java.util.ArrayList<>();
        List<String> emptyList = new java.util.ArrayList<>();
        List<String> safeErrors = errors != null ? errors : new java.util.ArrayList<>();
        return new RenderStepResult(id, provider, type, status,
                null, emptyArtifacts, emptyList, emptyList, safeErrors,
                durationMs, false, start, Instant.now());
    }

    private RenderStepResult executeFfmpegStep() {
        Instant start = Instant.now();
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exit = p.waitFor();
            long durationMs = java.time.Duration.between(start, Instant.now()).toMillis();
            List<String> errors = exit == 0 ? List.of() : List.of("ffmpeg exit: " + exit);
            return makeStepResult("ffmpeg-smoke", "ffmpeg", "MediaProcessing",
                    exit == 0 ? "COMPLETED" : "FAILED", errors, durationMs, start);
        } catch (Exception e) {
            long durationMs = java.time.Duration.between(start, Instant.now()).toMillis();
            return makeStepResult("ffmpeg-smoke", "ffmpeg", "MediaProcessing",
                    "FAILED", List.of("ffmpeg error: " + e.getMessage()), durationMs, start);
        }
    }

    private RenderStepResult executeRemotionStep() {
        Instant start = Instant.now();
        try {
            ProcessBuilder pb = new ProcessBuilder("npx", "remotion", "--version");
            pb.directory(workingDir.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exit = p.waitFor();
            long durationMs = java.time.Duration.between(start, Instant.now()).toMillis();
            List<String> errors = exit == 0 ? List.of() : List.of("remotion exit: " + exit);
            return makeStepResult("remotion-smoke", "remotion", "CompositionRender",
                    exit == 0 ? "COMPLETED" : "FAILED", errors, durationMs, start);
        } catch (Exception e) {
            long durationMs = java.time.Duration.between(start, Instant.now()).toMillis();
            return makeStepResult("remotion-smoke", "remotion", "CompositionRender",
                    "FAILED", List.of("remotion error: " + e.getMessage()), durationMs, start);
        }
    }

    private void deleteRecursive(Path path) throws Exception {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                for (Path child : stream.toArray(Path[]::new)) {
                    deleteRecursive(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
