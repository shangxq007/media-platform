package com.example.platform.delivery.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DeliveryPathRendererTest {

    @Test
    void rendersTemplate() {
        String path = DeliveryPathRenderer.render(
                "{tenantId}/{projectId}/{jobId}/{filename}",
                DeliveryPathRenderer.vars("t1", "p1", "rj_1", "out.mp4"));
        assertEquals("t1/p1/rj_1/out.mp4", path);
    }
}
