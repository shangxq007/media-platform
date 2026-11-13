package com.example.platform.config.firstrender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.platform.render.api.GraphRenderLifecycle;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.app.timeline.RenderJobRevisionPinningService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RenderJobLifecycleControllerTest {
    @Test void formalLifecycleRequiresProjectScopedJobReadBeforeReturningPinnedRevision() {
        var jobs = mock(RenderJobService.class);
        var lifecycle = mock(GraphRenderLifecycle.class);
        var pinning = mock(RenderJobRevisionPinningService.class);
        when(jobs.getByIdAndProject("tenant", "project", "job"))
                .thenReturn(new RenderJobResponse("job", "project", "snapshot", "default", "COMPLETED"));
        when(pinning.getPinnedRevisionId("job")).thenReturn("revision");
        when(lifecycle.find("tenant", "project", "job")).thenReturn(Optional.empty());

        var response = new RenderJobLifecycleController(jobs, lifecycle, pinning)
                .scopedLifecycle("tenant", "project", "job");

        assertEquals("revision", response.timelineRevisionId());
        assertEquals("project", response.projectId());
        assertEquals("COMPLETED", response.status());
        verify(jobs).getByIdAndProject("tenant", "project", "job");
        verify(jobs, never()).getById("job");
    }

    @Test void formalHttpLifecycleHasScopedRoute() throws Exception {
        var jobs = mock(RenderJobService.class);
        var lifecycle = mock(GraphRenderLifecycle.class);
        var pinning = mock(RenderJobRevisionPinningService.class);
        when(jobs.getByIdAndProject("tenant", "project", "job"))
                .thenReturn(new RenderJobResponse("job", "project", "snapshot", "default", "COMPLETED"));
        when(pinning.getPinnedRevisionId("job")).thenReturn("revision");
        when(lifecycle.find("tenant", "project", "job")).thenReturn(Optional.empty());

        MockMvcBuilders.standaloneSetup(new RenderJobLifecycleController(jobs, lifecycle, pinning)).build()
                .perform(get("/api/tenants/tenant/projects/project/render-jobs/job/lifecycle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.renderJobId").value("job"))
                .andExpect(jsonPath("$.timelineRevisionId").value("revision"));
    }
}
