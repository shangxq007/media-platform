package com.example.platform.identity.app;

import com.example.platform.identity.api.dto.ProjectImportedMetadataSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectImportMetadataReadServiceTest {

    @Mock
    private ProjectImportMetadataRepository repository;

    @Mock
    private MetadataScrubber scrubber;

    private ProjectImportMetadataReadService service;

    @BeforeEach
    void setUp() {
        service = new ProjectImportMetadataReadService(repository, scrubber);
    }

    @Test
    void findLatestByProjectShouldReturnSummary() {
        // Given
        ProjectImportMetadataRepository.MetadataRecord record =
                createSampleMetadataRecord("imp-1", "tenant-1", "prj-123");
        when(repository.findByProjectId("prj-123", "tenant-1"))
                .thenReturn(Optional.of(record));

        // When
        Optional<ProjectImportedMetadataSummaryDto> result =
                service.findLatestByProject("tenant-1", "prj-123");

        // Then
        assertTrue(result.isPresent());
        assertEquals("imp-1", result.get().importId());
        assertEquals("src-prj", result.get().sourceProjectId());
        assertTrue(result.get().timelinePresent());
        assertTrue(result.get().renderPlanPresent());
        assertTrue(result.get().spatialPlanPresent());
        assertTrue(result.get().assetsNeedUpload());
    }

    @Test
    void findLatestByProjectShouldReturnEmptyWhenNoMetadata() {
        // Given
        when(repository.findByProjectId("prj-123", "tenant-1"))
                .thenReturn(Optional.empty());

        // When
        Optional<ProjectImportedMetadataSummaryDto> result =
                service.findLatestByProject("tenant-1", "prj-123");

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void findLatestByProjectShouldDetectAbsentMetadata() {
        // Given
        ProjectImportMetadataRepository.MetadataRecord record =
                new ProjectImportMetadataRepository.MetadataRecord(
                        "meta-1", "tenant-1", "prj-123", "imp-1", "src-prj", "exp-1",
                        "project-export-v1", null, null, null, null,
                        null, null, null, null, Instant.now()
                );
        when(repository.findByProjectId("prj-123", "tenant-1"))
                .thenReturn(Optional.of(record));

        // When
        Optional<ProjectImportedMetadataSummaryDto> result =
                service.findLatestByProject("tenant-1", "prj-123");

        // Then
        assertTrue(result.isPresent());
        assertFalse(result.get().timelinePresent());
        assertFalse(result.get().renderPlanPresent());
        assertFalse(result.get().spatialPlanPresent());
    }

    @Test
    void findByImportIdShouldReturnSummary() {
        // Given
        ProjectImportMetadataRepository.MetadataRecord record =
                createSampleMetadataRecord("imp-1", "tenant-1", "prj-123");
        when(repository.findByImportId("imp-1"))
                .thenReturn(Optional.of(record));

        // When
        Optional<ProjectImportedMetadataSummaryDto> result =
                service.findByImportId("tenant-1", "imp-1");

        // Then
        assertTrue(result.isPresent());
        assertEquals("imp-1", result.get().importId());
    }

    @Test
    void findByImportIdShouldReturnEmptyForWrongTenant() {
        // Given
        ProjectImportMetadataRepository.MetadataRecord record =
                createSampleMetadataRecord("imp-1", "tenant-1", "prj-123");
        when(repository.findByImportId("imp-1"))
                .thenReturn(Optional.of(record));

        // When - request with different tenant
        Optional<ProjectImportedMetadataSummaryDto> result =
                service.findByImportId("tenant-2", "imp-1");

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void findByImportIdShouldReturnEmptyWhenNotFound() {
        // Given
        when(repository.findByImportId("imp-unknown"))
                .thenReturn(Optional.empty());

        // When
        Optional<ProjectImportedMetadataSummaryDto> result =
                service.findByImportId("tenant-1", "imp-unknown");

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void summaryShouldNotContainSensitiveFields() {
        // Given
        ProjectImportMetadataRepository.MetadataRecord record =
                createSampleMetadataRecord("imp-1", "tenant-1", "prj-123");
        when(repository.findByProjectId("prj-123", "tenant-1"))
                .thenReturn(Optional.of(record));

        // When
        Optional<ProjectImportedMetadataSummaryDto> result =
                service.findLatestByProject("tenant-1", "prj-123");

        // Then
        assertTrue(result.isPresent());
        ProjectImportedMetadataSummaryDto summary = result.get();
        assertEquals("src-prj", summary.sourceProjectId());
        // Summary DTO only contains boolean flags, no JSON content
        assertFalse(summary.toString().contains("downloadUrl"));
        assertFalse(summary.toString().contains("storageUri"));
    }

    private ProjectImportMetadataRepository.MetadataRecord createSampleMetadataRecord(
            String importId, String tenantId, String projectId) {
        return new ProjectImportMetadataRepository.MetadataRecord(
                "meta-1", tenantId, projectId, importId, "src-prj", "exp-1",
                "project-export-v1", "{\"tracks\":[]}", null,
                "{\"operations\":[]}", "{\"canvas\":{}}",
                null, null, null,
                "{\"art-1\":\"needs_upload\"}",
                Instant.now()
        );
    }
}
