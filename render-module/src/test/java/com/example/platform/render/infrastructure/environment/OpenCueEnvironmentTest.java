package com.example.platform.render.infrastructure.environment;

import com.example.platform.render.app.environment.EnvironmentRuntimeService;
import com.example.platform.render.app.execution.ExecutionControlService;
import com.example.platform.render.app.execution.ExecutionJobRegistry;
import com.example.platform.render.domain.environment.*;
import com.example.platform.render.domain.execution.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenCue Environment integration tests: lifecycle mapping, architecture boundaries,
 * compiler determinism, and production safety.
 *
 * <p>No real OpenCue cluster dependency. Uses safe defaults (disabled, stub mode).
 */
class OpenCueEnvironmentTest {

    private OpenCueProperties props;
    private OpenCueJobSpecValidator jobSpecValidator;
    private OpenCueExecutionEnvironment environment;
    private OpenCueEnvironmentCompiler compiler;
    private EnvironmentRuntimeService runtimeService;
    private ExecutionControlService controlService;
    private ExecutionJobRegistry jobRegistry;

    @BeforeEach
    void setUp() {
        props = new OpenCueProperties();
        jobSpecValidator = new OpenCueJobSpecValidator(props);
        environment = new OpenCueExecutionEnvironment(props, jobSpecValidator);
        compiler = new OpenCueEnvironmentCompiler(props);
        runtimeService = new EnvironmentRuntimeService(
                List.of(environment),
                List.of(compiler)
        );
        jobRegistry = new ExecutionJobRegistry();
        controlService = new ExecutionControlService(runtimeService, jobRegistry);
    }

    private static BackendExecutionSpec createTestSpec(String backendId, String producerId,
                                                        String executable, List<String> args) {
        return LocalProcessExecutionSpec.of(backendId, producerId,
                List.of(ExecutionInput.of("prod-1", "ref-1")),
                List.of(ExecutionOutput.of("MEDIA_FILE", "mp4")),
                executable, args);
    }

    @Test
    void identifiesAsOpenCueEnvironment() {
        assertEquals("opencue", environment.environmentId());
        assertEquals("opencue", environment.environmentType());
    }

    @Test
    void registeredInRuntimeService() {
        assertTrue(runtimeService.resolve("opencue").isPresent());
        assertTrue(runtimeService.resolveCompiler("opencue").isPresent());
    }

    @Test
    void isExecutionEnvironmentNotExecutionBackend() {
        assertTrue(environment instanceof ExecutionEnvironment,
                "OpenCue must be registered as ExecutionEnvironment, not ExecutionBackend");
    }

    @Test
    void compilerIsEnvironmentCompiler() {
        assertTrue(compiler instanceof EnvironmentCompiler,
                "OpenCueCompiler must be registered as EnvironmentCompiler");
    }

    @Test
    void compilerProducesDeterministicOutput() {
        BackendExecutionSpec spec1 = createTestSpec("ffmpeg-backend", "ffmpeg-producer",
                "ffmpeg", List.of("-i", "input.mp4", "output.mp4"));
        BackendExecutionSpec spec2 = createTestSpec("ffmpeg-backend", "ffmpeg-producer",
                "ffmpeg", List.of("-i", "input.mp4", "output.mp4"));

        ExecutionJob job1 = compiler.compile(spec1);
        ExecutionJob job2 = compiler.compile(spec2);

        assertEquals(job1.environmentId(), job2.environmentId());
        assertEquals(job1.backendId(), job2.backendId());
        assertEquals(job1.backendType(), job2.backendType());
        assertEquals(job1.tasks().size(), job2.tasks().size());
        assertEquals(job1.priority(), job2.priority());
    }

    @Test
    void submitRejectedWhenDisabled() {
        props.setEnabled(false);
        BackendExecutionSpec spec = createTestSpec("ffmpeg", "producer",
                "ffmpeg", List.of("-version"));
        ExecutionJob job = compiler.compile(spec);
        assertThrows(IllegalStateException.class, () -> environment.submit(job));
    }

    @Test
    void submitAllowedInStubMode() {
        props.setEnabled(true);
        props.setStubModeEnabled(true);
        BackendExecutionSpec spec = createTestSpec("ffmpeg", "producer",
                "ffmpeg", List.of("-version"));
        ExecutionJob job = compiler.compile(spec);
        String execId = environment.submit(job);
        assertNotNull(execId);
        assertTrue(execId.startsWith("oc-"));
    }

    @Test
    void cancelRejectedWhenDisabled() {
        props.setEnabled(false);
        assertFalse(environment.cancel("oc-123"));
    }

    @Test
    void cancelSucceedsWhenEnabled() {
        props.setEnabled(true);
        assertTrue(environment.cancel("oc-123"));
    }

    @Test
    void statusReturnsDeadWhenDisabled() {
        props.setEnabled(false);
        assertEquals("dead", environment.status("oc-123"));
    }

    @Test
    void statusReturnsQueuedInStubMode() {
        props.setEnabled(true);
        assertEquals("queued", environment.status("oc-123"));
    }

    @Test
    void lifecycleMappingPendingToSubmitted() {
        assertEquals(ExecutionStatus.SUBMITTED,
                environment.mapOpenCueStatusToPlatform("pending"));
    }

    @Test
    void lifecycleMappingQueuedToQueued() {
        assertEquals(ExecutionStatus.QUEUED,
                environment.mapOpenCueStatusToPlatform("queued"));
    }

    @Test
    void lifecycleMappingRunningToRunning() {
        assertEquals(ExecutionStatus.RUNNING,
                environment.mapOpenCueStatusToPlatform("running"));
    }

