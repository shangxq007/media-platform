package com.example.platform.workflow.temporal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppTemporalPropertiesTest {

    @Test
    void enabledDefaultsToFalse() {
        AppTemporalProperties props = new AppTemporalProperties();
        assertFalse(props.isEnabled());
    }

    @Test
    void resolveNamespaceUsesPrefixAndEnvironment() {
        AppTemporalProperties props = new AppTemporalProperties();
        props.setEnvironment("dev");
        assertEquals("media-platform-dev", props.resolveNamespace());
    }

    @Test
    void taskQueueDefaultsToRenderTaskQueueName() {
        assertEquals(RenderTaskQueue.NAME, new AppTemporalProperties().getTaskQueue());
    }

    @Test
    void workerRequiredDefaultsTrue() {
        assertTrue(new AppTemporalProperties().isWorkerRequired());
    }
}
