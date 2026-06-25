package com.example.platform.render.app.asset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.outbox.app.PlatformCoordinationService;
import com.example.platform.outbox.domain.*;
import com.example.platform.shared.events.AssetPublishedEvent;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketplaceConsumerTest {

    private PlatformCoordinationService coordinator;
    private MarketplaceConsumer consumer;

    @BeforeEach
    void setUp() {
        coordinator = mock(PlatformCoordinationService.class);
        consumer = new MarketplaceConsumer(coordinator);
    }

    @Test
    void shouldCreateMarketplacePrepareJobOnAssetPublished() {
        PlatformJob job = new PlatformJob("j1", JobType.MARKETPLACE_PREPARE, "ASSET", "a1",
                "t1", "p1", JobStatus.PENDING, 3, 0, 0, 2, 0, 0,
                "{}", null, Instant.now(), Instant.now(), null);
        when(coordinator.createJob(any(), any(), any(), any(), any(), any())).thenReturn(job);

        consumer.onAssetPublished(new AssetPublishedEvent("a1", "v1", "VIDEO", "p1", "PUBLISHED"));

        verify(coordinator).createJob(eq(JobType.MARKETPLACE_PREPARE), eq("ASSET"), eq("a1"), any(), any(), any());
        verify(coordinator, times(2)).createTask(any(), any(), any(), any(), anyInt());
    }
}
