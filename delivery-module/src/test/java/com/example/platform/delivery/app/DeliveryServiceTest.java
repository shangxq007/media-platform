package com.example.platform.delivery.app;

import com.example.platform.artifact.app.*;
import com.example.platform.artifact.domain.*;
import com.example.platform.delivery.infrastructure.DeliveryRepository;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.render.api.GraphRenderLifecycle;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.app.timeline.RenderJobRevisionPinningService;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.TenantContext;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryServiceTest {
    DeliveryAccess access=mock(DeliveryAccess.class);
    ArtifactProjectAuthorizationPort artifactAccess=mock(ArtifactProjectAuthorizationPort.class);
    RenderJobService jobs=mock(RenderJobService.class);
    GraphRenderLifecycle graph=mock(GraphRenderLifecycle.class);
    RenderJobRevisionPinningService revisions=mock(RenderJobRevisionPinningService.class);
    ArtifactApplicationQuery artifacts=mock(ArtifactApplicationQuery.class);
    ArtifactOutputRead output=mock(ArtifactOutputRead.class);
    DeliveryRepository repository=mock(DeliveryRepository.class);
    DeliveryService service;
    ArtifactOutputReference reference=new ArtifactOutputReference(new ArtifactScope("tenant","project","job"),new ArtifactId("artifact"));
    DeliveryRecord record=new DeliveryRecord("delivery","tenant","project","job","artifact","key",DeliveryService.requestHash("job","artifact"),"user",OffsetDateTime.now());
    @BeforeEach void setup() {
        TenantContext.set("tenant");var tx=mock(PlatformTransactionManager.class);when(tx.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        CanonicalActorResolver actors=()->Optional.of(CanonicalActor.user("user","tenant",Set.of(),"test"));
        service=new DeliveryService(access,artifactAccess,actors,jobs,graph,revisions,artifacts,output,repository,tx);
        when(jobs.getByIdAndProject("tenant","project","job")).thenReturn(new RenderJobResponse("job","project","snapshot","default","COMPLETED"));
        when(graph.find("tenant","project","job")).thenReturn(Optional.of(new GraphRenderLifecycle.Status(null,"COMPLETED","artifact",null,null)));
        var artifact=mock(Artifact.class);when(artifact.state()).thenReturn(ArtifactState.AVAILABLE);when(artifact.artifactKind()).thenReturn(ArtifactKind.RENDER_MASTER);
        when(artifacts.findArtifact(reference.scope(),reference.artifactId())).thenReturn(Optional.of(artifact));when(output.find(reference.scope())).thenReturn(Optional.of(reference));
        when(output.read(reference)).thenReturn(new ArtifactOutputRead.Content(new byte[]{1},ArtifactMediaType.VIDEO,"video/mp4","output.mp4"));
        when(repository.byKey("tenant","project","key")).thenReturn(Optional.empty());when(repository.insert(any())).thenAnswer(call->{
            DeliveryRecord saved=call.getArgument(0);
            when(repository.find(saved.tenantId(),saved.projectId(),saved.id())).thenReturn(Optional.of(saved));return true;
        });
        when(repository.find("tenant","project","delivery")).thenReturn(Optional.of(record));
    }
    @AfterEach void clear(){TenantContext.clear();}
    @Test void createsIndependentImmutableSelectionThroughOwners() {
        var result=service.create("project","job",null,"key");assertTrue(result.created());assertNotEquals("job",result.delivery().deliveryId());
        assertEquals("artifact",result.delivery().artifactId());assertEquals("READY",result.delivery().status());verify(output).read(reference);
    }
    @Test void explicitAndImplicitAcceptedArtifactNormalizeIdentically() {
        when(repository.byKey("tenant","project","key")).thenReturn(Optional.of(record));
        assertEquals("delivery",service.create("project","job","artifact","key").delivery().deliveryId());verify(repository,never()).insert(any());
    }
    @Test void replayCannotReplaceSelectedResult() {
        var old=new DeliveryRecord("delivery","tenant","project","other","other-artifact","key","different","user",OffsetDateTime.now());
        when(repository.byKey("tenant","project","key")).thenReturn(Optional.of(old));
        assertEquals("IDEMPOTENCY_CONFLICT",assertThrows(DeliveryFailure.class,()->service.create("project","job",null,"key")).code());verify(repository,never()).insert(any());
    }
    @Test void denialPrecedesIdempotencyDiscovery() {
        doThrow(new SecurityException()).when(access).require("tenant","project",true);
        assertThrows(SecurityException.class,()->service.create("project","job",null,"key"));verifyNoInteractions(repository,output,graph);
    }
    @Test void arbitraryArtifactCannotBypassAcceptedJobOutput() {
        assertEquals("ARTIFACT_NOT_ACCEPTED_RESULT",assertThrows(DeliveryFailure.class,()->service.create("project","job","guessed","key")).code());verifyNoInteractions(repository);verify(output,never()).read(any());
    }
    @Test void unfinishedOrCancelledJobHasNoDelivery() {
        for(String state:List.of("RUNNING","QUEUED","CANCELLED","FAILED")) {
            when(jobs.getByIdAndProject("tenant","project","job")).thenReturn(new RenderJobResponse("job","project","snapshot","default",state));
            assertThrows(DeliveryFailure.class,()->service.create("project","job",null,"key"));
        }verifyNoInteractions(repository);
    }
    @Test void readyRecordCannotBypassArtifactRevocation() {
        var unavailable=mock(Artifact.class);when(unavailable.state()).thenReturn(ArtifactState.QUARANTINED);
        when(artifacts.findArtifact(reference.scope(),reference.artifactId())).thenReturn(Optional.of(unavailable));
        assertEquals("ARTIFACT_UNAVAILABLE",assertThrows(DeliveryFailure.class,()->service.result("project","delivery")).code());verify(output,never()).read(any());
    }
    @Test void contentIntegrityFailureCannotCreateReadyRecord() {
        when(output.read(reference)).thenThrow(new IllegalStateException("storage private details"));
        var error=assertThrows(DeliveryFailure.class,()->service.create("project","job",null,"key"));assertEquals("ARTIFACT_UNAVAILABLE",error.getMessage());verifyNoInteractions(repository);
    }
    @Test void resultRechecksPermissionsAfterContentRead() {
        doNothing().doThrow(new SecurityException()).when(access).require("tenant","project",false);
        assertThrows(SecurityException.class,()->service.result("project","delivery"));verify(output).read(reference);
    }
}
