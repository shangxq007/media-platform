package com.example.platform.artifact.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.domain.ArtifactCatalogEntry;
import com.example.platform.artifact.domain.ArtifactStatus;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ArtifactSourcePinAuthorityServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String PROJECT = "project-1";
    private static final String OTHER_PROJECT = "project-2";
    private static final String DIGEST = "a".repeat(64);
    private static final ArtifactId ARTIFACT = new ArtifactId("art-1");

    private final ArtifactCatalogService catalog = mock(ArtifactCatalogService.class);
    private final ArtifactSourcePinAuthorityService authority =
            new ArtifactSourcePinAuthorityService(catalog);

    private static ArtifactCatalogEntry entry(
            String projectId, String checksum, ArtifactStatus status) {
        return new ArtifactCatalogEntry(
                ARTIFACT.value(), "job-1", projectId, "mp4", null, null, 10L, checksum, status,
                null, Instant.parse("2026-09-25T00:00:00Z"));
    }

    private void given(ArtifactCatalogEntry value) {
        when(catalog.findArtifact(TENANT, ARTIFACT.value())).thenReturn(Optional.of(value));
    }

    @Test
    void resolvedWhenIdentityScopeStatusAndDigestAllMatch() {
        given(entry(PROJECT, DIGEST, ArtifactStatus.ACTIVE));

        var resolution = authority.resolvePin(
                TENANT, PROJECT, ARTIFACT, ContentDigest.sha256(DIGEST));

        assertThat(resolution.outcome()).isEqualTo(ArtifactSourcePinAuthority.Outcome.RESOLVED);
        assertThat(resolution.resolved()).isTrue();
        assertThat(resolution.artifactDigest()).isEqualTo(DIGEST);
    }

    @Test
    void unknownArtifactWhenCatalogHasNoRowForTheTenant() {
        when(catalog.findArtifact(TENANT, ARTIFACT.value())).thenReturn(Optional.empty());

        var resolution = authority.resolvePin(
                TENANT, PROJECT, ARTIFACT, ContentDigest.sha256(DIGEST));

        assertThat(resolution.outcome())
                .isEqualTo(ArtifactSourcePinAuthority.Outcome.UNKNOWN_ARTIFACT);
        assertThat(resolution.artifactDigest()).isNull();
    }

    @Test
    void outOfScopeWhenArtifactBelongsToAnotherProject() {
        given(entry(OTHER_PROJECT, DIGEST, ArtifactStatus.ACTIVE));

        var resolution = authority.resolvePin(
                TENANT, PROJECT, ARTIFACT, ContentDigest.sha256(DIGEST));

        assertThat(resolution.outcome()).isEqualTo(ArtifactSourcePinAuthority.Outcome.OUT_OF_SCOPE);
    }

    @Test
    void notUsableWhenLifecycleStatusIsTombstoned() {
        given(entry(PROJECT, DIGEST, ArtifactStatus.TOMBSTONED));

        var resolution = authority.resolvePin(
                TENANT, PROJECT, ARTIFACT, ContentDigest.sha256(DIGEST));

        assertThat(resolution.outcome()).isEqualTo(ArtifactSourcePinAuthority.Outcome.NOT_USABLE);
    }

    @Test
    void pinMismatchWhenRecordedDigestDiffersFromThePin() {
        given(entry(PROJECT, "b".repeat(64), ArtifactStatus.ACTIVE));

        var resolution = authority.resolvePin(
                TENANT, PROJECT, ARTIFACT, ContentDigest.sha256(DIGEST));

        assertThat(resolution.outcome()).isEqualTo(ArtifactSourcePinAuthority.Outcome.PIN_MISMATCH);
    }

    @Test
    void pinMismatchWhenRecordedDigestIsAbsent() {
        given(entry(PROJECT, null, ArtifactStatus.ACTIVE));

        var resolution = authority.resolvePin(
                TENANT, PROJECT, ARTIFACT, ContentDigest.sha256(DIGEST));

        assertThat(resolution.outcome()).isEqualTo(ArtifactSourcePinAuthority.Outcome.PIN_MISMATCH);
    }

    @Test
    void scopeIsMandatoryAndNeverFallsBackToAmbientContext() {
        assertThatThrownBy(() -> authority.resolvePin(
                null, PROJECT, ARTIFACT, ContentDigest.sha256(DIGEST)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
        assertThatThrownBy(() -> authority.resolvePin(
                TENANT, " ", ARTIFACT, ContentDigest.sha256(DIGEST)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("projectId");
    }

    @Test
    void authorityPublishesNoMutationSurface() {
        assertThat(ArtifactSourcePinAuthority.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .containsExactly("resolvePin");
    }
}
