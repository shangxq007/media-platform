package com.example.platform.web.artifact;

import com.example.platform.artifact.contract.*;
import com.example.platform.artifact.app.ArtifactSourceFactsQuery;
import com.example.platform.entitlement.api.commercial.*;
import com.example.platform.entitlement.app.QuotaUsageAuthority;
import com.example.platform.entitlement.app.QuotaPolicyService;
import com.example.platform.entitlement.domain.QuotaUsageResult;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.identity.api.dto.ProjectResponse;
import com.example.platform.identity.api.project.ProjectReadQuery;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.api.*;
import com.example.platform.storage.contract.*;
import com.example.platform.web.render.TimelineProjectAuthorizationService;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SourceMediaUploadControllerTest {
    @AfterEach void clear() { TenantContext.clear(); }

    @Test void scopeAndCommercialDecisionPrecedeAnyStorageWrite() {
        TenantContext.set("tenant-a");
        var authorization = mock(TimelineProjectAuthorizationService.class);
        var projects = mock(ProjectReadQuery.class);
        var actors = mock(CanonicalActorResolver.class);
        var commercial = mock(CommercialAdmissionPort.class);
        var quota = mock(QuotaUsageAuthority.class);
        var storage = mock(StorageSourceUploadPort.class);
        var artifacts = mock(ArtifactUploadAdmissionPort.class);
        var sourceFacts = mock(ArtifactSourceFactsQuery.class);
        when(authorization.requireWrite("tenant-a", "project-a")).thenReturn(
                CanonicalActor.user("actor-a", "tenant-a", Set.of(), "test"));
        when(actors.resolveCurrentActor()).thenReturn(Optional.of(
                CanonicalActor.user("actor-a", "tenant-a", Set.of(), "test")));
        when(projects.getProject("tenant-a", "project-a")).thenReturn(
                new ProjectResponse("project-a", "tenant-a", "p", "", "ACTIVE",
                        Instant.now(), "workspace-a"));
        when(commercial.decideForAcceptance(any())).thenReturn(new CommercialDecision(
                PrincipalRef.tenantScoped("tenant-a", PrincipalType.ORGANIZATION, "tenant-a"),
                "artifact.source.upload", false, CommercialDecisionReason.NOT_ENTITLED,
                java.util.List.of(), "v1", "decision-1", Instant.now()));
        var controller = new SourceMediaUploadController(authorization, projects, actors,
                commercial, quota, new QuotaPolicyService(), storage, artifacts, sourceFacts);
        byte[] media = new byte[]{0, 0, 0, 16, 102, 116, 121, 112, 105, 115, 111, 109, 0, 0, 0, 0};
        assertThrows(ResponseStatusException.class, () -> controller.upload(
                "tenant-a", "project-a", "upload-1",
                new MockMultipartFile("file", "source.mp4", "video/mp4", media)));
        verifyNoInteractions(quota, storage, artifacts);
    }

    @Test void successfulAdmissionReturnsOnlyLogicalPin() throws Exception {
        TenantContext.set("tenant-a");
        var authorization = mock(TimelineProjectAuthorizationService.class);
        var projects = mock(ProjectReadQuery.class);
        var actors = mock(CanonicalActorResolver.class);
        var commercial = mock(CommercialAdmissionPort.class);
        var quota = mock(QuotaUsageAuthority.class);
        var storage = mock(StorageSourceUploadPort.class);
        var artifacts = mock(ArtifactUploadAdmissionPort.class);
        var sourceFacts = mock(ArtifactSourceFactsQuery.class);
        var actor = CanonicalActor.user("actor-a", "tenant-a", Set.of(), "test");
        when(authorization.requireWrite("tenant-a", "project-a")).thenReturn(actor);
        when(actors.resolveCurrentActor()).thenReturn(Optional.of(actor));
        when(projects.getProject("tenant-a", "project-a")).thenReturn(
                new ProjectResponse("project-a", "tenant-a", "p", "", "ACTIVE",
                        Instant.now(), "workspace-a"));
        when(commercial.decideForAcceptance(any())).thenReturn(new CommercialDecision(
                PrincipalRef.tenantScoped("tenant-a", PrincipalType.ORGANIZATION, "tenant-a"),
                "artifact.source.upload", true, CommercialDecisionReason.ALLOWED,
                java.util.List.of(), "v1", "decision-1", Instant.now()));
        var quotaResult = mock(QuotaUsageResult.class);
        when(quotaResult.applied()).thenReturn(true);
        when(quotaResult.operationId()).thenReturn("quota-operation-1");
        when(quota.execute(any())).thenReturn(quotaResult);
        var issuance = mock(StorageObjectIssuance.IssuanceResult.class);
        var placement = mock(StorageObjectIssuance.BackendPlacementResult.class);
        var receipt = mock(StorageObjectIssuance.PlacementReceipt.class);
        var location = mock(com.example.platform.storage.contract.identity.StorageObjectLocation.class);
        when(issuance.objectId()).thenReturn(new StorageObjectId("object-1"));
        when(issuance.placement()).thenReturn(placement);
        when(issuance.receipt()).thenReturn(receipt);
        when(placement.replicaId()).thenReturn(new StorageReplicaId("replica-1"));
        when(placement.location()).thenReturn(location);
        when(location.providerId()).thenReturn(new StorageProviderId("provider-1"));
        when(receipt.receiptId()).thenReturn("receipt-1");
        when(placement.committedDigest()).thenReturn(ContentDigest.sha256("a".repeat(64)));
        when(placement.committedLength()).thenReturn(16L);
        when(storage.writeSource(any(), any(), any(), eq("video/mp4"))).thenReturn(
                new StorageOutputPort.WrittenOutput(issuance, mock(StorageReference.class)));
        when(artifacts.admit(any())).thenReturn(new ArtifactUploadAdmissionPort.AdmissionResult(
                new ArtifactId("artifact-1"), "fingerprint-1"));
        when(sourceFacts.find(any(), any(), any(), any())).thenReturn(Optional.of(new ArtifactSourceFacts(
                com.example.platform.shared.time.MediaTime.ofMillis(1000),
                java.util.List.of(new ArtifactSourceFacts.Stream("artifact-1:s0", ArtifactSourceFacts.Kind.VIDEO,
                        com.example.platform.shared.time.MediaTime.ofMillis(1000))))));
        var controller = new SourceMediaUploadController(authorization, projects, actors,
                commercial, quota, new QuotaPolicyService(), storage, artifacts, sourceFacts);
        byte[] media = new byte[]{0, 0, 0, 16, 102, 116, 121, 112, 105, 115, 111, 109, 0, 0, 0, 0};
        var result = controller.upload("tenant-a", "project-a", "upload-1",
                new MockMultipartFile("file", "source.mp4", "video/mp4", media));
        assertEquals("artifact-1", result.artifactId());
        assertEquals("a".repeat(64), result.contentDigest());
        var captured = ArgumentCaptor.forClass(ArtifactUploadAdmission.class);
        verify(artifacts).admit(captured.capture());
        assertEquals("project-a", captured.getValue().scope().projectId());
        assertEquals("workspace-a", captured.getValue().scope().workspaceId());
        assertEquals("quota-operation-1", captured.getValue().quotaDecisionId());
    }
}
