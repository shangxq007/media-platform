package com.example.platform.artifact.contract;

import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Shared fail-closed checks used by future feature adapters. */
public final class ArtifactContractValidator {
    private ArtifactContractValidator() {}

    public static void requireScope(ArtifactScope expected, ArtifactScope actual) {
        if (!Objects.equals(expected, actual)) throw new ArtifactContractException(ArtifactContractErrorCode.SCOPE_MISMATCH, "tenant/workspace scope mismatch");
    }

    public static void requireDigest(ContentDigest expected, ContentDigest actual) {
        if (expected == null || actual == null || !expected.matches(actual)) throw new ArtifactContractException(ArtifactContractErrorCode.INTEGRITY_VERIFICATION_FAILED, "digest verification failed");
    }

    public static void requireRetrievable(ArtifactState state) {
        if (state != ArtifactState.AVAILABLE) throw new ArtifactContractException(ArtifactContractErrorCode.ARTIFACT_NOT_RETRIEVABLE, "artifact lifecycle is not retrievable");
    }

    public static void requireFresh(ArtifactSearchPage.Consistency consistency) {
        if (consistency != ArtifactSearchPage.Consistency.CONSISTENT) throw new ArtifactContractException(ArtifactContractErrorCode.STALE_INDEX, "search index is not consistent");
    }

    public static void rejectDuplicateArtifactLinks(List<ArtifactId> links) {
        if (links == null) throw new ArtifactContractException(ArtifactContractErrorCode.MISSING_REQUIRED_FACT, "artifact links are required");
        var distinct = new HashSet<ArtifactId>();
        for (ArtifactId link : links) {
            if (link == null || !distinct.add(link)) throw new ArtifactContractException(ArtifactContractErrorCode.DUPLICATE_IDEMPOTENCY_KEY, "duplicate Artifact link");
        }
    }
}
