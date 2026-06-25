package com.example.platform.render.app.asset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.outbox.app.TaskExecutionContext;
import com.example.platform.outbox.domain.*;
import com.example.platform.render.infrastructure.asset.AssetRepository;
import com.example.platform.render.infrastructure.asset.AssetSemanticMetadataRepository;
import com.example.platform.render.infrastructure.asset.SearchProjectionRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchReindexTaskHandlerTest {

    private AssetRepository assetRepository;
    private AssetSemanticMetadataRepository semanticRepo;
    private SearchProjectionRepository projectionRepo;
    private SearchReindexTaskHandler handler;

    @BeforeEach
    void setUp() {
        assetRepository = mock(AssetRepository.class);
        semanticRepo = mock(AssetSemanticMetadataRepository.class);
        projectionRepo = mock(SearchProjectionRepository.class);
        handler = new SearchReindexTaskHandler(assetRepository, semanticRepo, projectionRepo);
    }

    @Test
    void shouldReportReindexCapability() {
        assertEquals(TaskCapability.REINDEX, handler.capability());
    }

    @Test
    void shouldExecuteReindexForAsset() {
        PlatformJob job = new PlatformJob("j1", JobType.SEARCH_REINDEX, "ASSET", "a1",
                null, null, JobStatus.RUNNING, 1, 0, 0, 1, 0, 0,
                "{\"assetId\":\"a1\"}", null, Instant.now(), Instant.now(), null);
        PlatformTask task = new PlatformTask("t1", "j1", "REINDEX", TaskCapability.REINDEX, null,
                TaskStatus.PENDING, 0, 3, null, null, null, 0,
                null, null, null, null);
        TaskExecutionContext ctx = TaskExecutionContext.of(job, task);

        assertDoesNotThrow(() -> handler.execute(ctx));
    }
}
