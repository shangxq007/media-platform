package com.example.platform.render.infrastructure.font;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BasicFontStackResolverTest {

    private final BasicFontStackResolver resolver = new BasicFontStackResolver();

    @Test
    void resolverName() {
        assertEquals("BasicFontStackResolver", resolver.resolverName());
    }

    @Test
    void resolveReturnsRequestedFontWhenAvailable() {
        FontAsset asset = new FontAsset("font-1", "MyFont.ttf", "MyFont", "Regular",
                "ttf", 100000L, "abc123", "/fonts/MyFont.ttf",
                FontAssetStatus.READY, null, null, null);
        Map<String, FontAsset> available = Map.of("font-1", asset);

        FontStackResolver.FontStack stack = resolver.resolve("MyFont", available);

        assertEquals("MyFont", stack.primaryFont());
        assertTrue(stack.fallbackFonts().contains("MyFont"));
    }

    @Test
    void resolveUsesDefaultWhenFontNotFound() {
        FontStackResolver.FontStack stack = resolver.resolve("NonExistent", Map.of());

        assertEquals("NonExistent", stack.primaryFont());
        assertFalse(stack.fallbackFonts().isEmpty());
    }

    @Test
    void resolveWithNullFontFamily() {
        FontStackResolver.FontStack stack = resolver.resolve(null, Map.of());

        assertNotNull(stack.primaryFont());
        assertNotNull(stack.systemFallback());
    }

    @Test
    void resolveChainDetectsCjk() {
        // CJK codepoint: U+4E2D (中)
        Set<Integer> codepoints = Set.of(0x4E2D);

        FontStackResolver.FallbackChain chain = resolver.resolveChain("font-1", codepoints, Map.of());

        assertNotNull(chain);
        assertFalse(chain.fallbackFontIds().isEmpty());
    }

    @Test
    void resolveChainDetectsEmoji() {
        // Emoji codepoint: U+1F600 (😀)
        Set<Integer> codepoints = Set.of(0x1F600);

        FontStackResolver.FallbackChain chain = resolver.resolveChain("font-1", codepoints, Map.of());

        assertNotNull(chain);
        assertTrue(chain.systemFallbackUsed());
    }

    @Test
    void resolveChainHandlesEmptyCodepoints() {
        FontStackResolver.FallbackChain chain = resolver.resolveChain("font-1", Set.of(), Map.of());

        assertNotNull(chain);
        assertEquals("font-1", chain.primaryFontId());
    }

    @Test
    void deterministicOrdering() {
        FontStackResolver.FontStack stack1 = resolver.resolve("Font", Map.of());
        FontStackResolver.FontStack stack2 = resolver.resolve("Font", Map.of());

        assertEquals(stack1.primaryFont(), stack2.primaryFont());
        assertEquals(stack1.fallbackFonts(), stack2.fallbackFonts());
    }
}
