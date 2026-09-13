package com.example.platform.audit.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.artifact.api.event.ArtifactCreatedEvent;
import com.example.platform.render.api.event.RenderJobCompletedEvent;
import com.example.platform.render.api.event.RenderJobCreatedEvent;
import com.example.platform.render.api.event.RenderJobFailedEvent;
import com.example.platform.render.api.event.RenderJobStatusChangedEvent;
import com.example.platform.render.api.request.RenderInitiator;
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

    private static RenderInitiator initiator() {
        return RenderInitiator.from(com.example.platform.shared.authorization.CanonicalActor.user(
                "test-principal-p1", "tenant-1", java.util.Set.of(), "test"));
    }

    @Mock
    private AuditService auditService;

    private AuditEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AuditEventHandler(auditService);
    }

    @Test
    void onRenderJobCreated_usesCorrectActorId() {
        RenderJobCreatedEvent event = new RenderJobCreatedEvent("job-1", "proj-1", "snap-1", "profile-1", initiator(), java.time.Instant.now());

        handler.onRenderJobCreated(event);

        ArgumentCaptor<String> actorTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> actorIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).recordFact(anyString(), actorTypeCaptor.capture(), actorIdCaptor.capture(),
                actionCaptor.capture(), any(), any(), any(), any());

        assertEquals("SYSTEM", actorTypeCaptor.getValue());
        assertEquals("render-event-handler", actorIdCaptor.getValue(),
                "actorId should be 'render-event-handler', not projectId");
        assertEquals("RENDER_JOB_CREATED", actionCaptor.getValue());
    }

    @Test
    void onRenderJobCreated_resourceIdIsJobId() {
        RenderJobCreatedEvent event = new RenderJobCreatedEvent("job-1", "proj-1", "snap-1", "profile-1", initiator(), java.time.Instant.now());

        handler.onRenderJobCreated(event);

        ArgumentCaptor<String> resourceIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).recordFact(anyString(), any(), any(), any(), any(), resourceIdCaptor.capture(), any(), any());
        assertEquals("job-1", resourceIdCaptor.getValue(),
                "resourceId should be renderJobId");
    }

    @Test
    void onRenderJobCreated_payloadContainsProjectId() {
        RenderJobCreatedEvent event = new RenderJobCreatedEvent("job-1", "proj-1", "snap-1", "profile-1", initiator(), java.time.Instant.now());

        handler.onRenderJobCreated(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).recordFact(anyString(), any(), any(), any(), any(), any(), payloadCaptor.capture(), any());
        assertEquals("proj-1", payloadCaptor.getValue().get("projectId"),
                "projectId should be in payload, not actorId");
    }

    @Test
    void onRenderJobStatusChanged_usesCorrectActorId() {
        RenderJobStatusChangedEvent event = new RenderJobStatusChangedEvent("job-1", "proj-1", com.example.platform.render.domain.RenderJobStatus.QUEUED, com.example.platform.render.domain.RenderJobStatus.SELECTING_PROVIDER, Instant.now(), initiator());

        handler.onRenderJobStatusChanged(event);

        ArgumentCaptor<String> actorIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).recordFact(anyString(), any(), actorIdCaptor.capture(), any(), any(), any(), any(), any());
        assertEquals("render-event-handler", actorIdCaptor.getValue());
    }

    @Test
    void onRenderJobCompleted_usesCorrectActorId() {
        RenderJobCompletedEvent event = new RenderJobCompletedEvent(new com.example.platform.artifact.app.ArtifactOutputReference(new com.example.platform.artifact.app.ArtifactScope((initiator()).tenantId(),"proj-1","job-1"),new com.example.platform.shared.identity.ArtifactId("artifact-1")), Instant.now(), initiator());

        handler.onRenderJobCompleted(event);

        ArgumentCaptor<String> actorIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).recordFact(anyString(), any(), actorIdCaptor.capture(), any(), any(), any(), any(), any());
        assertEquals("render-event-handler", actorIdCaptor.getValue());
    }

    @Test
    void onRenderJobFailed_usesCorrectActorId() {
        RenderJobFailedEvent event = new RenderJobFailedEvent("job-1", "proj-1", com.example.platform.render.api.event.RenderFailureReason.EXECUTION_FAILED, Instant.now(), initiator(), com.example.platform.render.domain.RenderJobStatus.FAILED);

        handler.onRenderJobFailed(event);

        ArgumentCaptor<String> actorIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).recordFact(anyString(), any(), actorIdCaptor.capture(), any(), any(), any(), any(), any());
        assertEquals("render-event-handler", actorIdCaptor.getValue());
    }

    @Test
    void onArtifactCreated_usesCorrectActorId() {
        ArtifactCreatedEvent event = new ArtifactCreatedEvent(
                new com.example.platform.artifact.app.ArtifactOutputReference(new com.example.platform.artifact.app.ArtifactScope("tenant-1","proj-1","job-1"),new com.example.platform.shared.identity.ArtifactId("artifact-1")), Instant.now());

        handler.onArtifactCreated(event);

        ArgumentCaptor<String> actorIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).recordFact(eq(event.factKey()), any(), actorIdCaptor.capture(), any(), any(), any(), any(), any());
        assertEquals("artifact-event-handler", actorIdCaptor.getValue());
    }

    @Test
    void allEvents_useSYSTEMasActorType() {
        RenderJobCreatedEvent event = new RenderJobCreatedEvent("job-1", "proj-1", "snap-1", "profile-1", initiator(), java.time.Instant.now());

        handler.onRenderJobCreated(event);

        ArgumentCaptor<String> actorTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).recordFact(anyString(), actorTypeCaptor.capture(), any(), any(), any(), any(), any(), any());
        assertEquals("SYSTEM", actorTypeCaptor.getValue());
    }

    @Test
    void allEvents_useCategoryCONFIG() {
        RenderJobCreatedEvent event = new RenderJobCreatedEvent("job-1", "proj-1", "snap-1", "profile-1", initiator(), java.time.Instant.now());

        handler.onRenderJobCreated(event);

        ArgumentCaptor<AuditCategory> categoryCaptor = ArgumentCaptor.forClass(AuditCategory.class);
        verify(auditService).recordFact(anyString(), any(), any(), any(), any(), any(), any(), categoryCaptor.capture());
        assertEquals(AuditCategory.CONFIG, categoryCaptor.getValue());
    }

    @Test
    void onRenderJobCompleted_payloadContainsProjectId() {
        RenderJobCompletedEvent event = new RenderJobCompletedEvent(new com.example.platform.artifact.app.ArtifactOutputReference(new com.example.platform.artifact.app.ArtifactScope((initiator()).tenantId(),"proj-1","job-1"),new com.example.platform.shared.identity.ArtifactId("artifact-1")), Instant.now(), initiator());

        handler.onRenderJobCompleted(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).recordFact(anyString(), any(), any(), any(), any(), any(), payloadCaptor.capture(), any());
        assertEquals("proj-1", payloadCaptor.getValue().get("projectId"),
                "projectId should be in payload");
    }

    @Test
    void onRenderJobFailed_payloadContainsScopedNeutralReason() {
        RenderJobFailedEvent event = new RenderJobFailedEvent("job-1", "proj-1", com.example.platform.render.api.event.RenderFailureReason.EXECUTION_FAILED, Instant.now(), initiator(), com.example.platform.render.domain.RenderJobStatus.FAILED);

        handler.onRenderJobFailed(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).recordFact(anyString(), any(), any(), any(), any(), any(), payloadCaptor.capture(), any());
        assertEquals("proj-1", payloadCaptor.getValue().get("projectId"));
        assertEquals("Render execution failed", payloadCaptor.getValue().get("error"));
        assertEquals("EXECUTION_FAILED", payloadCaptor.getValue().get("reason"));
        assertEquals(initiator().tenantId(),payloadCaptor.getValue().get("tenantId"));
        assertFalse(payloadCaptor.getValue().toString().contains("FFmpeg"));
    }
}
