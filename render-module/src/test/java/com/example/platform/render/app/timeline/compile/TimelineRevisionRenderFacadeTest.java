package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.app.timeline.TimelineRevisionRenderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TimelineRevisionRenderFacade}.
 *
 * <p>Proves:
 * <ul>
 *   <li>LEGACY mode delegates to legacy service</li>
 *   <li>PLAN_BASED mode delegates to plan-based service</li>
 *   <li>Default properties use LEGACY mode</li>
 *   <li>Facade reports correct execution mode</li>
 *   <li>Both paths return same result contract</li>
 *   <li>Dedup reuses READY product</li>
 *   <li>Dedup fails closed on error</li>
 * </ul>
 */
class TimelineRevisionRenderFacadeTest {

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
        MockPlanBasedService planBased = new MockPlanBasedService();
        MockDedupService dedup = new MockDedupService();
        TimelineRenderExecutionProperties props = TimelineRenderExecutionProperties.defaults();

        TimelineRevisionRenderFacade facade = new TimelineRevisionRenderFacade(
                legacy, planBased, dedup, props);

        facade.render("proj-1", "rev-1", "default_1080p");

        assertTrue(legacy.called, "Legacy service should be called");
        assertFalse(planBased.called, "Plan-based service should NOT be called");
        assertEquals(TimelineRenderExecutionMode.LEGACY, facade.getExecutionMode());
    }

    @Test
    @DisplayName("PLAN_BASED mode delegates to plan-based service")
    void planBasedModeDelegatesToPlanBased() {
        MockLegacyService legacy = new MockLegacyService();
        MockPlanBasedService planBased = new MockPlanBasedService();
        MockDedupService dedup = new MockDedupService();
        TimelineRenderExecutionProperties props =
                new TimelineRenderExecutionProperties(TimelineRenderExecutionMode.PLAN_BASED);

        TimelineRevisionRenderFacade facade = new TimelineRevisionRenderFacade(
                legacy, planBased, dedup, props);

        facade.render("proj-1", "rev-1", "default_1080p");

        assertFalse(legacy.called, "Legacy service should NOT be called");
        assertTrue(planBased.called, "Plan-based service should be called");
        assertEquals(TimelineRenderExecutionMode.PLAN_BASED, facade.getExecutionMode());
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

        // LEGACY
        TimelineRevisionRenderFacade legacyFacade = new TimelineRevisionRenderFacade(
                legacy, planBased, dedup, TimelineRenderExecutionProperties.defaults());
        TimelineRevisionRenderService.RevisionRenderResult legacyResult =
                legacyFacade.render("proj-1", "rev-1", "default_1080p");

        // PLAN_BASED
        TimelineRevisionRenderFacade planFacade = new TimelineRevisionRenderFacade(
                legacy, planBased, dedup,
                new TimelineRenderExecutionProperties(TimelineRenderExecutionMode.PLAN_BASED));
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
                legacy, planBased, dedup, TimelineRenderExecutionProperties.defaults());
        assertFalse(legacyFacade.isPlanBasedEnabled());

        TimelineRevisionRenderFacade planFacade = new TimelineRevisionRenderFacade(
                legacy, planBased, dedup,
                new TimelineRenderExecutionProperties(TimelineRenderExecutionMode.PLAN_BASED));
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
                legacy, planBased, dedup, TimelineRenderExecutionProperties.defaults());

        TimelineRevisionRenderService.RevisionRenderResult result =
                facade.render("proj-1", "rev-1", "default_1080p");

        assertFalse(legacy.called, "Legacy should NOT be called when dedup reuses");
        assertFalse(planBased.called, "Plan-based should NOT be called when dedup reuses");
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
                legacy, planBased, dedup, TimelineRenderExecutionProperties.defaults());

        assertThrows(IllegalStateException.class, () ->
                facade.render("proj-1", "rev-1", "default_1080p"));
    }

    // --- Helpers ---

    private TimelineRevisionRenderService.RevisionRenderResult createTestResult() {
        return new TimelineRevisionRenderService.RevisionRenderResult(
                "rj-1", "rev-1", "snap-1",
                "prod-1", "READY", "stor-1", "video/mp4",
                "mp4", 1920, 1080, 30, 5.0, false,
                "ffmpeg-libass", "test", List.of("input-1"), 1);
    }

    // --- Mocks ---

    static class MockDedupService extends RenderDeduplicationService {
        TimelineRevisionRenderService.RevisionRenderResult reuseResult;
        boolean failLookup = false;

        MockDedupService() {
            super(null);
        }

        @Override
        public RenderDeduplicationDecision check(String projectId, String timelineRevisionId,
                                                   String outputProfile, String executionMode) {
            RenderRequestFingerprint fp = RenderRequestFingerprint.generate(
                    projectId, timelineRevisionId, outputProfile, executionMode);
            if (failLookup) {
                return RenderDeduplicationDecision.failedClosed(fp, "Mock lookup failure");
            }
            if (reuseResult != null) {
                return RenderDeduplicationDecision.reuse(fp, reuseResult, "Mock reuse");
            }
            return RenderDeduplicationDecision.proceed(fp,
                    RenderDeduplicationReason.NO_EXISTING_RENDER, "Mock proceed");
        }
    }

    static class MockLegacyService extends TimelineRevisionRenderService {
        boolean called = false;
        TimelineRevisionRenderService.RevisionRenderResult result;

        MockLegacyService() {
            super(null, null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public RevisionRenderResult render(String projectId, String revisionId, String outputProfile) {
            called = true;
            return result;
        }
    }

    static class MockPlanBasedService extends PlanBasedTimelineRevisionRenderService {
        boolean called = false;
        TimelineRevisionRenderService.RevisionRenderResult result;

        MockPlanBasedService() {
            super(null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null);
        }

        @Override
        public TimelineRevisionRenderService.RevisionRenderResult render(
                String projectId, String revisionId, String outputProfile) {
            called = true;
            return result;
        }
    }
}
