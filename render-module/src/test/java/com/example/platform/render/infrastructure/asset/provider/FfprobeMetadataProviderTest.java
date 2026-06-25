package com.example.platform.render.infrastructure.asset.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.asset.semantic.*;
import org.junit.jupiter.api.Test;

class FfprobeMetadataProviderTest {

    private final FfprobeMetadataProvider provider = new FfprobeMetadataProvider();

    @Test
    void shouldSupportStorageUri() {
        assertTrue(provider.supports(SemanticMetadataRequest.of("a1", "v1", "VIDEO", "s3://bucket/v.mp4")));
        assertFalse(provider.supports(SemanticMetadataRequest.of("a1", "v1", "VIDEO", null)));
    }

    @Test
    void shouldReportProbeCapability() {
        assertEquals(SemanticCapability.PROBE, provider.capability());
    }

    @Test
    void shouldReturnProviderName() {
        assertEquals("ffprobe", provider.providerName());
    }
}
