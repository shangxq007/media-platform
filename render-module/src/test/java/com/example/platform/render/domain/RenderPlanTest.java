package com.example.platform.render.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class RenderPlanTest {

    @Test
    void shouldCreatePlanWithSteps() {
        RenderProfile profile = RenderProfile.social1080p();
        List<RenderStep> steps = List.of(
                RenderStep.pending("rs-1", "rp-1", RenderStepType.BUILD_TIMELINE),
                RenderStep.pending("rs-2", "rp-1", RenderStepType.FFMPEG_TRANSCODE),
                RenderStep.pending("rs-3", "rp-1", RenderStepType.REGISTER_ARTIFACT)
        );
        RenderPlan plan = RenderPlan.create("rp-1", "rj-1", profile, steps);

        assertEquals("rp-1", plan.id());
        assertEquals("rj-1", plan.renderJobId());
        assertEquals(profile, plan.profile());
        assertEquals(3, plan.steps().size());
        assertEquals(RenderStepStatus.PENDING, plan.status());
        assertNotNull(plan.createdAt());
    }

    @Test
    void shouldFindNextPendingStep() {
        RenderProfile profile = RenderProfile.social1080p();
        List<RenderStep> steps = List.of(
                RenderStep.pending("rs-1", "rp-1", RenderStepType.BUILD_TIMELINE),
                RenderStep.pending("rs-2", "rp-1", RenderStepType.FFMPEG_TRANSCODE)
        );
        RenderPlan plan = RenderPlan.create("rp-1", "rj-1", profile, steps);

        RenderStep next = plan.nextPendingStep();
        assertNotNull(next);
        assertEquals("rs-1", next.id());
        assertEquals(RenderStepType.BUILD_TIMELINE, next.type());
    }

    @Test
    void shouldReturnNullWhenNoPendingStep() {
        RenderProfile profile = RenderProfile.social1080p();
        RenderStep completed = RenderStep.pending("rs-1", "rp-1", RenderStepType.BUILD_TIMELINE)
                .markRunning()
                .markCompleted(List.of("art-1"));
        RenderPlan plan = RenderPlan.create("rp-1", "rj-1", profile, List.of(completed));

        assertNull(plan.nextPendingStep());
    }

    @Test
    void shouldDetectCompletion() {
        RenderProfile profile = RenderProfile.social1080p();
        RenderStep s1 = RenderStep.pending("rs-1", "rp-1", RenderStepType.BUILD_TIMELINE)
                .markRunning().markCompleted(List.of("art-1"));
        RenderStep s2 = RenderStep.pending("rs-2", "rp-1", RenderStepType.FFMPEG_TRANSCODE)
                .markRunning().markCompleted(List.of("art-2"));
        RenderStep s3 = RenderStep.pending("rs-3", "rp-1", RenderStepType.REGISTER_ARTIFACT)
                .markRunning().markCompleted(List.of("art-3"));
        RenderPlan plan = RenderPlan.create("rp-1", "rj-1", profile, List.of(s1, s2, s3));

        assertTrue(plan.isComplete());
        assertTrue(plan.isDone());
        assertEquals(RenderStepStatus.COMPLETED, plan.status());
    }

    @Test
    void shouldDetectFailure() {
        RenderProfile profile = RenderProfile.social1080p();
        RenderStep s1 = RenderStep.pending("rs-1", "rp-1", RenderStepType.BUILD_TIMELINE)
                .markRunning().markCompleted(List.of("art-1"));
        RenderStep s2 = RenderStep.pending("rs-2", "rp-1", RenderStepType.FFMPEG_TRANSCODE)
                .markRunning().markFailed("TRANSCODE_FAILED", "Error");
        RenderPlan plan = RenderPlan.create("rp-1", "rj-1", profile, List.of(s1, s2));

        assertTrue(plan.hasFailed());
        assertTrue(plan.isDone());
        assertEquals(RenderStepStatus.FAILED, plan.status());
    }

    @Test
    void shouldUpdateStepInPlan() {
        RenderProfile profile = RenderProfile.social1080p();
        RenderStep s1 = RenderStep.pending("rs-1", "rp-1", RenderStepType.BUILD_TIMELINE);
        RenderStep s2 = RenderStep.pending("rs-2", "rp-1", RenderStepType.FFMPEG_TRANSCODE);
        RenderPlan plan = RenderPlan.create("rp-1", "rj-1", profile, List.of(s1, s2));

        RenderStep updatedS1 = s1.markRunning();
        RenderPlan updated = plan.withStep(updatedS1);

        assertEquals(RenderStepStatus.RUNNING, updated.steps().get(0).status());
        assertEquals(RenderStepStatus.PENDING, updated.steps().get(1).status());
        // Plan status should be RUNNING since one step is running
        assertEquals(RenderStepStatus.RUNNING, updated.status());
    }

    @Test
    void shouldAssociatePlanWithRenderJob() {
        RenderProfile profile = RenderProfile.social720p();
        List<RenderStep> steps = List.of(
                RenderStep.pending("rs-1", "rp-1", RenderStepType.BUILD_TIMELINE)
        );
        RenderPlan plan = RenderPlan.create("rp-1", "rj-42", profile, steps);

        assertEquals("rj-42", plan.renderJobId());
    }
}
