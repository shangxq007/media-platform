package com.example.platform.artifact.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.app.ArtifactGcService;
import com.example.platform.artifact.app.ArtifactLifecycleService;
import com.example.platform.artifact.app.ArtifactProjectAuthorizationPort;
import com.example.platform.artifact.domain.ArtifactCatalogEntry;
import com.example.platform.artifact.domain.ArtifactStatus;
import com.example.platform.shared.web.TenantContext;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ArtifactLifecycleControllerTest {

    private final ArtifactLifecycleService lifecycle = mock(ArtifactLifecycleService.class);
    private final ArtifactGcService gc = mock(ArtifactGcService.class);
    private final ArtifactProjectAuthorizationPort projectAuthorization =
            mock(ArtifactProjectAuthorizationPort.class);
    private final ArtifactLifecycleController controller =
            new ArtifactLifecycleController(lifecycle, gc, projectAuthorization);

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void lifecycleOperationRejectsMissingTenantContext() {
        assertThrows(IllegalStateException.class, () -> controller.deleteCheck("art-1", "prj-1"));
        verifyNoInteractions(lifecycle, gc, projectAuthorization);
    }

    @Test
    void lifecycleOperationRejectsBlankTenantContext() {
        TenantContext.set(" ");

        assertThrows(IllegalStateException.class, () -> controller.tombstone("art-1", "prj-1"));
        verifyNoInteractions(lifecycle, gc, projectAuthorization);
    }

    @Test
    void deleteCheckAuthorizesProjectReadBeforeServiceAccess() {
        TenantContext.set("t1");

        controller.deleteCheck("art-1", "prj-1");

        InOrder order = inOrder(projectAuthorization, lifecycle);
        order.verify(projectAuthorization).requireRead("t1", "prj-1");
        order.verify(lifecycle).deleteCheck("t1", "art-1");
    }

    @Test
    void tombstoneAndGcAuthorizeProjectWriteBeforeServiceAccess() {
        TenantContext.set("t1");
        when(lifecycle.tombstone("t1", "art-1")).thenReturn(new ArtifactCatalogEntry(
                "art-1", "job-1", "prj-1", "mp4", "1920x1080", 1L, 10L, "sha",
                ArtifactStatus.TOMBSTONED, Instant.EPOCH, Instant.EPOCH));

        controller.tombstone("art-1", "prj-1");
        controller.runGc("prj-1", false, 7, 50);

        InOrder order = inOrder(projectAuthorization, lifecycle, gc);
        order.verify(projectAuthorization).requireWrite("t1", "prj-1");
        order.verify(lifecycle).tombstone("t1", "art-1");
        order.verify(projectAuthorization).requireWrite("t1", "prj-1");
        order.verify(gc).runGc("t1", "prj-1", 7, false, 50);
    }

    @Test
    void authorizationDenialPreventsServiceInteraction() {
        TenantContext.set("t1");
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "denied"))
                .when(projectAuthorization).requireRead("t1", "prj-1");

        assertThrows(ResponseStatusException.class, () -> controller.deleteCheck("art-1", "prj-1"));
        verifyNoInteractions(lifecycle, gc);
    }
}
