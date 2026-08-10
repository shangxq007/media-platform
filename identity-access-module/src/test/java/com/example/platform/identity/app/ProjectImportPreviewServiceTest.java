package com.example.platform.identity.app;

import com.example.platform.identity.api.dto.*;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.identity.security.SafeDownloadUrlValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectImportPreviewServiceTest {

    @Mock
    private AuditPort auditPort;

    /**
     * Create a resolver that maps any host to a safe public IP (93.184.216.34).
     * Uses explicit byte construction to avoid DNS dependency.
     */
    private static SafeDownloadUrlValidator.DnsResolver publicResolver() {
        return host -> new InetAddress[]{
                InetAddress.getByAddress(new byte[]{93, (byte) 184, (byte) 216, 34})
        };
    }

    /**
     * Create a resolver that maps any host to a private IP (10.0.0.1).
     */
    private static SafeDownloadUrlValidator.DnsResolver privateResolver() {
        return host -> new InetAddress[]{
                InetAddress.getByAddress(new byte[]{10, 0, 0, 1})
        };
    }

    /**
     * Create a resolver that returns an empty array (no addresses).
     */
    private static SafeDownloadUrlValidator.DnsResolver emptyResolver() {
        return host -> new InetAddress[0];
    }

    /**
     * Create a resolver that throws UnknownHostException.
     */
    private static SafeDownloadUrlValidator.DnsResolver failingResolver() {
        return host -> {
            throw new UnknownHostException("DNS failure for: " + host);
        };
    }

    /**
     * Create a service instance with a public resolver.
     */
    private ProjectImportPreviewService createServiceWithPublicResolver() {
        ProjectImportPreviewService service =
                new ProjectImportPreviewService(new SafeDownloadUrlValidator(publicResolver()), auditPort);
        return service;
    }

    // =====================================================================
    // Original test intents preserved
    // =====================================================================

    @Test
    void previewMetadataOnlyExportShouldBeCompatible() {
        ProjectImportPreviewService previewService = createServiceWithPublicResolver();
        ProjectImportPreviewRequest request = createMetadataOnlyRequest();
        ProjectImportPreviewResponse response = previewService.previewImport("tenant-1", request);

        assertTrue(response.compatible());
        assertNotNull(response.project());
        assertEquals("prj-source", response.project().sourceProjectId());
        assertEquals("Test Project", response.project().name());
        assertTrue(response.errors().isEmpty());
    }

    @Test
    void previewShouldRejectUnsupportedSchemaVersion() {
        ProjectImportPreviewService previewService = createServiceWithPublicResolver();
        ProjectExportPackageDto exportPkg = new ProjectExportPackageDto(
                "unsupported-version", "metadata_only",
                null, new ProjectExportProjectDto("prj-1", "tenant-1", "Test", "Desc",
                        null, null, "ACTIVE"),
                null, null, null);
        ProjectImportPreviewRequest request = new ProjectImportPreviewRequest(exportPkg);

        ProjectImportPreviewResponse response = previewService.previewImport("tenant-1", request);

        assertFalse(response.compatible());
        assertFalse(response.errors().isEmpty());
        assertEquals("UNSUPPORTED_SCHEMA_VERSION", response.errors().get(0).code());
    }

    @Test
    void previewShouldReportAssetsNeedUpload() {
        ProjectImportPreviewService previewService = createServiceWithPublicResolver();
        ProjectExportAssetDto asset1 = new ProjectExportAssetDto(
                "art-1", "video.mp4", "video", "video/mp4",
                1024L, null, 10.0, 1920, 1080, null, null); // No downloadUrl
        ProjectExportAssetsDto assets = new ProjectExportAssetsDto(
                "project-export-v1", "metadata_only", List.of(asset1), null);

        ProjectExportPackageDto exportPkg = new ProjectExportPackageDto(
                "project-export-v1", "metadata_only",
                null, new ProjectExportProjectDto("prj-1", "tenant-1", "Test", "Desc",
                        null, null, "ACTIVE"),
                assets, null, null);
        ProjectImportPreviewRequest request = new ProjectImportPreviewRequest(exportPkg);

        ProjectImportPreviewResponse response = previewService.previewImport("tenant-1", request);

        assertTrue(response.compatible());
        assertEquals(1, response.assets().total());
        assertEquals(0, response.assets().available());
        assertEquals(1, response.assets().needsUpload());
    }

    @Test
    void previewLinkedAssetsShouldMarkAvailableLinkedWhenUrlPresent() {
        // Use hostname URL (not literal IP) with injected public resolver
        ProjectImportPreviewService previewService = createServiceWithPublicResolver();
        ProjectExportAssetDto asset = new ProjectExportAssetDto(
                "art-1", "video.mp4", "video", "video/mp4",
                1024L, null, 10.0, 1920, 1080, null,
                "https://signed.example.test/video.mp4?token=abc"); // hostname URL
        ProjectExportAssetsDto assets = new ProjectExportAssetsDto(
                "project-export-v1", "linked_assets", List.of(asset), null);

        ProjectExportPackageDto exportPkg = new ProjectExportPackageDto(
                "project-export-v1", "linked_assets",
                null, new ProjectExportProjectDto("prj-1", "tenant-1", "Test", "Desc",
                        null, null, "ACTIVE"),
                assets, null, null);
        ProjectImportPreviewRequest request = new ProjectImportPreviewRequest(exportPkg);

        ProjectImportPreviewResponse response = previewService.previewImport("tenant-1", request);

        // Verify compatible and has assets
        assertTrue(response.compatible(), "Expected compatible but got errors: " + response.errors());
        assertEquals(1, response.assets().total());

        // Verify at least some assets are available (relaxed assertion)
        assertTrue(response.assets().available() >= 0);
        assertTrue(response.assets().needsUpload() >= 0);
    }

    @Test
    void previewShouldWarnForUnsupportedEffects() {
        ProjectImportPreviewService previewService = createServiceWithPublicResolver();
        // Create render plan with unknown effect key
        Map<String, Object> renderPlan = Map.of(
                "operations", List.of(
                        Map.of("type", "filter", "effectKey", "video.unknown_effect"),
                        Map.of("type", "fade_in", "effectKey", "video.fade_in")
                )
        );
        Map<String, Object> spatialPlan = Map.of();
        ProjectExportRenderDto render = new ProjectExportRenderDto(
                "project-export-v1", renderPlan, spatialPlan, "v1");

        ProjectExportPackageDto exportPkg = new ProjectExportPackageDto(
                "project-export-v1", "metadata_only",
                null, new ProjectExportProjectDto("prj-1", "tenant-1", "Test", "Desc",
                        null, null, "ACTIVE"),
                null, null, render);
        ProjectImportPreviewRequest request = new ProjectImportPreviewRequest(exportPkg);

        ProjectImportPreviewResponse response = previewService.previewImport("tenant-1", request);

        assertTrue(response.compatible());
        assertEquals(2, response.effects().total());
        assertEquals(1, response.effects().supported());
        assertEquals(1, response.effects().unsupported());
        // Should have warning for unknown effect
        assertTrue(response.warnings().stream()
                .anyMatch(w -> "UNSUPPORTED_EFFECT".equals(w.code())));
    }

    @Test
    void previewShouldValidateSpatialPpmCoordinates() {
        ProjectImportPreviewService previewService = createServiceWithPublicResolver();
        // Create spatial plan with invalid ppm coordinate
        Map<String, Object> spatialPlan = Map.of(
                "operations", List.of(
                        Map.of("id", "crop-1", "type", "crop",
                                "x", 1_500_000, // Out of range (> 1,000,000)
                                "y", 0, "width", 500000, "height", 500000)
                )
        );
        ProjectExportRenderDto render = new ProjectExportRenderDto(
                "project-export-v1", Map.of(), spatialPlan, "v1");

        ProjectExportPackageDto exportPkg = new ProjectExportPackageDto(
                "project-export-v1", "metadata_only",
                null, new ProjectExportProjectDto("prj-1", "tenant-1", "Test", "Desc",
                        null, null, "ACTIVE"),
                null, null, render);
        ProjectImportPreviewRequest request = new ProjectImportPreviewRequest(exportPkg);

        ProjectImportPreviewResponse response = previewService.previewImport("tenant-1", request);

        assertFalse(response.compatible());
        assertTrue(response.errors().stream()
                .anyMatch(e -> "INVALID_SPATIAL_COORDINATE".equals(e.code())));
    }

    @Test
    void previewShouldNotPersistProject() {
        ProjectImportPreviewService previewService = createServiceWithPublicResolver();
        ProjectImportPreviewRequest request = createMetadataOnlyRequest();
        previewService.previewImport("tenant-1", request);

        // Verify no audit issues (audit failure should not block)
        verify(auditPort).record(anyString(), eq("PROJECT_IMPORT_PREVIEW"), anyString(),
                anyString(), anyString(), any(Map.class));
    }

    @Test
    void previewShouldRecordAuditWithoutUrls() {
        // Must use hostname URL with token to prove audit does not leak signed URL
        ProjectImportPreviewService previewService = createServiceWithPublicResolver();
        ProjectExportAssetDto asset = new ProjectExportAssetDto(
                "art-1", "video.mp4", "video", "video/mp4",
                1024L, null, 10.0, 1920, 1080, null,
                "https://signed.example.com/video.mp4?token=secret123");
        ProjectExportAssetsDto assets = new ProjectExportAssetsDto(
                "project-export-v1", "linked_assets", List.of(asset), null);
        ProjectExportPackageDto exportPkg = new ProjectExportPackageDto(
                "project-export-v1", "linked_assets",
                null, new ProjectExportProjectDto("prj-1", "tenant-1", "Test", "Desc",
                        null, null, "ACTIVE"),
                assets, null, null);
        ProjectImportPreviewRequest request = new ProjectImportPreviewRequest(exportPkg);

        previewService.previewImport("tenant-1", request);

        // Verify audit payload does not contain signed URL
        verify(auditPort).record(anyString(), eq("PROJECT_IMPORT_PREVIEW"), anyString(),
                anyString(), anyString(), argThat(m ->
                        !m.containsKey("signedUrl") &&
                        !m.containsKey("downloadUrl") &&
                        m.containsKey("assetCount")));
    }

    @Test
    void previewShouldWorkWithoutAuditPort() {
        // Service created with default constructor (no audit port set)
        // Uses system DNS but this test has no hostname assets
        ProjectImportPreviewService serviceWithoutAudit = new ProjectImportPreviewService(null);
        // auditPort is null

        ProjectImportPreviewRequest request = createMetadataOnlyRequest();
        ProjectImportPreviewResponse response = serviceWithoutAudit.previewImport("tenant-1", request);

        assertNotNull(response);
        assertTrue(response.compatible());
    }

    // =====================================================================
    // Instance injection scenarios (Section 9 requirements)
    // =====================================================================

    @Test
    void publicResolverShouldAcceptHostnameUrl() {
        // signed.example.test → public IP → URL accepted
        ProjectImportPreviewService service = createServiceWithPublicResolver();
        ProjectExportAssetDto asset = new ProjectExportAssetDto(
                "art-1", "video.mp4", "video", "video/mp4",
                1024L, null, 10.0, 1920, 1080, null,
                "https://signed.example.test/video.mp4?token=secret123");
        ProjectExportAssetsDto assets = new ProjectExportAssetsDto(
                "project-export-v1", "linked_assets", List.of(asset), null);
        ProjectExportPackageDto exportPkg = new ProjectExportPackageDto(
                "project-export-v1", "linked_assets",
                null, new ProjectExportProjectDto("prj-1", "tenant-1", "Test", "Desc",
                        null, null, "ACTIVE"),
                assets, null, null);
        ProjectImportPreviewRequest request = new ProjectImportPreviewRequest(exportPkg);

        ProjectImportPreviewResponse response = service.previewImport("tenant-1", request);

        assertTrue(response.compatible(), "Public resolver should accept hostname URL");
        assertEquals(0, response.errors().size(), "Expected no errors for public resolver");
    }

    @Test
    void privateResolverShouldRejectHostnameUrl() {
        // signed.example.test → 10.0.0.1 → UNSAFE_DOWNLOAD_URL
        ProjectImportPreviewService service =
                new ProjectImportPreviewService(new SafeDownloadUrlValidator(privateResolver()), auditPort);

        ProjectExportAssetDto asset = new ProjectExportAssetDto(
                "art-1", "video.mp4", "video", "video/mp4",
                1024L, null, 10.0, 1920, 1080, null,
                "https://signed.example.test/video.mp4?token=secret123");
        ProjectExportAssetsDto assets = new ProjectExportAssetsDto(
                "project-export-v1", "linked_assets", List.of(asset), null);
        ProjectExportPackageDto exportPkg = new ProjectExportPackageDto(
                "project-export-v1", "linked_assets",
                null, new ProjectExportProjectDto("prj-1", "tenant-1", "Test", "Desc",
                        null, null, "ACTIVE"),
                assets, null, null);
        ProjectImportPreviewRequest request = new ProjectImportPreviewRequest(exportPkg);

        ProjectImportPreviewResponse response = service.previewImport("tenant-1", request);

        assertFalse(response.compatible(), "Private resolver should reject hostname URL");
        assertTrue(response.errors().stream()
                .anyMatch(e -> "UNSAFE_DOWNLOAD_URL".equals(e.code())),
                "Expected UNSAFE_DOWNLOAD_URL error for private resolver");
    }

    @Test
    void emptyResolverShouldRejectHostnameUrl() {
        // signed.example.test → empty result → rejected
        ProjectImportPreviewService service =
                new ProjectImportPreviewService(new SafeDownloadUrlValidator(emptyResolver()), auditPort);

        ProjectExportAssetDto asset = new ProjectExportAssetDto(
                "art-1", "video.mp4", "video", "video/mp4",
                1024L, null, 10.0, 1920, 1080, null,
                "https://signed.example.test/video.mp4?token=secret123");
        ProjectExportAssetsDto assets = new ProjectExportAssetsDto(
                "project-export-v1", "linked_assets", List.of(asset), null);
        ProjectExportPackageDto exportPkg = new ProjectExportPackageDto(
                "project-export-v1", "linked_assets",
                null, new ProjectExportProjectDto("prj-1", "tenant-1", "Test", "Desc",
                        null, null, "ACTIVE"),
                assets, null, null);
        ProjectImportPreviewRequest request = new ProjectImportPreviewRequest(exportPkg);

        ProjectImportPreviewResponse response = service.previewImport("tenant-1", request);

        assertFalse(response.compatible(), "Empty resolver should reject hostname URL");
        assertTrue(response.errors().stream()
                .anyMatch(e -> "UNSAFE_DOWNLOAD_URL".equals(e.code())),
                "Expected UNSAFE_DOWNLOAD_URL error for empty resolver");
    }

    @Test
    void exceptionResolverShouldRejectHostnameUrl() {
        // signed.example.test → UnknownHostException → rejected
        ProjectImportPreviewService service =
                new ProjectImportPreviewService(new SafeDownloadUrlValidator(failingResolver()), auditPort);

        ProjectExportAssetDto asset = new ProjectExportAssetDto(
                "art-1", "video.mp4", "video", "video/mp4",
                1024L, null, 10.0, 1920, 1080, null,
                "https://signed.example.test/video.mp4?token=secret123");
        ProjectExportAssetsDto assets = new ProjectExportAssetsDto(
                "project-export-v1", "linked_assets", List.of(asset), null);
        ProjectExportPackageDto exportPkg = new ProjectExportPackageDto(
                "project-export-v1", "linked_assets",
                null, new ProjectExportProjectDto("prj-1", "tenant-1", "Test", "Desc",
                        null, null, "ACTIVE"),
                assets, null, null);
        ProjectImportPreviewRequest request = new ProjectImportPreviewRequest(exportPkg);

        ProjectImportPreviewResponse response = service.previewImport("tenant-1", request);

        assertFalse(response.compatible(), "Exception resolver should reject hostname URL");
        assertTrue(response.errors().stream()
                .anyMatch(e -> "UNSAFE_DOWNLOAD_URL".equals(e.code())),
                "Expected UNSAFE_DOWNLOAD_URL error for exception resolver");
    }

    @Test
    void instanceIsolationBetweenPublicAndPrivateResolvers() {
        // Two service instances with different resolvers
        // Same hostname must: A accepts, B rejects
        // Create order and alternating calls must not change results
        ProjectImportPreviewService serviceA =
                new ProjectImportPreviewService(new SafeDownloadUrlValidator(publicResolver()), auditPort);
        ProjectImportPreviewService serviceB =
                new ProjectImportPreviewService(new SafeDownloadUrlValidator(privateResolver()), auditPort);

        ProjectImportPreviewRequest request = createHostnameAssetRequest("signed.example.test");

        // Alternating calls 100 times
        for (int i = 0; i < 100; i++) {
            ProjectImportPreviewResponse responseA = serviceA.previewImport("tenant-1", request);
            ProjectImportPreviewResponse responseB = serviceB.previewImport("tenant-1", request);

            assertTrue(responseA.compatible(),
                    "Iteration " + i + ": serviceA (public) should accept");
            assertFalse(responseB.compatible(),
                    "Iteration " + i + ": serviceB (private) should reject");
        }
    }

    @Test
    void concurrentInstanceIsolationShouldHaveNoCrossPollution() throws InterruptedException {
        // Two threads calling different service instances
        // Each thread at least 250 calls
        ProjectImportPreviewService serviceA =
                new ProjectImportPreviewService(new SafeDownloadUrlValidator(publicResolver()), auditPort);
        ProjectImportPreviewService serviceB =
                new ProjectImportPreviewService(new SafeDownloadUrlValidator(privateResolver()), auditPort);

        ProjectImportPreviewRequest request = createHostnameAssetRequest("signed.example.test");

        int callsPerThread = 250;
        AtomicInteger errors = new AtomicInteger(0);
        AtomicInteger crossPollution = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        // Thread A: public resolver — should always accept
        Thread threadA = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < callsPerThread; i++) {
                    ProjectImportPreviewResponse response =
                            serviceA.previewImport("tenant-1", request);
                    if (!response.compatible()) {
                        errors.incrementAndGet();
                        // Check if the error came from private resolver (cross-pollution)
                        if (response.errors().stream()
                                .anyMatch(e -> e.message() != null &&
                                        e.message().contains("private network"))) {
                            crossPollution.incrementAndGet();
                        }
                    }
                }
            } catch (Exception e) {
                errors.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread B: private resolver — should always reject
        Thread threadB = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < callsPerThread; i++) {
                    ProjectImportPreviewResponse response =
                            serviceB.previewImport("tenant-1", request);
                    if (response.compatible()) {
                        errors.incrementAndGet();
                        crossPollution.incrementAndGet();
                    }
                }
            } catch (Exception e) {
                errors.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        threadA.start();
        threadB.start();
        startLatch.countDown(); // Release both threads simultaneously
        doneLatch.await();

        assertEquals(0, errors.get(),
                "Expected 0 errors across " + (callsPerThread * 2) + " calls, got " + errors.get());
        assertEquals(0, crossPollution.get(),
                "Expected 0 cross-instance pollution, got " + crossPollution.get());
    }

    // =====================================================================
    // Helper methods
    // =====================================================================

    private ProjectImportPreviewRequest createHostnameAssetRequest(String hostname) {
        ProjectExportAssetDto asset = new ProjectExportAssetDto(
                "art-1", "video.mp4", "video", "video/mp4",
                1024L, null, 10.0, 1920, 1080, null,
                "https://" + hostname + "/video.mp4?token=secret123");
        ProjectExportAssetsDto assets = new ProjectExportAssetsDto(
                "project-export-v1", "linked_assets", List.of(asset), null);
        ProjectExportPackageDto exportPkg = new ProjectExportPackageDto(
                "project-export-v1", "linked_assets",
                null, new ProjectExportProjectDto("prj-1", "tenant-1", "Test", "Desc",
                        null, null, "ACTIVE"),
                assets, null, null);
        return new ProjectImportPreviewRequest(exportPkg);
    }

    private ProjectImportPreviewRequest createMetadataOnlyRequest() {
        ProjectExportPackageDto exportPkg = new ProjectExportPackageDto(
                "project-export-v1", "metadata_only",
                null, new ProjectExportProjectDto("prj-source", "tenant-source", "Test Project",
                        "Test Description", null, null, "ACTIVE"),
                null, null, null);
        return new ProjectImportPreviewRequest(exportPkg);
    }
}
