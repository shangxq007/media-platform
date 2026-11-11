package com.example.platform.render.app;

import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.render.api.event.RenderJobCreatedEvent;
import com.example.platform.render.app.event.RenderLifecyclePublisher;
import com.example.platform.shared.authorization.ActorType;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.shared.web.PlatformException;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The remaining creation boundary supplies provenance to formal graph preparation, not a local executor. */
class RenderJobServiceTest {
    final RenderJobRepository jobs=mock(RenderJobRepository.class);
    final RenderLifecyclePublisher events=mock(RenderLifecyclePublisher.class);
    final RenderJobStatusHistoryRepository history=mock(RenderJobStatusHistoryRepository.class);
    final RenderAcceptanceContextService acceptance=mock(RenderAcceptanceContextService.class);
    final RenderJobService service=new RenderJobService(jobs,events,history,null,
        mock(com.example.platform.identity.api.project.ProjectReadQuery.class),
        mock(com.example.platform.identity.api.authorization.CanonicalActorResolver.class),
        mock(com.example.platform.identity.api.authorization.AuthorizationDecisionPort.class),acceptance);
    final RenderInitiator actor=RenderInitiator.restore(ActorType.USER,"editor","tenant");
    final CreateRenderJobRequest request=new CreateRenderJobRequest("project","snapshot","default","workspace","TENANT_ORGANIZATION");
    @BeforeEach void tenant(){TenantContext.set("tenant");}
    @AfterEach void clear(){TenantContext.clear();}

    @Test void graphPreparationPreservesOwnerRequestAndImmutableInitiator() {
        var result=service.createForGraphPreparation("tenant","project",request,actor);
        assertTrue(result.id().startsWith("rj_"));assertEquals("QUEUED",result.status());assertEquals("project",result.projectId());
        verify(acceptance).prepare("tenant","project",actor,"workspace","TENANT_ORGANIZATION");
        verify(jobs).create(eq(result.id()),eq("project"),eq("tenant"),eq("snapshot"),eq("default"),eq("QUEUED"),eq(actor),any(OffsetDateTime.class));
        var order=inOrder(jobs,acceptance,history,events);
        order.verify(jobs).create(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),eq(actor),any());
        order.verify(acceptance).persist(eq(result.id()),eq("snapshot"),isNull());
        order.verify(history).record(result.id(),null,"QUEUED","Job created",null);
        order.verify(events).publishEvent(any(RenderJobCreatedEvent.class));
        order.verify(jobs).markGraphPreparation("tenant",result.id());
    }
    @Test void failedAcceptanceNeverCreatesAJobOrPublishesAnEvent() {
        when(acceptance.prepare(any(),any(),any(),any(),any())).thenThrow(new SecurityException("denied"));
        assertThrows(SecurityException.class,()->service.createForGraphPreparation("tenant","project",request,actor));
        verifyNoInteractions(jobs,events,history);
    }
    @Test void initiatorAndTenantMismatchRejectsBeforeAcceptanceOrPersistence() {
        assertThrows(IllegalArgumentException.class,()->service.createForGraphPreparation("other","project",request,actor));
        verifyNoInteractions(acceptance,jobs,events,history);
    }
    @Test void projectHintCannotReplaceOwnerScope() {
        assertThrows(PlatformException.class,()->service.createForGraphPreparation("tenant","other-project",request,actor));
        verifyNoInteractions(acceptance,jobs,events,history);
    }
    @Test void missingInitiatorCannotAcquireSystemProvenance() {
        assertThrows(NullPointerException.class,()->service.createForGraphPreparation("tenant","project",request,null));
        verifyNoInteractions(acceptance,jobs,events,history);
    }
    @Test void creationFactCarriesThePersistedProvenance() {
        var result=service.createForGraphPreparation("tenant","project",request,actor);
        var captured=ArgumentCaptor.forClass(RenderJobCreatedEvent.class);verify(events).publishEvent(captured.capture());
        assertEquals(result.id(),captured.getValue().renderJobId());assertEquals(actor,captured.getValue().initiator());
    }
}
