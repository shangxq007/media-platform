package com.example.platform.web.artifact;

import com.example.platform.artifact.contract.*;
import com.example.platform.artifact.app.ArtifactSourceFactsQuery;
import com.example.platform.artifact.app.ArtifactSourceCatalog;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SourceMediaUploadControllerTest {
    private static PlatformTransactionManager transactions() {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return manager;
    }
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
        var sourceCatalog = mock(ArtifactSourceCatalog.class);
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
                commercial, quota, new QuotaPolicyService(), storage, artifacts, sourceFacts, sourceCatalog, transactions());
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
        var sourceCatalog = mock(ArtifactSourceCatalog.class);
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
        when(storage.writeSource(any(), any(), any(), eq(16L), eq("video/mp4"))).thenReturn(
                new StorageOutputPort.WrittenOutput(issuance, mock(StorageReference.class)));
        when(artifacts.prepare(any())).thenReturn(mock(ArtifactUploadAdmissionPort.PreparedSource.class));
        when(artifacts.admitPrepared(any(), any())).thenReturn(new ArtifactUploadAdmissionPort.AdmissionResult(
                new ArtifactId("artifact-1"), "fingerprint-1"));
        when(sourceFacts.find(any(), any(), any(), any())).thenReturn(Optional.of(new ArtifactSourceFacts(
                com.example.platform.shared.time.MediaTime.ofMillis(1000),
                java.util.List.of(new ArtifactSourceFacts.Stream("artifact-1:s0", ArtifactSourceFacts.Kind.VIDEO,
                        com.example.platform.shared.time.MediaTime.ofMillis(1000))))));
        var controller = new SourceMediaUploadController(authorization, projects, actors,
                commercial, quota, new QuotaPolicyService(), storage, artifacts, sourceFacts, sourceCatalog, transactions());
        byte[] media = new byte[]{0, 0, 0, 16, 102, 116, 121, 112, 105, 115, 111, 109, 0, 0, 0, 0};
        var result = controller.upload("tenant-a", "project-a", "upload-1",
                new MockMultipartFile("file", "source.mp4", "video/mp4", media));
        assertEquals("artifact-1", result.artifactId());
        assertEquals("artifact-1", result.mediaAssetId());
        assertEquals("source.mp4", result.name());
        assertEquals("a".repeat(64), result.contentDigest());
        verify(sourceCatalog).recordDisplayName(eq("tenant-a"), eq("project-a"), eq(new ArtifactId("artifact-1")),
                eq(ContentDigest.sha256("a".repeat(64))), eq("source.mp4"));
        var captured = ArgumentCaptor.forClass(ArtifactUploadAdmission.class);
        verify(artifacts).admitPrepared(any(), captured.capture());
        assertEquals("project-a", captured.getValue().scope().projectId());
        assertEquals("workspace-a", captured.getValue().scope().workspaceId());
        assertEquals("quota-operation-1", captured.getValue().quotaDecisionId());
    }

    @Test void sourceBrowserRequiresProjectReadAndReturnsOnlyScopedLogicalPins() {
        var authorization = mock(TimelineProjectAuthorizationService.class);
        var sourceFacts = mock(ArtifactSourceFactsQuery.class);
        var sourceCatalog = mock(ArtifactSourceCatalog.class);
        var controller = new SourceMediaUploadController(authorization, mock(ProjectReadQuery.class),
                mock(CanonicalActorResolver.class), mock(CommercialAdmissionPort.class),
                mock(QuotaUsageAuthority.class), new QuotaPolicyService(),
                mock(StorageSourceUploadPort.class), mock(ArtifactUploadAdmissionPort.class), sourceFacts, sourceCatalog, transactions());
        var facts = new ArtifactSourceFacts(com.example.platform.shared.time.MediaTime.ofMillis(1000),
                java.util.List.of(new ArtifactSourceFacts.Stream("stream-1", ArtifactSourceFacts.Kind.VIDEO,
                        com.example.platform.shared.time.MediaTime.ofMillis(1000))));
        when(sourceCatalog.list("tenant-a", "project-a", 25)).thenReturn(java.util.List.of(
                new ArtifactSourceCatalog.SourceEntry(new ArtifactId("artifact-1"),
                        ContentDigest.sha256("a".repeat(64)), 100L, "source.mp4", facts)));
        var rows = controller.list("tenant-a", "project-a", 25);
        verify(authorization).requireRead("tenant-a", "project-a");
        assertEquals(1, rows.size());
        assertEquals("source.mp4", rows.getFirst().name());
        assertEquals("artifact-1", rows.getFirst().mediaAssetId());
        assertEquals("stream-1", rows.getFirst().streams().getFirst().streamId());
    }

    @Test void invalidClientFileNameIsRejectedBeforeQuotaOrStorageSideEffects() {
        var authorization = mock(TimelineProjectAuthorizationService.class);
        var projects = mock(ProjectReadQuery.class);
        var actors = mock(CanonicalActorResolver.class);
        var quota = mock(QuotaUsageAuthority.class);
        var storage = mock(StorageSourceUploadPort.class);
        var artifacts = mock(ArtifactUploadAdmissionPort.class);
        when(projects.getProject("tenant-a", "project-a")).thenReturn(
                new ProjectResponse("project-a", "tenant-a", "p", "", "ACTIVE",
                        Instant.now(), "workspace-a"));
        when(actors.resolveCurrentActor()).thenReturn(Optional.of(
                CanonicalActor.user("actor-a", "tenant-a", Set.of(), "test")));
        var controller = new SourceMediaUploadController(authorization, projects, actors,
                mock(CommercialAdmissionPort.class), quota, new QuotaPolicyService(), storage,
                artifacts, mock(ArtifactSourceFactsQuery.class), mock(ArtifactSourceCatalog.class), transactions());
        var invalid = new MockMultipartFile("file", "invalid\nname.mp4", "video/mp4", new byte[]{1});

        assertThrows(ResponseStatusException.class,
                () -> controller.upload("tenant-a", "project-a", "upload-1", invalid));
        verifyNoInteractions(quota, storage, artifacts);
    }
}
