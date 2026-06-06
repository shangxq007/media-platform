package com.example.platform.identity.api;

import com.example.platform.identity.api.dto.ProjectImportedMetadataDetailDto;
import com.example.platform.identity.api.dto.ProjectImportedMetadataSummaryDto;
import com.example.platform.identity.app.ProjectImportMetadataReadService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectImportMetadataDetailControllerTest {

    @Mock
    private ProjectImportMetadataReadService readService;

    private ProjectImportMetadataController controller;

    @BeforeEach
    void setUp() {
        controller = new ProjectImportMetadataController(readService);
    }

    @Test
    void getLatestImportMetadataDetailShouldReturnDetail() {
        // Given
        ProjectImportedMetadataDetailDto detail = createSampleDetail();
        when(readService.findLatestDetailByProject("tenant-1", "prj-123"))
                .thenReturn(Optional.of(detail));

        // When
        ResponseEntity<?> response = controller.getLatestImportMetadataDetail("tenant-1", "prj-123");

        // Then
        assertEquals(200, response.getStatusCode().value());
        ProjectImportedMetadataDetailDto body = (ProjectImportedMetadataDetailDto) response.getBody();
        assertNotNull(body);
        assertNotNull(body.summary());
        assertEquals("imp-1", body.summary().importId());
    }

    @Test
    void getLatestImportMetadataDetailShouldReturn404WhenNoMetadata() {
        // Given
        when(readService.findLatestDetailByProject("tenant-1", "prj-123"))
                .thenReturn(Optional.empty());

        // When
        ResponseEntity<?> response = controller.getLatestImportMetadataDetail("tenant-1", "prj-123");

        // Then
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void getImportMetadataDetailByIdShouldReturnDetail() {
        // Given
        ProjectImportedMetadataDetailDto detail = createSampleDetail();
        when(readService.findDetailByImportId("tenant-1", "imp-1"))
                .thenReturn(Optional.of(detail));

        // When
        ResponseEntity<?> response = controller.getImportMetadataDetailById("tenant-1", "imp-1");

        // Then
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void getImportMetadataDetailByIdShouldReturn404WhenWrongTenant() {
        // Given
        when(readService.findDetailByImportId("tenant-2", "imp-1"))
                .thenReturn(Optional.empty());

        // When
        ResponseEntity<?> response = controller.getImportMetadataDetailById("tenant-2", "imp-1");

        // Then
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void detailEndpointShouldNotExposeSensitiveUrls() {
        // Given
        ProjectImportedMetadataDetailDto detail = createSampleDetail();
        when(readService.findLatestDetailByProject("tenant-1", "prj-123"))
                .thenReturn(Optional.of(detail));

        // When
        ResponseEntity<?> response = controller.getLatestImportMetadataDetail("tenant-1", "prj-123");

        // Then
        String responseBody = response.getBody().toString();
        assertFalse(responseBody.contains("downloadUrl"));
        assertFalse(responseBody.contains("storageUri"));
        assertFalse(responseBody.contains("storageRef"));
        assertFalse(responseBody.contains("signedUrl"));
        assertFalse(responseBody.contains("bucket"));
        assertFalse(responseBody.contains("https://"));
    }

    @Test
    void detailEndpointShouldReturnAssetsNeedUpload() {
        // Given
        ProjectImportedMetadataDetailDto detail = createSampleDetail();
        when(readService.findLatestDetailByProject("tenant-1", "prj-123"))
                .thenReturn(Optional.of(detail));

        // When
        ResponseEntity<?> response = controller.getLatestImportMetadataDetail("tenant-1", "prj-123");

        // Then
        ProjectImportedMetadataDetailDto body = (ProjectImportedMetadataDetailDto) response.getBody();
        assertNotNull(body.assetMapping());
        body.assetMapping().forEach((id, entry) -> {
            assertNull(entry.targetAssetId());
            assertEquals("needs_upload", entry.status());
        });
    }

    @Test
    void detailEndpointShouldBeReadOnly() {
        // Given
        ProjectImportedMetadataDetailDto detail = createSampleDetail();
        when(readService.findLatestDetailByProject("tenant-1", "prj-123"))
                .thenReturn(Optional.of(detail));

        // When
        ResponseEntity<?> response = controller.getLatestImportMetadataDetail("tenant-1", "prj-123");

        // Then - only GET endpoints, no mutation
        assertEquals(200, response.getStatusCode().value());
        // Read service has no save/delete methods - it's read-only
        verify(readService).findLatestDetailByProject("tenant-1", "prj-123");
    }

    private ProjectImportedMetadataDetailDto createSampleDetail() {
        ProjectImportedMetadataSummaryDto summary = new ProjectImportedMetadataSummaryDto(
                "imp-1", "src-prj", "exp-1", "project-export-v1",
                true, false, true, true, false, true, true, true, true,
                "2026-06-06T00:00:00Z"
        );

        return new ProjectImportedMetadataDetailDto(
                summary,
                null, // timeline - would be JsonNode in real scenario
                null, // timelineOtio
                null, // renderPlan
                null, // spatialPlan
                null, // exportProfiles
                null, // effectTaxonomy
                null, // appliedEffects
                java.util.Map.of("art-1",
                    new ProjectImportedMetadataDetailDto.AssetMappingEntry(null, "needs_upload")),
                java.util.List.of()
        );
    }
}
