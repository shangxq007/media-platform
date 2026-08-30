package com.example.platform.artifact.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.domain.ArtifactCatalogEntry;
import com.example.platform.artifact.domain.ArtifactStatus;
import com.example.platform.shared.web.ErrorCodeRegistry;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ArtifactCatalogServiceTest {

    @Test
    void projectionReadsCanonicalRepositoryWithoutRegistrationFallback() {
        ArtifactCatalogRepository repository = mock(ArtifactCatalogRepository.class);
        ArtifactRelationRepository relations = mock(ArtifactRelationRepository.class);
        ArtifactCatalogEntry entry = new ArtifactCatalogEntry(
                "art-1", "job-1", "project-1", "VIDEO", null, null,
                10L, "a".repeat(64), ArtifactStatus.ACTIVE, null, Instant.now());
        when(repository.findById("art-1")).thenReturn(Optional.of(entry));
        when(repository.countAll()).thenReturn(1);

        ArtifactCatalogService service = new ArtifactCatalogService(
                repository, relations, mock(ErrorCodeRegistry.class));

        assertEquals(Optional.of(entry), service.findArtifact("art-1"));
        assertEquals(1, service.overview().get("artifactCount"));
        assertTrue(java.util.Arrays.stream(ArtifactCatalogService.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().startsWith("register")));
    }

    @Test
    void relationWriteUsesCanonicalRelationRepository() {
        ArtifactRelationRepository relations = mock(ArtifactRelationRepository.class);
        ArtifactCatalogService service = new ArtifactCatalogService(
                mock(ArtifactCatalogRepository.class), relations, mock(ErrorCodeRegistry.class));

        var relation = service.relateArtifacts("art-a", "art-b", "DERIVED_FROM");

        verify(relations).save(relation);
    }
}
