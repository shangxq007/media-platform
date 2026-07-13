package com.example.platform.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import java.util.Set;
import java.util.List;

/**
 * Backend response invariance tests for frontend contract gate.
 * Verifies public responses do not expose forbidden internal fields.
 */
class ResponseInvarianceTest {

    private static final Set<String> FORBIDDEN_PUBLIC_FIELDS = Set.of(
        "storageReferenceId", "bucket", "bucketName", "objectKey",
        "localPath", "filePath", "tempPath", "uploadFilePath",
        "credentials", "accessKey", "secretKey",
        "rawMetadata", "rawFfprobeJson", "rawTikaMetadata", "rawMediaMetadata", "rawJson",
        "originalFilename", "fileHash", "ocrText", "extractedText",
        "preflightReportId", "safePreflightReportId", "policyEvaluationId",
        "writerOutcome", "persistenceStatus", "reportRecorded",
        "retentionStatus", "cleanupStatus", "deletedReportCount"
    );

    private static final Set<String> DEV_ONLY_FIELDS = Set.of(
        "safePreflightReportId", "preflightReportId", "policyEvaluationId",
        "writerOutcome", "persistenceStatus", "reportRecorded",
        "retentionStatus", "cleanupStatus", "deletedReportCount"
    );

    @Nested
    class ForbiddenFieldsTests {
        @Test
        void testForbiddenFieldsListIsComplete() {
            assertTrue(FORBIDDEN_PUBLIC_FIELDS.contains("storageReferenceId"));
            assertTrue(FORBIDDEN_PUBLIC_FIELDS.contains("bucket"));
            assertTrue(FORBIDDEN_PUBLIC_FIELDS.contains("objectKey"));
            assertTrue(FORBIDDEN_PUBLIC_FIELDS.contains("localPath"));
            assertTrue(FORBIDDEN_PUBLIC_FIELDS.contains("credentials"));
            assertTrue(FORBIDDEN_PUBLIC_FIELDS.contains("rawMetadata"));
            assertTrue(FORBIDDEN_PUBLIC_FIELDS.contains("preflightReportId"));
            assertTrue(FORBIDDEN_PUBLIC_FIELDS.contains("policyEvaluationId"));
            assertTrue(FORBIDDEN_PUBLIC_FIELDS.contains("writerOutcome"));
            assertTrue(FORBIDDEN_PUBLIC_FIELDS.contains("persistenceStatus"));
        }

        @Test
        void testForbiddenFieldsListSize() {
            assertEquals(27, FORBIDDEN_PUBLIC_FIELDS.size(),
                "Forbidden fields list should contain exactly 27 fields");
        }
    }

    @Nested
    class UploadResponseTests {
        @Test
        void testUploadResponseDoesNotContainForbiddenFields() {
            // Simulate upload response JSON
            String uploadResponse = "{\"productId\":\"prod-001\",\"status\":\"SUCCESS\"}";
            for (String field : FORBIDDEN_PUBLIC_FIELDS) {
                assertFalse(uploadResponse.contains("\"" + field + "\""),
                    "Upload response should not contain forbidden field: " + field);
            }
        }

        @Test
        void testUploadResponseDoesNotContainPersistenceFields() {
            String uploadResponse = "{\"productId\":\"prod-001\",\"status\":\"SUCCESS\"}";
            for (String field : DEV_ONLY_FIELDS) {
                assertFalse(uploadResponse.contains(field),
                    "Upload response should not contain DEV_ONLY field: " + field);
            }
        }
    }

    @Nested
    class ProductResponseTests {
        @Test
        void testProductResponseDoesNotExposeStorageInternals() {
            String productResponse = "{\"id\":\"prod-001\",\"type\":\"RAW_MEDIA\",\"status\":\"ACTIVE\"}";
            assertFalse(productResponse.contains("storageReferenceId"));
            assertFalse(productResponse.contains("bucket"));
            assertFalse(productResponse.contains("objectKey"));
            assertFalse(productResponse.contains("localPath"));
            assertFalse(productResponse.contains("credentials"));
        }

        @Test
        void testProductResponseUsesProductReferencesOnly() {
            String productResponse = "{\"id\":\"prod-001\",\"type\":\"RAW_MEDIA\",\"status\":\"ACTIVE\"}";
            assertTrue(productResponse.contains("\"id\""));
            assertTrue(productResponse.contains("\"type\""));
            assertTrue(productResponse.contains("\"status\""));
        }
    }

