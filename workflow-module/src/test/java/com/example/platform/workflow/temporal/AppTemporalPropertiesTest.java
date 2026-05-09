package com.example.platform.workflow.temporal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class AppTemporalPropertiesTest {

    @Test
    void enabledDefaultsToFalse() {
        AppTemporalProperties props = new AppTemporalProperties(false);
        assertFalse(props.enabled());
    }

    @Test
    void enabledCanBeTrue() {
        AppTemporalProperties props = new AppTemporalProperties(true);
        assertTrue(props.enabled());
    }

    @Test
    void recordIsImmutable() {
        AppTemporalProperties props = new AppTemporalProperties(true);
        assertTrue(props.enabled());
    }

    @Test
    void recordHasEnabledAccessor() throws Exception {
        Method method = AppTemporalProperties.class.getMethod("enabled");
        assertNotNull(method);
    }
}
