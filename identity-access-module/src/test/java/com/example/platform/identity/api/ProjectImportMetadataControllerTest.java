package com.example.platform.identity.api;

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
class ProjectImportMetadataControllerTest {

    @Mock
    private ProjectImportMetadataReadService readService;

    private ProjectImportMetadataController controller;

    @BeforeEach
    void setUp() {
        controller = new ProjectImportMetadataController(readService);
    }

    @Test
    void getLatestImportMetadataShouldReturnMetadata() {
        // Given
        ProjectImportedMetadataSummaryDto summary = new ProjectImportedMetadataSummaryDto(
                "imp-1", "src-prj", "exp-1", "project-export-v1",
                true, false, true, true, false, true, true, true, true,
                "2026-06-06T00:00:00Z"
        );
        when(readService.findLatestByProject("tenant-1", "prj-123"))
                .thenReturn(Optional.of(summary));

        // When
        ResponseEntity<?> response = controller.getLatestImportMetadata("tenant-1", "prj-123");

        // Then
        assertEquals(200, response.getStatusCode().value());
        ProjectImportedMetadataSummaryDto body = (ProjectImportedMetadataSummaryDto) response.getBody();
        assertNotNull(body);
        assertEquals("imp-1", body.importId());
        assertTrue(body.timelinePresent());
        assertTrue(body.renderPlanPresent());
    }

    @Test
    void getLatestImportMetadataShouldReturn404WhenNoMetadata() {
        // Given
        when(readService.findLatestByProject("tenant-1", "prj-123"))
                .thenReturn(Optional.empty());

        // When
        ResponseEntity<?> response = controller.getLatestImportMetadata("tenant-1", "prj-123");

        // Then
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void getImportMetadataByIdShouldReturnMetadata() {
        // Given
        ProjectImportedMetadataSummaryDto summary = new ProjectImportedMetadataSummaryDto(
                "imp-1", "src-prj", "exp-1", "project-export-v1",
                true, false, true, true, false, true, true, true, true,
                "2026-06-06T00:00:00Z"
        );
        when(readService.findByImportId("tenant-1", "imp-1"))
                .thenReturn(Optional.of(summary));

        // When
        ResponseEntity<?> response = controller.getImportMetadataById("tenant-1", "imp-1");

        // Then
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void getImportMetadataByIdShouldReturn404WhenNotFound() {
        // Given
        when(readService.findByImportId("tenant-1", "imp-unknown"))
                .thenReturn(Optional.empty());

        // When
        ResponseEntity<?> response = controller.getImportMetadataById("tenant-1", "imp-unknown");

        // Then
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void endpointsShouldNotExposeJsonContent() {
        // Given - summary only contains boolean flags, no JSON
        ProjectImportedMetadataSummaryDto summary = new ProjectImportedMetadataSummaryDto(
                "imp-1", "src-prj", "exp-1", "project-export-v1",
                true, false, true, true, false, true, true, true, true,
                "2026-06-06T00:00:00Z"
        );
        when(readService.findLatestByProject("tenant-1", "prj-123"))
                .thenReturn(Optional.of(summary));

        // When
        ResponseEntity<?> response = controller.getLatestImportMetadata("tenant-1", "prj-123");

        // Then
        ProjectImportedMetadataSummaryDto body = (ProjectImportedMetadataSummaryDto) response.getBody();
        assertNotNull(body);
        // Verify summary doesn't contain JSON strings
        assertFalse(body.toString().contains("tracks"));
        assertFalse(body.toString().contains("operations"));
        assertFalse(body.toString().contains("canvas"));
    }

    @Test
    void endpointsShouldNotExposeSensitiveUrls() {
        // Given
        ProjectImportedMetadataSummaryDto summary = new ProjectImportedMetadataSummaryDto(
                "imp-1", "src-prj", "exp-1", "project-export-v1",
                true, false, true, true, false, true, true, true, true,
                "2026-06-06T00:00:00Z"
        );
        when(readService.findLatestByProject("tenant-1", "prj-123"))
                .thenReturn(Optional.of(summary));

        // When
        ResponseEntity<?> response = controller.getLatestImportMetadata("tenant-1", "prj-123");

        // Then
        String responseBody = response.getBody().toString();
        assertFalse(responseBody.contains("downloadUrl"));
        assertFalse(responseBody.contains("storageUri"));
        assertFalse(responseBody.contains("signedUrl"));
        assertFalse(responseBody.contains("bucket"));
        assertFalse(responseBody.contains("https://"));
    }
}