    @Test
    void lifecycleMappingSucceededToCompleted() {
        assertEquals(ExecutionStatus.COMPLETED,
                environment.mapOpenCueStatusToPlatform("succeeded"));
    }

    @Test
    void lifecycleMappingDeadToFailed() {
        assertEquals(ExecutionStatus.FAILED,
                environment.mapOpenCueStatusToPlatform("dead"));
    }

    @Test
    void lifecycleMappingKilledToCancelled() {
        assertEquals(ExecutionStatus.CANCELLED,
                environment.mapOpenCueStatusToPlatform("killed"));
    }

    @Test
    void lifecycleMappingDependentToQueued() {
        assertEquals(ExecutionStatus.QUEUED,
                environment.mapOpenCueStatusToPlatform("dependent"));
    }

    @Test
    void lifecycleMappingNullFailsToFailed() {
        assertEquals(ExecutionStatus.FAILED,
                environment.mapOpenCueStatusToPlatform(null));
    }

    @Test
    void lifecycleMappingBlankFailsToFailed() {
        assertEquals(ExecutionStatus.FAILED,
                environment.mapOpenCueStatusToPlatform("  "));
    }

    @Test
    void lifecycleMappingUnknownFailsToFailed() {
        assertEquals(ExecutionStatus.FAILED,
                environment.mapOpenCueStatusToPlatform("bogus-state"));
    }

    @Test
    void lifecycleMappingIsCaseInsensitive() {
        assertEquals(ExecutionStatus.COMPLETED,
                environment.mapOpenCueStatusToPlatform("SUCCEEDED"));
        assertEquals(ExecutionStatus.RUNNING,
                environment.mapOpenCueStatusToPlatform("Running"));
    }

    @Test
    void noNewLifecycleStatesAdded() {
        ExecutionStatus[] states = ExecutionStatus.values();
        assertEquals(9, states.length,
                "ExecutionStatus must have exactly 9 states (no new states added)");
    }

    @Test
    void supportsReturnsFalseWhenDisabled() {
        props.setEnabled(false);
        assertFalse(environment.supports(List.of("MEDIA_PIPELINE")));
    }

    @Test
    void supportsReturnsTrueWhenEnabledAndMatching() {
        props.setEnabled(true);
        assertTrue(environment.supports(List.of("MEDIA_PIPELINE")));
        assertTrue(environment.supports(List.of("TRANSCODE")));
    }

    @Test
    void supportsReturnsFalseForUnsupportedCapability() {
        props.setEnabled(true);
        assertFalse(environment.supports(List.of("ASR")));
    }

    @Test
    void controlServiceSubmitRejectedWhenOpenCueDisabled() {
        props.setEnabled(false);
        BackendExecutionSpec spec = createTestSpec("ffmpeg", "producer",
                "ffmpeg", List.of("-version"));
        ExecutionJob job = compiler.compile(spec);
        assertThrows(IllegalStateException.class, () ->
                controlService.submit(job));
    }

    @Test
    void doesNotRequireBackendCompilerSpiChanges() {
        assertDoesNotThrow(() -> {
            Class<?> spiClass = Class.forName(
                    "com.example.platform.render.domain.execution.BackendCompiler");
            assertTrue(spiClass.isInterface());
        }, "BackendCompiler SPI must exist unchanged");
    }

    @Test
    void doesNotRequireExecutionBackendSpiChanges() {
        assertDoesNotThrow(() -> {
            Class<?> backendClass = Class.forName(
                    "com.example.platform.outbox.app.ExecutionBackend");
            assertTrue(backendClass.isInterface());
        }, "ExecutionBackend SPI must exist unchanged");
    }

    @Test
    void doesNotBypassExecutionControlService() {
        assertNotNull(controlService);

        BackendExecutionSpec spec = createTestSpec("ffmpeg", "producer",
                "ffmpeg", List.of("-version"));
        ExecutionJob job = compiler.compile(spec);
        assertEquals(ExecutionStatus.CREATED, job.status(),
                "Compiler must produce CREATED status for control service to transition");
    }

    @Test
    void isNotRegisteredAsRenderProvider() {
        assertFalse(environment instanceof com.example.platform.render.infrastructure.RenderProvider,
                "OpenCue must not be a RenderProvider");
    }

    @Test
    void stubSubmitDoNotRequireProductionSubmit() {
        props.setEnabled(true);
        props.setStubModeEnabled(true);
        props.setProductionSubmitEnabled(false);
        BackendExecutionSpec spec = createTestSpec("ffmpeg", "producer",
                "ffmpeg", List.of("-version"));
        ExecutionJob job = compiler.compile(spec);
        String execId = environment.submit(job);
        assertTrue(execId.startsWith("oc-"),
                "stub submit must work even when production submit is disabled");
    }

    @Test
    void nonStubSubmitRequiresProductionSubmit() {
        props.setEnabled(true);
        props.setStubModeEnabled(false);
        props.setProductionSubmitEnabled(false);
        BackendExecutionSpec spec = createTestSpec("ffmpeg", "producer",
                "ffmpeg", List.of("-version"));
        ExecutionJob job = compiler.compile(spec);
        assertThrows(IllegalStateException.class, () -> environment.submit(job),
                "Submit must be rejected when neither stub nor production submit is enabled");
    }

    @Test
    void validJobSpecFromFactoryMethod() {
        OpenCueJobSpec spec = OpenCueJobSpec.fromJobId("test-job");
        assertEquals("platform-test-job", spec.jobName());
        assertEquals("platform", spec.owner());
        assertEquals(50, spec.priority());
    }
}