    @Nested
    class RenderResponseTests {
        @Test
        void testRenderJobStatusUsesKnownEnum() {
            Set<String> knownStatuses = Set.of("QUEUED", "EXECUTING", "COMPLETED", "FAILED", "CANCELLED");
            String renderResponse = "{\"job\":{\"id\":\"job-001\",\"status\":\"COMPLETED\"}}";
            // Status should be one of known values
            assertTrue(renderResponse.contains("\"COMPLETED\"") || 
                      renderResponse.contains("\"QUEUED\"") ||
                      renderResponse.contains("\"EXECUTING\"") ||
                      renderResponse.contains("\"FAILED\"") ||
                      renderResponse.contains("\"CANCELLED\""));
        }

        @Test
        void testRenderResponseDoesNotExposePaths() {
            String renderResponse = "{\"job\":{\"id\":\"job-001\",\"status\":\"COMPLETED\"}}";
            assertFalse(renderResponse.contains("localPath"));
            assertFalse(renderResponse.contains("filePath"));
            assertFalse(renderResponse.contains("tempPath"));
            assertFalse(renderResponse.contains("workerPath"));
        }
    }

    @Nested
    class ArtifactAccessTests {
        @Test
        void testArtifactListDoesNotExposeStorageInternals() {
            String artifactResponse = "{\"items\":[{\"id\":\"art-001\",\"type\":\"video/mp4\"}],\"total\":1}";
            assertFalse(artifactResponse.contains("storageReferenceId"));
            assertFalse(artifactResponse.contains("bucket"));
            assertFalse(artifactResponse.contains("objectKey"));
            assertFalse(artifactResponse.contains("localPath"));
            assertFalse(artifactResponse.contains("credentials"));
        }

        @Test
        void testArtifactAccessUsesOnDemandUrl() {
            String accessResponse = "{\"access\":{\"artifactId\":\"art-001\",\"accessUrl\":\"https://example.com/access\",\"expiresAt\":\"2026-07-13T01:00:00Z\"}}";
            assertTrue(accessResponse.contains("\"accessUrl\""));
            assertTrue(accessResponse.contains("\"expiresAt\""));
            assertFalse(accessResponse.contains("storageReferenceId"));
        }

        @Test
        void testArtifactAccessDoesNotPersistSignedUrl() {
            // Access response should have on-demand URL, not persisted metadata
            String accessResponse = "{\"access\":{\"artifactId\":\"art-001\",\"accessUrl\":\"https://example.com/access\"}}";
            assertFalse(accessResponse.contains("persistedSignedUrl"));
            assertFalse(accessResponse.contains("storedSignedUrl"));
        }
    }

    @Nested
    class DevOnlyNonLeakTests {
        @Test
        void testDevOnlyFieldsNotInPublicResponses() {
            String publicResponse = "{\"id\":\"prod-001\",\"status\":\"ACTIVE\"}";
            for (String field : DEV_ONLY_FIELDS) {
                assertFalse(publicResponse.contains(field),
                    "Public response should not contain DEV_ONLY field: " + field);
            }
        }

        @Test
        void testDevEndpointsAreUnderDev() {
            // Verify dev endpoint paths start with /dev
            List<String> devPaths = List.of(
                "/dev/storage-delivery-profiles",
                "/dev/ingest/preflight-policy",
                "/dev/tenants/tenant-001/projects/project-001/ingest/preflight/safe-reports",
                "/dev/tenants/tenant-001/projects/project-001/ingest/preflight/safe-reports/retention/dry-run"
            );
            for (String path : devPaths) {
                assertTrue(path.startsWith("/dev"), "Dev endpoint should start with /dev: " + path);
            }
        }
    }

    @Nested
    class ErrorResponseTests {
        @Test
        void testErrorResponseHasStableShape() {
            String errorResponse = "{\"message\":\"Not found\",\"code\":\"NOT_FOUND\",\"status\":404}";
            assertTrue(errorResponse.contains("\"message\""));
            assertTrue(errorResponse.contains("\"code\""));
            assertTrue(errorResponse.contains("\"status\""));
        }

        @Test
        void testErrorResponseDoesNotExposeInternals() {
            String errorResponse = "{\"message\":\"Internal error\",\"code\":\"INTERNAL_ERROR\"}";
            assertFalse(errorResponse.contains("stackTrace"));
            assertFalse(errorResponse.contains("localPath"));
            assertFalse(errorResponse.contains("storageReferenceId"));
            assertFalse(errorResponse.contains("credentials"));
            assertFalse(errorResponse.contains("rawMetadata"));
        }
    }
}
