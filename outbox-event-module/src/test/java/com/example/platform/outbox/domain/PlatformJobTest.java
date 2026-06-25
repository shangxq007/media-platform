package com.example.platform.outbox.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PlatformJobTest {

    @Test
    void shouldDetectBarrierSatisfied() {
        PlatformJob job = new PlatformJob("j1", JobType.ASSET_ENRICHMENT, "ASSET", "a1",
                "t1", "p1", JobStatus.RUNNING, 0b111, 0b111, 0, 3, 3, 0,
                null, null, null, null, null);
        assertTrue(job.isBarrierSatisfied());
    }

    @Test
    void shouldDetectBarrierNotSatisfied() {
        PlatformJob job = new PlatformJob("j1", JobType.ASSET_ENRICHMENT, "ASSET", "a1",
                "t1", "p1", JobStatus.RUNNING, 0b111, 0b010, 0, 3, 1, 0,
                null, null, null, null, null);
        assertFalse(job.isBarrierSatisfied());
    }
}
