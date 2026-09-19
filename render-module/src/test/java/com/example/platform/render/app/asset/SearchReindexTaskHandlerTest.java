package com.example.platform.render.app.asset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.outbox.coordination.TaskExecutionContext;
import com.example.platform.outbox.coordination.*;
import com.example.platform.sandbox.execution.TaskCapability;
import com.example.platform.media.api.MediaAssets;
import com.example.platform.render.infrastructure.asset.AssetSemanticMetadataRepository;
import com.example.platform.render.infrastructure.asset.SearchProjectionRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchReindexTaskHandlerTest {

    private MediaAssets assetRepository;
    private AssetSemanticMetadataRepository semanticRepo;
    private SearchProjectionRepository projectionRepo;
    private SearchReindexTaskHandler handler;

    @BeforeEach
    void setUp() {
        assetRepository = mock(MediaAssets.class);
        semanticRepo = mock(AssetSemanticMetadataRepository.class);
        projectionRepo = mock(SearchProjectionRepository.class);
        handler = new SearchReindexTaskHandler(assetRepository, semanticRepo, projectionRepo);
    }

    @Test
    void shouldReportReindexCapability() {
        assertEquals(TaskCapability.REINDEX, handler.capability());
    }

    @Test
    void missingScopeCannotFallBackToSystem() {
        PlatformJob job = new PlatformJob("j1", JobType.SEARCH_REINDEX, "ASSET", "a1",
                null, null, JobStatus.RUNNING, 1, 0, 0, 1, 0, 0,
                "{\"assetId\":\"a1\"}", null, Instant.now(), Instant.now(), null);
        PlatformTask task = new PlatformTask("t1", "j1", "REINDEX", TaskCapability.REINDEX, null,
                TaskStatus.PENDING, 0, 3, null, null, null, 0,
                null, null, null, null);
        TaskExecutionContext ctx = TaskExecutionContext.of(job, task);

        assertThrows(IllegalArgumentException.class, () -> handler.execute(ctx));
        verifyNoInteractions(assetRepository, projectionRepo);
    }
    @Test
    void delayedPublicationReindexReadsCurrentLockedOwnerFacts() {
        var asset=mock(com.example.platform.media.api.Asset.class);
        when(asset.id()).thenReturn("a1");when(asset.tenantId()).thenReturn("tenant");when(asset.projectId()).thenReturn("project");
        when(asset.filename()).thenReturn("asset");when(asset.mediaType()).thenReturn("VIDEO");when(asset.publishStatus()).thenReturn("ARCHIVED");
        when(assetRepository.publicationSnapshot("tenant","project","a1")).thenReturn(asset);
        var job=new PlatformJob("j1",JobType.SEARCH_REINDEX,"ASSET","a1","tenant","project",JobStatus.RUNNING,1,0,0,1,0,0,
                "{\"assetId\":\"a1\",\"tenantId\":\"tenant\",\"projectId\":\"project\",\"reason\":\"asset.published\"}",null,Instant.now(),Instant.now(),null);
        handler.execute(new TaskExecutionContext("j1","t1",TaskCapability.REINDEX,job,null,job.payloadJson()));
        verify(projectionRepo).upsert(argThat(p->p.publishStatus().equals("ARCHIVED")&&p.tenantId().equals("tenant")&&p.projectId().equals("project")));
    }
}
