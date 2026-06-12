package com.example.platform.render.infrastructure.effects;

import com.example.platform.render.infrastructure.EffectMappingService;
import com.example.platform.render.infrastructure.EffectDescriptor;
import com.example.platform.render.infrastructure.EffectParameterSchema;
import com.example.platform.render.infrastructure.RenderProviderCapability;
import com.example.platform.render.infrastructure.RenderProviderRegistry;
import com.example.platform.render.infrastructure.ProviderStatus;
import com.example.platform.render.infrastructure.ProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EffectPolicyServiceTest {

    @Mock
    private EffectMappingService effectMapping;
    
    @Mock
    private RenderProviderRegistry providerRegistry;

    private EffectPolicyService policyService;

    @BeforeEach
    void setUp() {
        policyService = new EffectPolicyService(effectMapping, providerRegistry);
    }

    @Test
    void validateEffectForProvider_unknownEffect_returnsError() {
        when(effectMapping.getDescriptor("unknown.effect")).thenReturn(Optional.empty());

        var result = policyService.validateEffectForProvider("unknown.effect", "ffmpeg", "FREE");

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Unknown effect")));
    }

    @Test
    void validateEffectForProvider_unknownProvider_returnsError() {
        EffectDescriptor descriptor = new EffectDescriptor(
                "video.blur", "Blur", "video", "Gaussian blur",
                List.of(), List.of("ffmpeg"), Map.of(), List.of("FREE"), "filter", true);
        when(effectMapping.getDescriptor("video.blur")).thenReturn(Optional.of(descriptor));
        when(providerRegistry.getCapability("unknown")).thenReturn(Optional.empty());

        var result = policyService.validateEffectForProvider("video.blur", "unknown", "FREE");

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Unknown provider")));
    }

    @Test
    void validateEffectForProvider_effectNotSupportedByProvider_returnsError() {
        EffectDescriptor descriptor = new EffectDescriptor(
                "video.blur", "Blur", "video", "Gaussian blur",
                List.of(), List.of("ffmpeg"), Map.of(), List.of("FREE"), "filter", true);
        when(effectMapping.getDescriptor("video.blur")).thenReturn(Optional.of(descriptor));

        RenderProviderCapability capability = new RenderProviderCapability(
                "remotion", Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                "1080p", false, false, false, Set.of(),
                ProviderStatus.STUB, "P3", ProviderType.RENDER, "Remotion",
                List.of(), false);
        when(providerRegistry.getCapability("remotion")).thenReturn(Optional.of(capability));

        var result = policyService.validateEffectForProvider("video.blur", "remotion", "FREE");

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("does not support effect")));
    }

    @Test
    void validateEffectForProvider_tierNotAllowed_returnsError() {
        EffectDescriptor descriptor = new EffectDescriptor(
                "video.vignette", "Vignette", "video", "Vignette effect",
                List.of(), List.of("ffmpeg"), Map.of(), List.of("PRO", "TEAM"), "filter", true);
        when(effectMapping.getDescriptor("video.vignette")).thenReturn(Optional.of(descriptor));

        RenderProviderCapability capability = new RenderProviderCapability(
                "ffmpeg", Set.of(), Set.of(), Set.of("video.vignette"), Set.of(), Set.of(),
                "4k", false, false, false, Set.of(),
                ProviderStatus.PRODUCTION, "P0", ProviderType.RENDER, "FFmpeg",
                List.of(), true);
        when(providerRegistry.getCapability("ffmpeg")).thenReturn(Optional.of(capability));

        var result = policyService.validateEffectForProvider("video.vignette", "ffmpeg", "FREE");

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("requires tier")));
    }

    @Test
    void validateEffectForProvider_allValid_returnsSuccess() {
        EffectDescriptor descriptor = new EffectDescriptor(
                "video.blur", "Blur", "video", "Gaussian blur",
                List.of(), List.of("ffmpeg"), Map.of(), List.of("FREE"), "filter", true);
        when(effectMapping.getDescriptor("video.blur")).thenReturn(Optional.of(descriptor));

        RenderProviderCapability capability = new RenderProviderCapability(
                "ffmpeg", Set.of(), Set.of(), Set.of("video.blur"), Set.of(), Set.of(),
                "4k", false, false, false, Set.of(),
                ProviderStatus.PRODUCTION, "P0", ProviderType.RENDER, "FFmpeg",
                List.of(), true);
        when(providerRegistry.getCapability("ffmpeg")).thenReturn(Optional.of(capability));

        var result = policyService.validateEffectForProvider("video.blur", "ffmpeg", "FREE");

        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void validateEffectParameters_missingRequired_returnsError() {
        EffectDescriptor descriptor = new EffectDescriptor(
                "video.blur", "Blur", "video", "Gaussian blur",
                List.of(new EffectParameterSchema("radius", "float", null, 0.1, 10.0, "Blur radius")),
                List.of("ffmpeg"), Map.of(), List.of("FREE"), "filter", true);
        when(effectMapping.getDescriptor("video.blur")).thenReturn(Optional.of(descriptor));

        var result = policyService.validateEffectParameters("video.blur", Map.of());

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Missing required parameter")));
    }

    @Test
    void validateEffectParameters_outOfRange_returnsError() {
        EffectDescriptor descriptor = new EffectDescriptor(
                "video.blur", "Blur", "video", "Gaussian blur",
                List.of(new EffectParameterSchema("radius", "float", 2.0, 0.1, 10.0, "Blur radius")),
                List.of("ffmpeg"), Map.of(), List.of("FREE"), "filter", true);
        when(effectMapping.getDescriptor("video.blur")).thenReturn(Optional.of(descriptor));

        var result = policyService.validateEffectParameters("video.blur", Map.of("radius", 15.0));

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("must be <=")));
    }

    @Test
    void validateEffectParameters_validParams_returnsSuccess() {
        EffectDescriptor descriptor = new EffectDescriptor(
                "video.blur", "Blur", "video", "Gaussian blur",
                List.of(new EffectParameterSchema("radius", "float", 2.0, 0.1, 10.0, "Blur radius")),
                List.of("ffmpeg"), Map.of(), List.of("FREE"), "filter", true);
        when(effectMapping.getDescriptor("video.blur")).thenReturn(Optional.of(descriptor));

        var result = policyService.validateEffectParameters("video.blur", Map.of("radius", 5.0));

        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void getAvailableEffects_returnsMatchingEffects() {
        EffectDescriptor blur = new EffectDescriptor(
                "video.blur", "Blur", "video", "Gaussian blur",
                List.of(), List.of("ffmpeg"), Map.of(), List.of("FREE"), "filter", true);
        EffectDescriptor vignette = new EffectDescriptor(
                "video.vignette", "Vignette", "video", "Vignette effect",
                List.of(), List.of("ffmpeg"), Map.of(), List.of("PRO"), "filter", true);
        
        when(effectMapping.getAllDescriptors()).thenReturn(List.of(blur, vignette));

        var freeEffects = policyService.getAvailableEffects("ffmpeg", "FREE");
        assertEquals(1, freeEffects.size());
        assertEquals("video.blur", freeEffects.get(0).effectKey());

        var proEffects = policyService.getAvailableEffects("ffmpeg", "PRO");
        assertEquals(2, proEffects.size());
    }
}
