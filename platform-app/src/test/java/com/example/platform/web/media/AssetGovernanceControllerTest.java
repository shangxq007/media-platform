package com.example.platform.web.media;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.artifact.app.ArtifactGcService;
import com.example.platform.render.app.cache.RenderCacheCleanupService;
import com.example.platform.render.app.timeline.TimelineAssetGcService;
import com.example.platform.security.AdminAuditHelper;
import com.example.platform.shared.audit.AdminAuditPublisher;
import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AssetGovernanceControllerTest {

    private GlobalAssetIntegrityService integrityService;
    private TimelineAssetGcService timelineGcService;
    private ArtifactGcService artifactGcService;
    private StorageBucketOrphanScanner bucketOrphanScanner;
    private StorageOrphanPurgeService orphanPurgeService;
    private RenderCacheCleanupService cacheCleanupService;
    private AdminAuditHelper auditHelper;
    private AssetGovernanceController controller;

    @BeforeEach
    void setUp() {
        integrityService = mock(GlobalAssetIntegrityService.class);
        timelineGcService = mock(TimelineAssetGcService.class);
        artifactGcService = mock(ArtifactGcService.class);
        bucketOrphanScanner = mock(StorageBucketOrphanScanner.class);
        orphanPurgeService = mock(StorageOrphanPurgeService.class);
        cacheCleanupService = mock(RenderCacheCleanupService.class);
        auditHelper = new AdminAuditHelper(mock(AdminAuditPublisher.class));
        controller = new AssetGovernanceController(
                integrityService, timelineGcService, artifactGcService,
                bucketOrphanScanner, orphanPurgeService, cacheCleanupService, auditHelper);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void runSegmentCacheCleanup_adminSucceeds() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addUserRole("ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(cacheCleanupService.runCleanup("tenant-a", "proj-1"))
                .thenReturn(new RenderCacheCleanupService.CleanupResult(5, 10, 3));

        var result = controller.runSegmentCacheCleanup("tenant-a", "proj-1", request, response);

        assertNotNull(result);
        assertEquals(200, response.getStatus());
        verify(cacheCleanupService).runCleanup("tenant-a", "proj-1");
    }

    @Test
    void runSegmentCacheCleanup_legacyJwtAdminSucceeds() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.roles", java.util.List.of("ADMIN"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(cacheCleanupService.runCleanup(null, null))
                .thenReturn(new RenderCacheCleanupService.CleanupResult(0, 0, 0));

        var result = controller.runSegmentCacheCleanup(null, null, request, response);

        assertNotNull(result);
        assertEquals(200, response.getStatus());
    }

    @Test
    void runSegmentCacheCleanup_nonAdmin_returns403() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addUserRole("USER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(IllegalStateException.class,
                () -> controller.runSegmentCacheCleanup("tenant-a", "proj-1", request, response));
        assertEquals(403, response.getStatus());
        verifyNoInteractions(cacheCleanupService);
    }

    @Test
    void runSegmentCacheCleanup_noRole_returns403() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(IllegalStateException.class,
                () -> controller.runSegmentCacheCleanup("tenant-a", "proj-1", request, response));
        assertEquals(403, response.getStatus());
    }

    @Test
    void runSegmentCacheCleanup_crossTenantRequiresAdmin() {
        // Non-admin should not be able to cleanup another tenant's cache
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addUserRole("USER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(IllegalStateException.class,
                () -> controller.runSegmentCacheCleanup("other-tenant", "proj-1", request, response));
        assertEquals(403, response.getStatus());
        verifyNoInteractions(cacheCleanupService);
    }
}
