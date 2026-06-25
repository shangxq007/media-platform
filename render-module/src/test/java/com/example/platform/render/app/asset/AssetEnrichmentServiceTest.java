package com.example.platform.render.app.asset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.domain.asset.semantic.*;
import com.example.platform.render.infrastructure.asset.AssetSemanticMetadataRepository;
import com.example.platform.render.infrastructure.asset.provider.MockWhisperAsrProvider;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetEnrichmentServiceTest {

    private SemanticMetadataProviderRegistry registry;
    private AssetSemanticMetadataRepository repository;
    private AssetEnrichmentService service;

    @BeforeEach
    void setUp() {
        registry = new SemanticMetadataProviderRegistry();
        repository = mock(AssetSemanticMetadataRepository.class);
        service = new AssetEnrichmentService(registry, repository);
    }

    @Test
    void shouldRegisterAndResolveAsrProvider() {
        var whisper = new MockWhisperAsrProvider();
        registry.register(whisper);

        assertEquals(1, registry.providerCount());
        assertTrue(registry.findFirst(SemanticCapability.ASR).isPresent());
    }

    @Test
    void shouldRegisterProviderAndRunEnrichment() {
        var whisper = new MockWhisperAsrProvider();
        registry.register(whisper);

        when(repository.findById("asset_1")).thenReturn(Optional.empty());

        AssetSemanticMetadata meta = service.enrich("asset_1", "v1", "VIDEO", "s3://v.mp4");

        assertNotNull(meta);
        assertEquals("asset_1", meta.assetId());
        assertEquals(AssetSemanticMetadata.EnrichmentStatus.COMPLETE, meta.status());
    }

    @Test
    void shouldHandleEmptyPipelineGracefully() {
        AssetSemanticMetadata meta = service.enrich("asset_1", "v1", "VIDEO", "s3://v.mp4");

        assertNotNull(meta);
        assertEquals(AssetSemanticMetadata.EnrichmentStatus.COMPLETE, meta.status());
        assertTrue(meta.transcripts().isEmpty());
    }

    @Test
    void shouldTrackStatusTransitions() {
        registry.register(new MockWhisperAsrProvider());

        when(repository.findById("asset_1")).thenReturn(Optional.empty());

        AssetSemanticMetadata meta = service.enrich("asset_1", "v1", "VIDEO", "s3://v.mp4");

        assertEquals(AssetSemanticMetadata.EnrichmentStatus.COMPLETE, meta.status());
    }
}
