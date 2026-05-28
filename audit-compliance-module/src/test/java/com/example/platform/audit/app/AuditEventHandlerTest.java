package com.example.platform.audit.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.shared.events.ArtifactCreatedEvent;
import com.example.platform.shared.events.RenderJobCompletedEvent;
import com.example.platform.shared.events.RenderJobCreatedEvent;
import com.example.platform.shared.events.RenderJobFailedEvent;
import com.example.platform.shared.events.RenderJobStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class AuditEventHandlerTest {

    @Mock
    private AuditService auditService;

    private AuditEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AuditEventHandler(auditService);
    }

    @Test
    void onRenderJobCreated_usesCorrectActorId() {
        RenderJobCreatedEvent event = new RenderJobCreatedEvent(
                "job-1", "proj-1", "snap-1", "profile-1", "FFMPEG");

        handler.onRenderJobCreated(event);

        ArgumentCaptor<String> actorTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> actorIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(actorTypeCaptor.capture(), actorIdCaptor.capture(),
                actionCaptor.capture(), any(), any(), any(), any());

        assertEquals("SYSTEM", actorTypeCaptor.getValue());
        assertEquals("render-event-handler", actorIdCaptor.getValue(),
                "actorId should be 'render-event-handler', not projectId");
        assertEquals("RENDER_JOB_CREATED", actionCaptor.getValue());
    }

    @Test
    void onRenderJobCreated_resourceIdIsJobId() {
        RenderJobCreatedEvent event = new RenderJobCreatedEvent(
                "job-1", "proj-1", "snap-1", "profile-1", "FFMPEG");

        handler.onRenderJobCreated(event);

        ArgumentCaptor<String> resourceIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(any(), any(), any(), any(), resourceIdCaptor.capture(), any(), any());
        assertEquals("job-1", resourceIdCaptor.getValue(),
                "resourceId should be renderJobId");
    }

    @Test
    void onRenderJobCreated_payloadContainsProjectId() {
        RenderJobCreatedEvent event = new RenderJobCreatedEvent(
                "job-1", "proj-1", "snap-1", "profile-1", "FFMPEG");

        handler.onRenderJobCreated(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(any(), any(), any(), any(), any(), payloadCaptor.capture(), any());
        assertEquals("proj-1", payloadCaptor.getValue().get("projectId"),
                "projectId should be in payload, not actorId");
    }

    @Test
    void onRenderJobStatusChanged_usesCorrectActorId() {
        RenderJobStatusChangedEvent event = new RenderJobStatusChangedEvent(
                "job-1", "proj-1", "QUEUED", "RENDERING", Instant.now());

        handler.onRenderJobStatusChanged(event);

        ArgumentCaptor<String> actorIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(any(), actorIdCaptor.capture(), any(), any(), any(), any(), any());
        assertEquals("render-event-handler", actorIdCaptor.getValue());
    }

    @Test
    void onRenderJobCompleted_usesCorrectActorId() {
        RenderJobCompletedEvent event = new RenderJobCompletedEvent(
                "job-1", "proj-1", "artifact-1", "s3://bucket/key", Instant.now());

        handler.onRenderJobCompleted(event);

        ArgumentCaptor<String> actorIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(any(), actorIdCaptor.capture(), any(), any(), any(), any(), any());
        assertEquals("render-event-handler", actorIdCaptor.getValue());
    }

    @Test
    void onRenderJobFailed_usesCorrectActorId() {
        RenderJobFailedEvent event = new RenderJobFailedEvent(
                "job-1", "proj-1", "FFmpeg error", Instant.now());

        handler.onRenderJobFailed(event);

        ArgumentCaptor<String> actorIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(any(), actorIdCaptor.capture(), any(), any(), any(), any(), any());
        assertEquals("render-event-handler", actorIdCaptor.getValue());
    }

    @Test
    void onArtifactCreated_usesCorrectActorId() {
        ArtifactCreatedEvent event = new ArtifactCreatedEvent(
                "artifact-1", "job-1", "proj-1", "s3://bucket/key", Instant.now());

        handler.onArtifactCreated(event);

        ArgumentCaptor<String> actorIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(any(), actorIdCaptor.capture(), any(), any(), any(), any(), any());
        assertEquals("render-event-handler", actorIdCaptor.getValue());
    }

    @Test
    void allEvents_useSYSTEMasActorType() {
        RenderJobCreatedEvent event = new RenderJobCreatedEvent(
                "job-1", "proj-1", "snap-1", "profile-1", "FFMPEG");

        handler.onRenderJobCreated(event);

        ArgumentCaptor<String> actorTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(actorTypeCaptor.capture(), any(), any(), any(), any(), any(), any());
        assertEquals("SYSTEM", actorTypeCaptor.getValue());
    }

    @Test
    void allEvents_useCategoryCONFIG() {
        RenderJobCreatedEvent event = new RenderJobCreatedEvent(
                "job-1", "proj-1", "snap-1", "profile-1", "FFMPEG");

        handler.onRenderJobCreated(event);

        ArgumentCaptor<AuditCategory> categoryCaptor = ArgumentCaptor.forClass(AuditCategory.class);
        verify(auditService).record(any(), any(), any(), any(), any(), any(), categoryCaptor.capture());
        assertEquals(AuditCategory.CONFIG, categoryCaptor.getValue());
    }

    @Test
    void onRenderJobCompleted_payloadContainsProjectId() {
        RenderJobCompletedEvent event = new RenderJobCompletedEvent(
                "job-1", "proj-1", "artifact-1", "s3://bucket/key", Instant.now());

        handler.onRenderJobCompleted(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(any(), any(), any(), any(), any(), payloadCaptor.capture(), any());
        assertEquals("proj-1", payloadCaptor.getValue().get("projectId"),
                "projectId should be in payload");
    }

    @Test
    void onRenderJobFailed_payloadContainsProjectIdAndError() {
        RenderJobFailedEvent event = new RenderJobFailedEvent(
                "job-1", "proj-1", "FFmpeg timeout", Instant.now());

        handler.onRenderJobFailed(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(any(), any(), any(), any(), any(), payloadCaptor.capture(), any());
        assertEquals("proj-1", payloadCaptor.getValue().get("projectId"));
        assertEquals("FFmpeg timeout", payloadCaptor.getValue().get("error"));
    }
}
