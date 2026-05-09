package com.example.platform.render.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.RenderPlan;
import com.example.platform.render.domain.RenderProfile;
import com.example.platform.render.domain.RenderStepStatus;
import com.example.platform.render.domain.RenderStepType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenderPlanServiceTest {

    private RenderPlanService service;

    @BeforeEach
    void setUp() {
        service = new RenderPlanService();
    }

    @Test
    void shouldCreateDefaultPlan() {
        RenderProfile profile = RenderProfile.social1080p();
        RenderPlan plan = service.createDefaultPlan("rj-1", profile);

        assertNotNull(plan);
        assertEquals("rj-1", plan.renderJobId());
        assertEquals(3, plan.steps().size());
        assertEquals(RenderStepType.BUILD_TIMELINE, plan.steps().get(0).type());
        assertEquals(RenderStepType.FFMPEG_TRANSCODE, plan.steps().get(1).type());
        assertEquals(RenderStepType.REGISTER_ARTIFACT, plan.steps().get(2).type());
    }

    @Test
    void shouldCreateCustomPlan() {
        RenderProfile profile = RenderProfile.social720p();
        List<RenderStepType> types = List.of(
                RenderStepType.BUILD_TIMELINE,
                RenderStepType.FFMPEG_PROBE,
                RenderStepType.FFMPEG_TRANSCODE,
                RenderStepType.QC_PROBE,
                RenderStepType.REGISTER_ARTIFACT
        );
        RenderPlan plan = service.createCustomPlan("rj-2", profile, types);

        assertNotNull(plan);
        assertEquals(5, plan.steps().size());
        assertEquals(RenderStepType.FFMPEG_PROBE, plan.steps().get(1).type());
        assertEquals(RenderStepType.QC_PROBE, plan.steps().get(3).type());
    }

    @Test
    void shouldCreateMltPlan() {
        RenderProfile profile = RenderProfile.social1080p();
        RenderPlan plan = service.createMltPlan("rj-3", profile);

        assertNotNull(plan);
        assertEquals(3, plan.steps().size());
        assertEquals(RenderStepType.MLT_RENDER_TIMELINE, plan.steps().get(1).type());
    }

    @Test
    void shouldCreatePackagingPlan() {
        RenderProfile profile = RenderProfile.social1080p();
        RenderPlan plan = service.createPackagingPlan("rj-4", profile, true, true);

        assertNotNull(plan);
        assertEquals(5, plan.steps().size());
        assertEquals(RenderStepType.BUILD_TIMELINE, plan.steps().get(0).type());
        assertEquals(RenderStepType.FFMPEG_TRANSCODE, plan.steps().get(1).type());
        assertEquals(RenderStepType.GPAC_PACKAGE_HLS, plan.steps().get(2).type());
        assertEquals(RenderStepType.GPAC_PACKAGE_DASH, plan.steps().get(3).type());
        assertEquals(RenderStepType.REGISTER_ARTIFACT, plan.steps().get(4).type());
    }

    @Test
    void shouldCreatePackagingPlanHlsOnly() {
        RenderProfile profile = RenderProfile.social1080p();
        RenderPlan plan = service.createPackagingPlan("rj-5", profile, true, false);

        assertNotNull(plan);
        assertEquals(4, plan.steps().size());
        assertEquals(RenderStepType.BUILD_TIMELINE, plan.steps().get(0).type());
        assertEquals(RenderStepType.FFMPEG_TRANSCODE, plan.steps().get(1).type());
        assertEquals(RenderStepType.GPAC_PACKAGE_HLS, plan.steps().get(2).type());
        assertEquals(RenderStepType.REGISTER_ARTIFACT, plan.steps().get(3).type());
    }

    @Test
    void shouldFindPlanById() {
        RenderProfile profile = RenderProfile.social1080p();
        RenderPlan plan = service.createDefaultPlan("rj-6", profile);

        assertTrue(service.findById(plan.id()).isPresent());
        assertEquals(plan.id(), service.findById(plan.id()).get().id());
    }

    @Test
    void shouldReturnEmptyForUnknownPlan() {
        assertTrue(service.findById("nonexistent").isEmpty());
    }

    @Test
    void shouldFindPlansByRenderJobId() {
        RenderProfile profile = RenderProfile.social1080p();
        service.createDefaultPlan("rj-7", profile);
        service.createDefaultPlan("rj-7", profile);
        service.createDefaultPlan("rj-8", profile);

        List<RenderPlan> plans = service.findByRenderJobId("rj-7");
        assertEquals(2, plans.size());
    }

    @Test
    void shouldSaveUpdatedPlan() {
        RenderProfile profile = RenderProfile.social1080p();
        RenderPlan plan = service.createDefaultPlan("rj-9", profile);

        var updatedStep = plan.steps().get(0).markRunning().markCompleted(List.of("art-1"));
        RenderPlan updated = plan.withStep(updatedStep);
        service.save(updated);

        RenderPlan retrieved = service.findById(plan.id()).get();
        assertEquals(RenderStepStatus.COMPLETED, retrieved.steps().get(0).status());
    }
}
