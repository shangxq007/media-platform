package com.example.platform.render.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.app.timeline.BaseJobTimelineLoader;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class RenderJobTimelineQueryServiceTest {

    private RenderJobRepository renderJobRepository;
    private BaseJobTimelineLoader baseJobTimelineLoader;
    private RenderJobTimelineQueryService service;

    @BeforeEach
    void setUp() {
        renderJobRepository = mock(RenderJobRepository.class);
        baseJobTimelineLoader = mock(BaseJobTimelineLoader.class);
        service = new RenderJobTimelineQueryService(
                renderJobRepository, baseJobTimelineLoader, null);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void returnsBaseTimelineWhenAvailable() {
        TenantContext.set("tenant-1");
        when(baseJobTimelineLoader.loadInternalTimelineJson("rj-1", "tenant-1"))
                .thenReturn(Optional.of("{\"tracks\":[]}"));

        String result = service.loadJobTimelineJson("tenant-1", "rj-1");

        assertEquals("{\"tracks\":[]}", result);
        verifyNoInteractions(renderJobRepository);
    }

    @Test
    void fallsBackToAiScriptWhenBaseTimelineEmpty() {
        TenantContext.set("tenant-2");
        when(baseJobTimelineLoader.loadInternalTimelineJson("rj-2", "tenant-2"))
                .thenReturn(Optional.empty());
        when(renderJobRepository.findAiScriptById("rj-2"))
                .thenReturn(Optional.of("{\"tracks\":[{\"type\":\"VIDEO\"}]}"));

        String result = service.loadJobTimelineJson("tenant-2", "rj-2");

        assertEquals("{\"tracks\":[{\"type\":\"VIDEO\"}]}", result);
    }

    @Test
    void returnsEmptyStringWhenNothingAvailable() {
        TenantContext.set("tenant-3");
        when(baseJobTimelineLoader.loadInternalTimelineJson("rj-3", "tenant-3"))
                .thenReturn(Optional.empty());
        when(renderJobRepository.findAiScriptById("rj-3"))
                .thenReturn(Optional.empty());

        String result = service.loadJobTimelineJson("tenant-3", "rj-3");

        assertEquals("", result);
    }

    @Test
    void rejectsCrossTenantAccess() {
        TenantContext.set("tenant-a");

        assertThrows(IllegalArgumentException.class,
                () -> service.loadJobTimelineJson("tenant-b", "rj-4"));
    }

    @Test
    void doesNotCallRepositoryWhenBaseTimelineFound() {
        TenantContext.set("tenant-5");
        when(baseJobTimelineLoader.loadInternalTimelineJson("rj-5", "tenant-5"))
                .thenReturn(Optional.of("{\"tracks\":[]}"));

        service.loadJobTimelineJson("tenant-5", "rj-5");

        verify(renderJobRepository, never()).findAiScriptById(anyString());
    }
}
