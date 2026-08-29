package com.example.platform.render.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class RenderProviderPropertiesTest {

    @Test
    void exposesOnlyUnaffectedProviderConfigurationComponents() {
        List<String> componentNames = Arrays.stream(RenderProviderProperties.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertEquals(List.of("ofx", "mock", "gstreamer", "gpac", "mlt"), componentNames);
        assertEquals(componentNames.size(), new HashSet<>(componentNames).size());
        assertFalse(componentNames.contains("provider"));
    }
}
