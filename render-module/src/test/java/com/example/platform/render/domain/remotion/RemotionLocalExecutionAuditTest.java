package com.example.platform.render.domain.remotion;

import com.example.platform.render.app.timeline.compile.RenderCorrelationContext;
import com.example.platform.render.app.timeline.compile.audit.*;
import com.example.platform.render.domain.timeline.compile.remotion.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Remotion runner audit/correlation precheck events.
 * Proves: safe payloads, no secrets/paths/commands, runner unaffected by audit failure.
 */
class RemotionLocalExecutionAuditTest {

    private InMemoryRenderAuditEventSink sink;
    private RenderAuditRecorder recorder;

    @BeforeEach
    void setUp() {
        sink = new InMemoryRenderAuditEventSink();
        recorder = new RenderAuditRecorder(sink);
    }

    // --- Audit event emission ---

    @Test
    @DisplayName("Null request emits FAILED_CLOSED audit event")
    void nullRequestEmitsAudit() {
        RemotionLocalExecutionRunner runner = new RemotionLocalExecutionRunner(recorder);
        runner.execute(null);

        List<RenderAuditEvent> events = sink.findAll();
        assertFalse(events.isEmpty());
        assertTrue(events.stream().anyMatch(e ->
                e.eventType() == RenderAuditEventType.PROVIDER_LOCAL_EXECUTION_PRECHECK_REJECTED));
    }

    @Test
    @DisplayName("BLOCKED_BY_POLICY emits rejected audit event")
    void blockedByPolicyEmitsAudit() {
        RemotionLocalExecutionRunner runner = new RemotionLocalExecutionRunner(recorder);
        RemotionLocalExecutionRequest request = new RemotionLocalExecutionRequest(
                null, null, createReadiness(),
                RemotionExecutionPolicy.manualExperimentDesignOnly(),
                RemotionSandboxPolicy.lockedDown(), null, Map.of());

        runner.execute(request);

        assertTrue(sink.findAll().stream().anyMatch(e ->
                e.eventType() == RenderAuditEventType.PROVIDER_LOCAL_EXECUTION_PRECHECK_REJECTED));
    }

    @Test
    @DisplayName("READY_BUT_EXECUTION_DISABLED emits not-implemented audit event")
    void readyButDisabledEmitsAudit() {
        RemotionLocalExecutionRunner runner = new RemotionLocalExecutionRunner(recorder);
        RemotionLocalExecutionRequest request = new RemotionLocalExecutionRequest(
                null, null, null,
                RemotionExecutionPolicy.disabledDefault(),
                RemotionSandboxPolicy.lockedDown(), null, Map.of());

        runner.execute(request);

        assertTrue(sink.findAll().stream().anyMatch(e ->
                e.eventType() == RenderAuditEventType.PROVIDER_LOCAL_EXECUTION_NOT_IMPLEMENTED));
    }

    @Test
    @DisplayName("Audit event includes providerName=remotion")
    void auditIncludesProviderName() {
        RemotionLocalExecutionRunner runner = new RemotionLocalExecutionRunner(recorder);
        runner.execute(new RemotionLocalExecutionRequest(
                null, null, null,
                RemotionExecutionPolicy.disabledDefault(),
                RemotionSandboxPolicy.lockedDown(), null, Map.of()));

        assertTrue(sink.findAll().stream()
                .allMatch(e -> "remotion".equals(e.providerName())));
    }

    @Test
    @DisplayName("Audit event includes correlation context when provided")
    void auditIncludesCorrelation() {
        RenderCorrelationContext corr = RenderCorrelationContext.create("p-1", "r-1", "MANUAL")
                .withFingerprint("rfp-test");
        RemotionLocalExecutionRunner runner = new RemotionLocalExecutionRunner(recorder);
        runner.execute(new RemotionLocalExecutionRequest(
                null, null, null,
                RemotionExecutionPolicy.disabledDefault(),
                RemotionSandboxPolicy.lockedDown(), null, corr, Map.of()));

        RenderAuditEvent event = sink.findAll().get(0);
        assertNotNull(event.renderCorrelationId());
        assertEquals("rfp-test", event.renderRequestFingerprint());
    }

    @Test
    @DisplayName("Audit event does not include serialized document")
    void auditNoSerializedDocument() {
        ProviderExecutionDocumentGenerationResult doc = new ProviderExecutionDocumentGenerationResult(
                "doc-1", "draft-1", "remotion", "REMOTION_INPUT_PROPS_DOCUMENT",
                ProviderExecutionDocumentGenerationStatus.GENERATED,
                false, true, List.of(), "{\"composition\":{}}", Map.of());

        RemotionLocalExecutionRunner runner = new RemotionLocalExecutionRunner(recorder);
        runner.execute(new RemotionLocalExecutionRequest(
                doc, null, null,
                RemotionExecutionPolicy.disabledDefault(),
                RemotionSandboxPolicy.lockedDown(), null, Map.of()));

        sink.findAll().forEach(event -> {
            if (event.sanitizedDetails() != null) {
                assertFalse(event.sanitizedDetails().contains("{\"composition\""),
                        "Audit must not contain serialized document");
            }
        });
    }

