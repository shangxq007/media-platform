package com.example.platform.artifact.contract;

import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactContractFoundationTest {
    private static final ArtifactScope SCOPE = new ArtifactScope("tenant-a", "workspace-a");
    private static final ContentDigest DIGEST = ContentDigest.sha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

    @Test
    void uploadContractRequiresCompleteImmutableFactsAndHasStableFingerprint() {
        var upload = new ArtifactUploadAdmission(1, SCOPE,
                new ArtifactStorageReference(new StorageObjectId("object-1"), new StorageReplicaId("replica-1"), new StorageProviderId("provider-1"), "issuance-1"),
                DIGEST, 42, new ArtifactTechnicalMetadata(ArtifactKind.SOURCE_MEDIA, ArtifactMediaType.VIDEO, Map.of("codec", "h264")),
                new ArtifactAuditFacts("actor", "request", Instant.parse("2026-01-01T00:00:00Z")),
                new ArtifactLifecycleFacts(ArtifactState.AVAILABLE, Instant.parse("2026-01-01T00:00:00Z")),
                new ArtifactLineageFacts(List.of(), "upload", 1), "idem-1", "quota-1");
        assertEquals(ArtifactContractFingerprint.upload(upload), ArtifactContractFingerprint.upload(upload));
        assertThrows(ArtifactContractException.class, () -> new ArtifactUploadAdmission(1, SCOPE, upload.storage(), DIGEST, 42, upload.technicalMetadata(), upload.audit(), upload.lifecycle(), upload.lineage(), "", "quota-1"));
    }

    @Test
    void canonicalJsonSortsNestedObjectKeysPreservesArraysAndNulls() throws Exception {
        var mapper = new ObjectMapper();
        var a = mapper.readTree("{\"b\":{\"z\":null,\"a\":1},\"a\":[{\"y\":2,\"x\":1},null]}");
        var b = mapper.readTree("{\"a\":[{\"x\":1,\"y\":2},null],\"b\":{\"a\":1,\"z\":null}}");
        assertEquals(ArtifactContractCanonicalizer.canonicalJson(a), ArtifactContractCanonicalizer.canonicalJson(b));
        assertEquals(ArtifactContractCanonicalizer.fingerprint(a), ArtifactContractCanonicalizer.fingerprint(b));
    }

    @Test
    void subjectProjectionAndSourceReferenceAreScopedAndVersioned() {
        var id = new ArtifactId("artifact-1");
        var subject = new ArtifactSubject(1, id, SCOPE, ArtifactSubject.Visibility.WORKSPACE, "owner-1", "lineage-1", "life-1");
        var source = new ArtifactSourceReference(1, id, DIGEST, SCOPE, "lineage-1", 1);
        assertNotNull(ArtifactContractFingerprint.subject(subject));
        assertNotNull(ArtifactContractFingerprint.source(source));
        var otherScope = new ArtifactSourceReference(1, id, DIGEST, new ArtifactScope("tenant-a", "workspace-b"), "lineage-1", 1);
        assertNotEquals(ArtifactContractFingerprint.source(source), ArtifactContractFingerprint.source(otherScope));
    }

    @Test
    void projectionFailsClosedOnMissingLineageAndSearchRejectsInvalidCursor() {
        assertThrows(ArtifactContractException.class, () -> new ArtifactProjection(1, new ArtifactId("a"), SCOPE, ArtifactKind.SOURCE_MEDIA, DIGEST, 1, ArtifactState.AVAILABLE, "", Instant.now(), 1));
        assertThrows(ArtifactContractException.class, () -> new ArtifactSearchQuery(SCOPE, null, null, 0, null));
    }

    @Test
    void sharedValidatorRejectsScopeDigestLifecycleStalenessAndDuplicateLinks() {
        assertThrows(ArtifactContractException.class, () -> ArtifactContractValidator.requireScope(SCOPE, new ArtifactScope("tenant-a", "workspace-b")));
        assertThrows(ArtifactContractException.class, () -> ArtifactContractValidator.requireDigest(DIGEST, ContentDigest.sha256("abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd")));
        assertThrows(ArtifactContractException.class, () -> ArtifactContractValidator.requireRetrievable(ArtifactState.QUARANTINED));
        assertThrows(ArtifactContractException.class, () -> ArtifactContractValidator.requireFresh(ArtifactSearchPage.Consistency.STALE));
        assertThrows(ArtifactContractException.class, () -> ArtifactContractValidator.rejectDuplicateArtifactLinks(List.of(new ArtifactId("a"), new ArtifactId("a"))));
    }
}
