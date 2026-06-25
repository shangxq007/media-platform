package com.example.platform.render.app.asset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.outbox.app.TaskExecutionContext;
import com.example.platform.outbox.domain.*;
import com.example.platform.render.domain.asset.marketplace.*;
import com.example.platform.render.infrastructure.asset.MarketplaceListingRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketplacePackageTest {

    private MarketplaceListingBuilder builder;
    private MarketplaceListingRepository listingRepo;
    private MarketplacePackageTaskHandler handler;

    @BeforeEach
    void setUp() {
        builder = mock(MarketplaceListingBuilder.class);
        listingRepo = mock(MarketplaceListingRepository.class);
        handler = new MarketplacePackageTaskHandler(builder, listingRepo);
    }

    @Test
    void shouldReportPackageCapability() {
        assertEquals(TaskCapability.PACKAGE, handler.capability());
    }

    @Test
    void shouldBuildAndPersistListing() {
        MarketplaceListing draft = MarketplaceListing.draft("mlst_1", "a1", "t1", "p1",
                MarketplaceListingType.MEDIA, "test.mp4");
        when(builder.buildDraft(any(), any(), any())).thenReturn(draft);

        handler.execute(ctx("{\"assetId\":\"a1\",\"projectId\":\"p1\"}"));

        verify(listingRepo).upsert(draft);
    }

    private TaskExecutionContext ctx(String payload) {
        PlatformJob job = new PlatformJob("j1", JobType.MARKETPLACE_PREPARE, "ASSET", "a1",
                "t1", "p1", JobStatus.RUNNING, 3, 0, 0, 2, 0, 0,
                payload, null, Instant.now(), Instant.now(), null);
        PlatformTask task = new PlatformTask("t1", "j1", "PACKAGE", TaskCapability.PACKAGE, null,
                TaskStatus.LEASED, 0, 3, null, null, null, 1,
                null, null, null, null);
        return TaskExecutionContext.of(job, task);
    }
}