    @Test
    @DisplayName("Audit event does not contain raw command")
    void auditNoRawCommand() {
        RemotionLocalExecutionRunner runner = new RemotionLocalExecutionRunner(recorder);
        runner.execute(new RemotionLocalExecutionRequest(
                null, null, null,
                RemotionExecutionPolicy.disabledDefault(),
                RemotionSandboxPolicy.lockedDown(), null, Map.of()));

        sink.findAll().forEach(event -> {
            // Message field is safe human-readable status, not raw commands
            assertNotNull(event.message());
            assertFalse(event.message().contains("ffmpeg -i"));
            assertFalse(event.message().contains("npx remotion"));
        });
    }

    @Test
    @DisplayName("Audit event does not contain local paths or storage internals")
    void auditNoLocalPathsOrStorage() {
        RemotionLocalExecutionRunner runner = new RemotionLocalExecutionRunner(recorder);
        runner.execute(new RemotionLocalExecutionRequest(
                null, null, null,
                RemotionExecutionPolicy.disabledDefault(),
                RemotionSandboxPolicy.lockedDown(), null, Map.of()));

        sink.findAll().forEach(event -> {
            String all = event.toString();
            assertFalse(all.contains("/tmp"));
            assertFalse(all.contains("/home"));
            assertFalse(all.contains("\"bucket\""));
            assertFalse(all.contains("\"objectKey\""));
            assertFalse(all.contains("\"signedUrl\""));
        });
    }

    // --- Audit failure safety ---

    @Test
    @DisplayName("Audit failure does not break runner")
    void auditFailureDoesNotBreakRunner() {
        RenderAuditEventSink failingSink = new RenderAuditEventSink() {
            @Override public void record(RenderAuditEvent e) { throw new RuntimeException("fail"); }
            @Override public List<RenderAuditEvent> findAll() { return List.of(); }
            @Override public List<RenderAuditEvent> findByRenderJobId(String id) { return List.of(); }
            @Override public List<RenderAuditEvent> findByProjectId(String id) { return List.of(); }
            @Override public void clear() {}
        };
        RenderAuditRecorder failingRecorder = new RenderAuditRecorder(failingSink);
        RemotionLocalExecutionRunner runner = new RemotionLocalExecutionRunner(failingRecorder);

        RemotionLocalExecutionResult result = runner.execute(new RemotionLocalExecutionRequest(
                null, null, null,
                RemotionExecutionPolicy.disabledDefault(),
                RemotionSandboxPolicy.lockedDown(), null, Map.of()));

        assertFalse(result.executed());
        assertFalse(result.readyToExecute());
    }

    @Test
    @DisplayName("Runner without recorder still works")
    void runnerWithoutRecorderStillWorks() {
        RemotionLocalExecutionRunner runner = new RemotionLocalExecutionRunner();

        RemotionLocalExecutionResult result = runner.execute(new RemotionLocalExecutionRequest(
                null, null, null,
                RemotionExecutionPolicy.disabledDefault(),
                RemotionSandboxPolicy.lockedDown(), null, Map.of()));

        assertFalse(result.executed());
        assertEquals(RemotionLocalExecutionStatus.NOT_IMPLEMENTED, result.status());
    }

    @Test
    @DisplayName("Runner result always has executed=false and readyToExecute=false")
    void resultAlwaysSafe() {
        RemotionLocalExecutionRunner runner = new RemotionLocalExecutionRunner(recorder);

        for (var policy : List.of(
                RemotionExecutionPolicy.disabledDefault(),
                RemotionExecutionPolicy.manualExperimentDesignOnly())) {
            RemotionLocalExecutionResult result = runner.execute(new RemotionLocalExecutionRequest(
                    null, null, null, policy, RemotionSandboxPolicy.lockedDown(), null, Map.of()));
            assertFalse(result.executed());
            assertFalse(result.readyToExecute());
            assertNull(result.outputProductId());
            assertNull(result.outputPathRef());
        }
    }

    // --- Helpers ---

    private RemotionProviderReadiness createReadiness() {
        return RemotionProviderReadiness.from(
                new com.example.platform.render.infrastructure.RenderToolCapabilityInventory() {
                    @Override public boolean isToolAvailable(String n) { return "ffmpeg".equals(n); }
                }.detectTools().isEmpty()
                ? RemotionRuntimeAvailability.notChecked()
                : new RemotionRuntimeProbe(
                        new com.example.platform.render.infrastructure.RenderToolCapabilityInventory()).probe());
    }
}
