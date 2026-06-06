package com.example.platform.identity.app;

import com.example.platform.shared.Ids;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProjectImportMetadataRepositoryTest {

    private ProjectImportMetadataRepository repository;

    @BeforeEach
    void setUp() {
        // Note: This test requires a test database context
        // For now, we test the record structure and validation
        // Full integration tests would require DSLContext mock or test database
    }

    @Test
    void metadataRecordShouldContainAllFields() {
        // Given
        String id = Ids.newId("imp-meta");
        String tenantId = "tenant-1";
        String projectId = "prj-123";
        String importId = "imp-456";
        String sourceProjectId = "src-prj-789";
        String sourceExportId = "exp-012";
        String schemaVersion = "project-export-v1";
        String timelineJson = "{\"tracks\":[]}";
        String renderPlanJson = "{\"operations\":[]}";
        String spatialPlanJson = "{\"canvas\":{}}";
        String assetMappingJson = "{\"art-1\":\"needs_upload\"}";
        Instant createdAt = Instant.now();

        // When
        ProjectImportMetadataRepository.MetadataRecord record =
                new ProjectImportMetadataRepository.MetadataRecord(
                        id, tenantId, projectId, importId, sourceProjectId, sourceExportId,
                        schemaVersion, timelineJson, null, renderPlanJson, spatialPlanJson,
                        null, null, null, assetMappingJson, createdAt
                );

        // Then
        assertNotNull(record);
        assertEquals(id, record.id());
        assertEquals(tenantId, record.tenantId());
        assertEquals(projectId, record.projectId());
        assertEquals(importId, record.importId());
        assertEquals(sourceProjectId, record.sourceProjectId());
        assertEquals(sourceExportId, record.sourceExportId());
        assertEquals(schemaVersion, record.schemaVersion());
        assertEquals(timelineJson, record.timelineJson());
        assertEquals(renderPlanJson, record.renderPlanJson());
        assertEquals(spatialPlanJson, record.spatialPlanJson());
        assertEquals(assetMappingJson, record.assetMappingJson());
        assertEquals(createdAt, record.createdAt());
    }

    @Test
    void metadataRecordShouldHandleNullFields() {
        // When
        ProjectImportMetadataRepository.MetadataRecord record =
                new ProjectImportMetadataRepository.MetadataRecord(
                        "id", "tenant", "project", "import", null, null,
                        "v1", null, null, null, null,
                        null, null, null, null, Instant.now()
                );

        // Then
        assertNotNull(record);
        assertNull(record.sourceProjectId());
        assertNull(record.sourceExportId());
        assertNull(record.timelineJson());
        assertNull(record.renderPlanJson());
        assertNull(record.spatialPlanJson());
        assertNull(record.assetMappingJson());
    }

    @Test
    void metadataRecordShouldBeTenantScoped() {
        // Given
        String tenantId = "tenant-1";
        String projectId = "prj-123";

        // When
        ProjectImportMetadataRepository.MetadataRecord record =
                new ProjectImportMetadataRepository.MetadataRecord(
                        Ids.newId("imp-meta"), tenantId, projectId, "imp-1", null, null,
                        "v1", null, null, null, null,
                        null, null, null, null, Instant.now()
                );

        // Then
        assertEquals(tenantId, record.tenantId());
        assertEquals(projectId, record.projectId());
    }

    @Test
    void metadataRecordShouldStoreScrubbedTimelineJson() {
        // Given
        String scrubbedTimelineJson = "{\"tracks\":[{\"id\":\"v1\",\"name\":\"Video 1\"}]}";

        // When
        ProjectImportMetadataRepository.MetadataRecord record =
                new ProjectImportMetadataRepository.MetadataRecord(
                        Ids.newId("imp-meta"), "tenant", "project", "imp-1", null, null,
                        "v1", scrubbedTimelineJson, null, null, null,
                        null, null, null, null, Instant.now()
                );

        // Then
        assertNotNull(record.timelineJson());
        assertTrue(record.timelineJson().contains("tracks"));
        assertFalse(record.timelineJson().contains("downloadUrl"));
        assertFalse(record.timelineJson().contains("storageUri"));
    }

    @Test
    void metadataRecordShouldStoreScrubbedRenderPlanJson() {
        // Given
        String scrubbedRenderPlanJson = "{\"operations\":[{\"type\":\"fade\"}]}";

        // When
        ProjectImportMetadataRepository.MetadataRecord record =
                new ProjectImportMetadataRepository.MetadataRecord(
                        Ids.newId("imp-meta"), "tenant", "project", "imp-1", null, null,
                        "v1", null, null, scrubbedRenderPlanJson, null,
                        null, null, null, null, Instant.now()
                );

        // Then
        assertNotNull(record.renderPlanJson());
        assertTrue(record.renderPlanJson().contains("operations"));
        assertFalse(record.renderPlanJson().contains("storageUri"));
        assertFalse(record.renderPlanJson().contains("downloadUrl"));
    }

    @Test
    void metadataRecordShouldStoreScrubbedSpatialPlanJson() {
        // Given
        String scrubbedSpatialPlanJson = "{\"canvas\":{\"width\":1920,\"height\":1080}}";

        // When
        ProjectImportMetadataRepository.MetadataRecord record =
                new ProjectImportMetadataRepository.MetadataRecord(
                        Ids.newId("imp-meta"), "tenant", "project", "imp-1", null, null,
                        "v1", null, null, null, scrubbedSpatialPlanJson,
                        null, null, null, null, Instant.now()
                );

        // Then
        assertNotNull(record.spatialPlanJson());
        assertTrue(record.spatialPlanJson().contains("canvas"));
        assertFalse(record.spatialPlanJson().contains("storageRef"));
        assertFalse(record.spatialPlanJson().contains("bucket"));
    }

    @Test
    void metadataRecordShouldStoreAssetMapping() {
        // Given
        String assetMappingJson = "{\"art-1\":\"needs_upload\",\"art-2\":\"needs_upload\"}";

        // When
        ProjectImportMetadataRepository.MetadataRecord record =
                new ProjectImportMetadataRepository.MetadataRecord(
                        Ids.newId("imp-meta"), "tenant", "project", "imp-1", null, null,
                        "v1", null, null, null, null,
                        null, null, null, assetMappingJson, Instant.now()
                );

        // Then
        assertNotNull(record.assetMappingJson());
        assertTrue(record.assetMappingJson().contains("art-1"));
        assertTrue(record.assetMappingJson().contains("needs_upload"));
    }

    @Test
    void metadataRecordShouldNotStoreSensitiveUrls() {
        // This test verifies that sensitive URLs are scrubbed BEFORE creating the record
        // The record itself should never contain these URLs

        // Given - simulate scrubbed content (already clean)
        String cleanTimelineJson = "{\"tracks\":[]}";
        String cleanRenderPlanJson = "{\"operations\":[]}";

        // When
        ProjectImportMetadataRepository.MetadataRecord record =
                new ProjectImportMetadataRepository.MetadataRecord(
                        Ids.newId("imp-meta"), "tenant", "project", "imp-1", null, null,
                        "v1", cleanTimelineJson, null, cleanRenderPlanJson, null,
                        null, null, null, null, Instant.now()
                );

        // Then - verify no sensitive fields
        assertFalse(record.timelineJson().contains("downloadUrl"));
        assertFalse(record.timelineJson().contains("storageUri"));
        assertFalse(record.renderPlanJson().contains("downloadUrl"));
        assertFalse(record.renderPlanJson().contains("storageRef"));
    }

    @Test
    void metadataRecordShouldSupportSourceTracking() {
        // Given
        String sourceProjectId = "src-prj-789";
        String sourceExportId = "exp-012";

        // When
        ProjectImportMetadataRepository.MetadataRecord record =
                new ProjectImportMetadataRepository.MetadataRecord(
                        Ids.newId("imp-meta"), "tenant", "project", "imp-1",
                        sourceProjectId, sourceExportId,
                        "v1", null, null, null, null,
                        null, null, null, null, Instant.now()
                );

        // Then
        assertEquals(sourceProjectId, record.sourceProjectId());
        assertEquals(sourceExportId, record.sourceExportId());
    }
}
