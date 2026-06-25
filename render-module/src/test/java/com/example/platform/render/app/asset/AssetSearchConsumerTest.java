package com.example.platform.render.app.asset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.outbox.app.PlatformCoordinationService;
import com.example.platform.outbox.domain.PlatformJob;
import com.example.platform.outbox.domain.JobType;
import com.example.platform.outbox.domain.JobStatus;
import com.example.platform.shared.events.AssetPublishedEvent;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetSearchConsumerTest {

    private PlatformCoordinationService coordinator;
    private AssetSearchConsumer consumer;

    @BeforeEach
    void setUp() {
        coordinator = mock(PlatformCoordinationService.class);
        consumer = new AssetSearchConsumer(coordinator);
    }

    @Test
    void shouldCreateReindexJobOnAssetPublished() {
        PlatformJob job = new PlatformJob("j1", JobType.SEARCH_REINDEX, "ASSET", "a1",
                "t1", "p1", JobStatus.PENDING, 1, 0, 0, 1, 0, 0,
                "{}", null, Instant.now(), Instant.now(), null);
        when(coordinator.createJob(any(), any(), any(), any(), any(), any())).thenReturn(job);

        consumer.onAssetPublished(new AssetPublishedEvent("a1", "v1", "VIDEO", "p1", "PUBLISHED"));

        verify(coordinator).createJob(eq(JobType.SEARCH_REINDEX), eq("ASSET"), eq("a1"), any(), any(), any());
        verify(coordinator).createTask(eq("j1"), eq("REINDEX"), any(), any(), eq(0));
    }
}
