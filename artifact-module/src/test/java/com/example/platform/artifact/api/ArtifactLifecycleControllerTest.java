package com.example.platform.artifact.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.platform.artifact.app.ArtifactGcService;
import com.example.platform.artifact.app.ArtifactLifecycleService;
import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ArtifactLifecycleControllerTest {

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void lifecycleOperationRejectsMissingTenantContext() {
        ArtifactLifecycleService lifecycle = mock(ArtifactLifecycleService.class);
        ArtifactGcService gc = mock(ArtifactGcService.class);
        ArtifactLifecycleController controller = new ArtifactLifecycleController(lifecycle, gc);

        assertThrows(IllegalStateException.class, () -> controller.deleteCheck("art-1"));
        verifyNoInteractions(lifecycle, gc);
    }

    @Test
    void lifecycleOperationRejectsBlankTenantContext() {
        ArtifactLifecycleService lifecycle = mock(ArtifactLifecycleService.class);
        ArtifactGcService gc = mock(ArtifactGcService.class);
        ArtifactLifecycleController controller = new ArtifactLifecycleController(lifecycle, gc);
        TenantContext.set(" ");

        assertThrows(IllegalStateException.class, () -> controller.tombstone("art-1"));
        verifyNoInteractions(lifecycle, gc);
    }
}
