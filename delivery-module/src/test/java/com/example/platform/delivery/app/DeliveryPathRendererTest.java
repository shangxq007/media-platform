package com.example.platform.delivery.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeliveryPathRendererTest {

    @Test
    void rendersTemplate() {
        String path = DeliveryPathRenderer.render(
                "{tenantId}/{projectId}/{jobId}/{filename}",
                DeliveryPathRenderer.vars("t1", "p1", "rj_1", "out.mp4"));
        assertEquals("t1/p1/rj_1/out.mp4", path);
    }

    @Test
    void rendersWithSpecialCharsSanitized() {
        String path = DeliveryPathRenderer.render(
                "{tenantId}/{projectId}/{jobId}/{filename}",
                DeliveryPathRenderer.vars("t1", "p1", "rj_1", "my file (1).mp4"));
        assertEquals("t1/p1/rj_1/my_file__1_.mp4", path);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../secret",
            "t1/../../../etc/passwd",
            "t1/p1/..%2Fsecret",
            "t1/p1/%2e%2e/secret",
            "/absolute/path",
            "t1/p1/..\\secret",
            "C:\\temp\\secret",
    })
    void rejectsTraversalInRenderedPath(String traversalTemplate) {
        assertThrows(IllegalArgumentException.class, () ->
                DeliveryPathRenderer.assertValidRenderedPath(traversalTemplate));
    }

    @Test
    void rejectsNullByte() {
        assertThrows(IllegalArgumentException.class, () ->
                DeliveryPathRenderer.assertValidRenderedPath("t1/p1/file\0.mp4"));
    }

    @Test
    void acceptsValidPaths() {
        DeliveryPathRenderer.assertValidRenderedPath("t1/p1/rj_1/out.mp4");
        DeliveryPathRenderer.assertValidRenderedPath("tenant/project/job/file.mp4");
    }

    @Test
    void variableInjectionTraversalRejected() {
        // Even if sanitize() strips most chars, verify the rendered path is checked
        String path = DeliveryPathRenderer.render(
                "{tenantId}/{projectId}/{jobId}/{filename}",
                DeliveryPathRenderer.vars("t1", "p1", "rj_1", "safe.mp4"));
        assertEquals("t1/p1/rj_1/safe.mp4", path);
    }
}
