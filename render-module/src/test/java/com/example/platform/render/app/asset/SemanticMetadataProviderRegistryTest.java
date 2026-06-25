package com.example.platform.render.app.asset;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.asset.semantic.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SemanticMetadataProviderRegistryTest {

    private SemanticMetadataProviderRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SemanticMetadataProviderRegistry();
    }

    @Test
    void shouldRegisterProvider() {
        var provider = new TestProvider("TestASR");
        registry.register(provider);
        assertEquals(1, registry.providerCount());
        assertTrue(registry.listProviders().contains("TestASR"));
    }

    @Test
    void shouldResolveMatchingProvider() {
        registry.register(new TestProvider("WhisperProvider"));
        var request = SemanticMetadataRequest.of("a1", "v1", "VIDEO", "s3://bucket/v.mp4");
        var resolved = registry.resolve(request);
        assertTrue(resolved.isPresent());
        assertEquals("WhisperProvider", resolved.get().providerName());
    }

    @Test
    void shouldReturnEmptyWhenNoProviderSupports() {
        var request = SemanticMetadataRequest.of("a1", "v1", "VIDEO", "s3://bucket/v.mp4");
        assertTrue(registry.resolve(request).isEmpty());
    }

    private record TestProvider(String providerName, SemanticCapability capability) implements SemanticMetadataProvider {
        TestProvider(String name) { this(name, SemanticCapability.ASR); }
        @Override
        public boolean supports(SemanticMetadataRequest request) { return true; }
        @Override
        public SemanticMetadataResult analyze(SemanticMetadataRequest request) {
            return SemanticMetadataResult.success(
                    AssetSemanticMetadata.empty(request.assetId(), request.assetVersion()),
                    providerName);
        }
    }
}
