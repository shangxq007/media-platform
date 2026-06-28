package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.app.timeline.TimelineRevisionRenderService;
import com.example.platform.render.app.timeline.compile.audit.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TimelineRevisionRenderFacadeTest {

    private final RenderAuditRecorder auditRecorder =
            new RenderAuditRecorder(new NoopRenderAuditEventSink());

    @Test
    @DisplayName("Default properties use LEGACY mode")
    void defaultPropertiesUseLegacy() {
        TimelineRenderExecutionProperties props = TimelineRenderExecutionProperties.defaults();
        assertEquals(TimelineRenderExecutionMode.LEGACY, props.executionMode());
        assertTrue(props.isLegacyEnabled());
        assertFalse(props.isPlanBasedEnabled());
    }

    @Test
    @DisplayName("PLAN_BASED properties report correctly")
    void planBasedPropertiesReport() {
        TimelineRenderExecutionProperties props =
                new TimelineRenderExecutionProperties(TimelineRenderExecutionMode.PLAN_BASED);
        assertEquals(TimelineRenderExecutionMode.PLAN_BASED, props.executionMode());
        assertFalse(props.isLegacyEnabled());
        assertTrue(props.isPlanBasedEnabled());
    }

    @Test
    @DisplayName("LEGACY mode delegates to legacy service")
    void legacyModeDelegatesToLegacy() {
        MockLegacyService legacy = new MockLegacyService();
        legacy.result = createTestResult();
        MockPlanBasedService planBased = new MockPlanBasedService();
        MockDedupService dedup = new MockDedupService();
        TimelineRevisionRenderFacade facade = new TimelineRevisionRenderFacade(
                legacy, planBased, dedup, TimelineRenderExecutionProperties.defaults(), auditRecorder);
        facade.render("proj-1", "rev-1", "default_1080p");
        assertTrue(legacy.called);
        assertFalse(planBased.called);
    }

    @Test
    @DisplayName("PLAN_BASED mode delegates to plan-based service")
    void planBasedModeDelegatesToPlanBased() {
        MockLegacyService legacy = new MockLegacyService();
        MockPlanBasedService planBased = new MockPlanBasedService();
        planBased.result = createTestResult();
        MockDedupService dedup = new MockDedupService();
        TimelineRevisionRenderFacade facade = new TimelineRevisionRenderFacade(
                legacy, planBased, dedup,
                new TimelineRenderExecutionProperties(TimelineRenderExecutionMode.PLAN_BASED), auditRecorder);
        facade.render("proj-1", "rev-1", "default_1080p");
        assertFalse(legacy.called);
        assertTrue(planBased.called);
    }

    @Test
    @DisplayName("Both paths return same result contract")
    void bothPathsReturnSameContract() {
        TimelineRevisionRenderService.RevisionRenderResult expectedResult = createTestResult();
        MockLegacyService legacy = new MockLegacyService();
        legacy.result = expectedResult;
        MockPlanBasedService planBased = new MockPlanBasedService();
        planBased.result = expectedResult;
        MockDedupService dedup = new MockDedupService();

        TimelineRevisionRenderFacade legacyFacade = new TimelineRevisionRenderFacade(
                legacy, planBased, dedup, TimelineRenderExecutionProperties.defaults(), auditRecorder);
        TimelineRevisionRenderService.RevisionRenderResult legacyResult =
                legacyFacade.render("proj-1", "rev-1", "default_1080p");

        TimelineRevisionRenderFacade planFacade = new TimelineRevisionRenderFacade(
                legacy, planBased, dedup,
                new TimelineRenderExecutionProperties(TimelineRenderExecutionMode.PLAN_BASED), auditRecorder);
        TimelineRevisionRenderService.RevisionRenderResult planResult =
                planFacade.render("proj-1", "rev-1", "default_1080p");

        assertEquals(legacyResult.outputProductId(), planResult.outputProductId());
        assertEquals(legacyResult.productStatus(), planResult.productStatus());
    }

    @Test
    @DisplayName("Facade isPlanBasedEnabled reflects configuration")
    void facadeReflectsConfiguration() {
        MockLegacyService legacy = new MockLegacyService();
        MockPlanBasedService planBased = new MockPlanBasedService();
        MockDedupService dedup = new MockDedupService();

        TimelineRevisionRenderFacade legacyFacade = new TimelineRevisionRenderFacade(
                legacy, planBased, dedup, TimelineRenderExecutionProperties.defaults(), auditRecorder);
        assertFalse(legacyFacade.isPlanBasedEnabled());

        TimelineRevisionRenderFacade planFacade = new TimelineRevisionRenderFacade(
                legacy, planBased, dedup,
                new TimelineRenderExecutionProperties(TimelineRenderExecutionMode.PLAN_BASED), auditRecorder);
        assertTrue(planFacade.isPlanBasedEnabled());
    }

    @Test
    @DisplayName("Dedup reuses READY product when available")
    void dedupReusesReadyProduct() {
        MockLegacyService legacy = new MockLegacyService();
        MockPlanBasedService planBased = new MockPlanBasedService();
        MockDedupService dedup = new MockDedupService();
        dedup.reuseResult = createTestResult();
        TimelineRevisionRenderFacade facade = new TimelineRevisionRenderFacade(
                legacy, planBased, dedup, TimelineRenderExecutionProperties.defaults(), auditRecorder);
        TimelineRevisionRenderService.RevisionRenderResult result =
                facade.render("proj-1", "rev-1", "default_1080p");
        assertFalse(legacy.called);
        assertFalse(planBased.called);
        assertEquals("prod-1", result.outputProductId());
    }

    @Test
    @DisplayName("Dedup fails closed on error")
    void dedupFailsClosedOnError() {
        MockLegacyService legacy = new MockLegacyService();
        MockPlanBasedService planBased = new MockPlanBasedService();
        MockDedupService dedup = new MockDedupService();
        dedup.failLookup = true;
        TimelineRevisionRenderFacade facade = new TimelineRevisionRenderFacade(
                legacy, planBased, dedup, TimelineRenderExecutionProperties.defaults(), auditRecorder);
        assertThrows(IllegalStateException.class, () ->
                facade.render("proj-1", "rev-1", "default_1080p"));
    }

    @Test
    @DisplayName("Facade emits audit events")
    void facadeEmitsAuditEvents() {
        InMemoryRenderAuditEventSink sink = new InMemoryRenderAuditEventSink();
        RenderAuditRecorder recorder = new RenderAuditRecorder(sink);
        MockLegacyService legacy = new MockLegacyService();
        legacy.result = createTestResult();
        MockDedupService dedup = new MockDedupService();
        TimelineRevisionRenderFacade facade = new TimelineRevisionRenderFacade(
                legacy, new MockPlanBasedService(), dedup,
                TimelineRenderExecutionProperties.defaults(), recorder);
        facade.render("proj-1", "rev-1", "default_1080p");
        List<RenderAuditEvent> events = sink.findAll();
        assertTrue(events.stream().anyMatch(e -> e.eventType() == RenderAuditEventType.RENDER_REQUEST_RECEIVED));
        assertTrue(events.stream().anyMatch(e -> e.eventType() == RenderAuditEventType.RENDER_DEDUP_CHECKED));
        assertTrue(events.stream().anyMatch(e -> e.eventType() == RenderAuditEventType.RENDER_NEW_ATTEMPT_STARTED));
        assertTrue(events.stream().anyMatch(e -> e.eventType() == RenderAuditEventType.RENDER_COMPLETED));
    }

    private TimelineRevisionRenderService.RevisionRenderResult createTestResult() {
        return new TimelineRevisionRenderService.RevisionRenderResult(
                "rj-1", "rev-1", "snap-1", "prod-1", "READY", "stor-1", "video/mp4",
                "mp4", 1920, 1080, 30, 5.0, false, "ffmpeg-libass", "test", List.of("input-1"), 1);
    }

    static class MockDedupService extends RenderDeduplicationService {
        TimelineRevisionRenderService.RevisionRenderResult reuseResult;
        boolean failLookup = false;
        MockDedupService() { super(null); }
        @Override
        public RenderDeduplicationDecision check(String projectId, String timelineRevisionId,
                                                   String outputProfile, String executionMode) {
            RenderRequestFingerprint fp = RenderRequestFingerprint.generate(
                    projectId, timelineRevisionId, outputProfile, executionMode);
            if (failLookup) return RenderDeduplicationDecision.failedClosed(fp, "Mock lookup failure");
            if (reuseResult != null) return RenderDeduplicationDecision.reuse(fp, reuseResult, "Mock reuse");
            return RenderDeduplicationDecision.proceed(fp, RenderDeduplicationReason.NO_EXISTING_RENDER, "Mock proceed");
        }
    }

    static class MockLegacyService extends TimelineRevisionRenderService {
        boolean called = false;
        TimelineRevisionRenderService.RevisionRenderResult result;
        MockLegacyService() { super(null, null, null, null, null, null, null, null, null, null, null); }
        @Override
        public RevisionRenderResult render(String projectId, String revisionId, String outputProfile) {
            called = true; return result;
        }
    }

    static class MockPlanBasedService extends PlanBasedTimelineRevisionRenderService {
        boolean called = false;
        TimelineRevisionRenderService.RevisionRenderResult result;
        MockPlanBasedService() { super(null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null); }
        @Override
        public TimelineRevisionRenderService.RevisionRenderResult render(
                String projectId, String revisionId, String outputProfile) {
            called = true; return result;
        }
    }
}
