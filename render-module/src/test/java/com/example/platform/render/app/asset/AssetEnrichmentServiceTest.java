package com.example.platform.render.app.asset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.domain.asset.semantic.*;
import com.example.platform.render.infrastructure.asset.AssetSemanticMetadataRepository;
import com.example.platform.render.infrastructure.asset.provider.MockWhisperAsrProvider;
import com.example.platform.shared.authorization.AuthorizationDeniedException;
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
    void callerStorageUriCannotAuthorizeEnrichment() {
        AuthorizationDeniedException failure = assertThrows(AuthorizationDeniedException.class, () ->
                service.enrich("asset_1", "v1", "VIDEO", "s3://caller-controlled/v.mp4"));

        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
        verifyNoInteractions(repository);
    }

    @Test
    void absentProviderNeverPersistsComplete() {
        AuthorizationDeniedException failure = assertThrows(AuthorizationDeniedException.class, () ->
                service.enrich("asset_1", "v1", "VIDEO", null));

        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
        verifyNoInteractions(repository);
    }

    @Test
    void registeredProviderCannotBypassMissingArtifactAuthority() {
        var provider = mock(SemanticMetadataProvider.class);
        when(provider.providerName()).thenReturn("provider");
        when(provider.capability()).thenReturn(SemanticCapability.ASR);
        registry.register(provider);
        clearInvocations(provider);

        AuthorizationDeniedException failure = assertThrows(AuthorizationDeniedException.class, () ->
                service.enrichWith(SemanticCapability.ASR, "asset_1", "v1", "VIDEO", "s3://v.mp4"));

        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
        verifyNoInteractions(provider, repository);
    }
}
