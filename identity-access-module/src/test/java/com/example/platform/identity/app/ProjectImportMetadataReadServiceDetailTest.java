package com.example.platform.identity.app;

import com.example.platform.identity.api.dto.ProjectImportedMetadataDetailDto;
import com.example.platform.identity.api.dto.ProjectImportedMetadataSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectImportMetadataReadServiceDetailTest {

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
    void findLatestDetailByProjectShouldReturnDetail() {
        // Given
        ProjectImportMetadataRepository.MetadataRecord record =
                createSampleMetadataRecord("imp-1", "tenant-1", "prj-123");
        when(repository.findByProjectId("prj-123", "tenant-1"))
                .thenReturn(Optional.of(record));
        when(scrubber.scrub(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Optional<ProjectImportedMetadataDetailDto> result =
                service.findLatestDetailByProject("tenant-1", "prj-123");

        // Then
        assertTrue(result.isPresent());
        assertNotNull(result.get().summary());
        assertEquals("imp-1", result.get().summary().importId());
        assertNotNull(result.get().timeline());
        assertNotNull(result.get().renderPlan());
        assertNotNull(result.get().spatialPlan());
    }

    @Test
    void findLatestDetailByProjectShouldReturnEmptyWhenNoMetadata() {
        // Given
        when(repository.findByProjectId("prj-123", "tenant-1"))
                .thenReturn(Optional.empty());

        // When
        Optional<ProjectImportedMetadataDetailDto> result =
                service.findLatestDetailByProject("tenant-1", "prj-123");

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void findLatestDetailByProjectShouldRespectTenantScope() {
        // Given
        ProjectImportMetadataRepository.MetadataRecord record =
                createSampleMetadataRecord("imp-1", "tenant-2", "prj-123");
        when(repository.findByProjectId("prj-123", "tenant-1"))
                .thenReturn(Optional.empty());

        // When
        Optional<ProjectImportedMetadataDetailDto> result =
                service.findLatestDetailByProject("tenant-1", "prj-123");

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void findLatestDetailByProjectShouldReScrubSensitiveFields() {
        // Given
        String timelineJson = "{\"tracks\":[{\"clip\":{\"downloadUrl\":\"https://evil.com\"}}]}";
        ProjectImportMetadataRepository.MetadataRecord record =
                new ProjectImportMetadataRepository.MetadataRecord(
                        "meta-1", "tenant-1", "prj-123", "imp-1", "src-prj", "exp-1",
                        "project-export-v1", timelineJson, null,
                        null, null, null, null, null, null, Instant.now()
                );
        when(repository.findByProjectId("prj-123", "tenant-1"))
                .thenReturn(Optional.of(record));
        // Mock scrubber removes entire key-value pairs containing sensitive keys
        when(scrubber.scrub(anyString())).thenAnswer(invocation -> {
            String json = invocation.getArgument(0);
            if (json == null) return null;
            // Simulate real scrubber behavior: remove downloadUrl and its value
            return "{\"tracks\":[{\"clip\":{}}]}";
        });

        // When
        Optional<ProjectImportedMetadataDetailDto> result =
                service.findLatestDetailByProject("tenant-1", "prj-123");

        // Then
        assertTrue(result.isPresent());
        String timelineStr = result.get().timeline().toString();
        assertFalse(timelineStr.contains("downloadUrl"));
        assertFalse(timelineStr.contains("https://evil.com"));
    }

    @Test
    void findLatestDetailByProjectShouldNotContainStorageUri() {
        // Given
        String renderPlanJson = "{\"operations\":[{\"storageUri\":\"s3://bucket/key\"}]}";
        ProjectImportMetadataRepository.MetadataRecord record =
                new ProjectImportMetadataRepository.MetadataRecord(
                        "meta-1", "tenant-1", "prj-123", "imp-1", "src-prj", "exp-1",
                        "project-export-v1", null, null,
                        renderPlanJson, null, null, null, null, null, Instant.now()
                );
        when(repository.findByProjectId("prj-123", "tenant-1"))
                .thenReturn(Optional.of(record));
        // Mock scrubber removes entire key-value pairs containing sensitive keys
        when(scrubber.scrub(anyString())).thenAnswer(invocation -> {
            String json = invocation.getArgument(0);
            if (json == null) return null;
            // Simulate real scrubber behavior: remove storageUri and its value
            return "{\"operations\":[{}]}";
        });

        // When
        Optional<ProjectImportedMetadataDetailDto> result =
                service.findLatestDetailByProject("tenant-1", "prj-123");

        // Then
        assertTrue(result.isPresent());
        String renderPlanStr = result.get().renderPlan().toString();
        assertFalse(renderPlanStr.contains("storageUri"));
        assertFalse(renderPlanStr.contains("s3://"));
    }

    @Test
    void findLatestDetailByProjectShouldReturnAssetMappingNeedsUpload() {
        // Given
        String assetMappingJson = "{\"art-1\":\"needs_upload\",\"art-2\":\"needs_upload\"}";
        ProjectImportMetadataRepository.MetadataRecord record =
                new ProjectImportMetadataRepository.MetadataRecord(
                        "meta-1", "tenant-1", "prj-123", "imp-1", "src-prj", "exp-1",
                        "project-export-v1", null, null,
                        null, null, null, null, null, assetMappingJson, Instant.now()
                );
        when(repository.findByProjectId("prj-123", "tenant-1"))
                .thenReturn(Optional.of(record));
        when(scrubber.scrub(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Optional<ProjectImportedMetadataDetailDto> result =
                service.findLatestDetailByProject("tenant-1", "prj-123");

        // Then
        assertTrue(result.isPresent());
        Map<String, ProjectImportedMetadataDetailDto.AssetMappingEntry> mapping = result.get().assetMapping();
        assertNotNull(mapping);
        assertEquals(2, mapping.size());
        assertEquals("needs_upload", mapping.get("art-1").status());
        assertNull(mapping.get("art-1").targetAssetId());
    }

    @Test
    void findLatestDetailByProjectShouldHandleMalformedJson() {
        // Given
        ProjectImportMetadataRepository.MetadataRecord record =
                new ProjectImportMetadataRepository.MetadataRecord(
                        "meta-1", "tenant-1", "prj-123", "imp-1", "src-prj", "exp-1",
                        "project-export-v1", "invalid json", null,
                        null, null, null, null, null, null, Instant.now()
                );
        when(repository.findByProjectId("prj-123", "tenant-1"))
                .thenReturn(Optional.of(record));
        when(scrubber.scrub(anyString())).thenReturn("invalid json");

        // When
        Optional<ProjectImportedMetadataDetailDto> result =
                service.findLatestDetailByProject("tenant-1", "prj-123");

        // Then - should return detail with null timeline (malformed JSON handled gracefully)
        assertTrue(result.isPresent());
        assertNull(result.get().timeline());
    }

    @Test
    void findLatestDetailByProjectShouldReturnSummary() {
        // Given
        ProjectImportMetadataRepository.MetadataRecord record =
                createSampleMetadataRecord("imp-1", "tenant-1", "prj-123");
        when(repository.findByProjectId("prj-123", "tenant-1"))
                .thenReturn(Optional.of(record));
        when(scrubber.scrub(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Optional<ProjectImportedMetadataDetailDto> result =
                service.findLatestDetailByProject("tenant-1", "prj-123");

        // Then
        assertTrue(result.isPresent());
        ProjectImportedMetadataSummaryDto summary = result.get().summary();
        assertNotNull(summary);
        assertEquals("imp-1", summary.importId());
        assertEquals("src-prj", summary.sourceProjectId());
        assertEquals("exp-1", summary.sourceExportId());
        assertEquals("project-export-v1", summary.schemaVersion());
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
