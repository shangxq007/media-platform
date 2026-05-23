package com.example.platform.render.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RenderWorkerQueueServiceTest {

    @Test
    void enqueueAndPollNatron() {
        RenderWorkerQueueProperties props = new RenderWorkerQueueProperties();
        props.setEnabled(true);
        RenderWorkerQueueService service = new RenderWorkerQueueService(props);

        assertThat(service.enqueueNatron("job-1", "tenant-1", "natron_poc_1080p")).isTrue();
        assertThat(service.natronDepth()).isEqualTo(1);

        assertThat(service.pollNatron()).isPresent();
        assertThat(service.natronDepth()).isZero();
    }
}
