package com.example.platform.render.app.asset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.outbox.app.TaskExecutionContext;
import com.example.platform.outbox.domain.*;
import com.example.platform.render.infrastructure.asset.AssetRepository;
import com.example.platform.render.infrastructure.asset.SearchProjectionRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketplaceValidationTest {

    private AssetRepository assetRepo;
    private SearchProjectionRepository projRepo;
    private MarketplaceValidateTaskHandler handler;

    @BeforeEach
    void setUp() {
        assetRepo = mock(AssetRepository.class);
        projRepo = mock(SearchProjectionRepository.class);
        handler = new MarketplaceValidateTaskHandler(assetRepo, projRepo);
    }

    @Test
    void shouldReportValidateCapability() {
        assertEquals(TaskCapability.VALIDATE, handler.capability());
    }

    @Test
    void shouldThrowWhenAssetNotFound() {
        when(assetRepo.findById(any(), eq("a1"))).thenReturn(Optional.empty());
        var ctx = createCtx("a1");
        assertThrows(IllegalStateException.class, () -> handler.execute(ctx));
    }

    private TaskExecutionContext createCtx(String assetId) {
        String payload = "{\"assetId\":\"" + assetId + "\",\"projectId\":\"p1\"}";
        PlatformJob job = new PlatformJob("j1", JobType.MARKETPLACE_PREPARE, "ASSET", assetId,
                "t1", "p1", JobStatus.RUNNING, 3, 0, 0, 2, 0, 0,
                payload, null, Instant.now(), Instant.now(), null);
        PlatformTask task = new PlatformTask("t1", "j1", "VALIDATE", TaskCapability.VALIDATE, null,
                TaskStatus.LEASED, 0, 3, null, null, null, 0,
                null, null, null, null);
        return TaskExecutionContext.of(job, task);
    }
}
