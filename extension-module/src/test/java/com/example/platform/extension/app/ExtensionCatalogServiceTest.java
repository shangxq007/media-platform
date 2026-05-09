package com.example.platform.extension.app;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionCatalogServiceTest {

    private final ExtensionCatalogService service = new ExtensionCatalogService();

    @Test
    void extensionCodesReturnsExpectedCodes() {
        List<String> codes = service.extensionCodes();
        assertNotNull(codes);
        assertFalse(codes.isEmpty());
        assertTrue(codes.contains("tool.ffprobe"));
        assertTrue(codes.contains("script.prompt_patch"));
        assertTrue(codes.contains("provider.publish.youtube"));
    }

    @Test
    void extensionCodesReturnsImmutableList() {
        List<String> codes = service.extensionCodes();
        assertThrows(UnsupportedOperationException.class, () -> codes.add("new.extension"));
    }

    @Test
    void extensionCodesContainsToolExtensions() {
        List<String> codes = service.extensionCodes();
        assertTrue(codes.stream().anyMatch(c -> c.startsWith("tool.")));
    }

    @Test
    void extensionCodesContainsScriptExtensions() {
        List<String> codes = service.extensionCodes();
        assertTrue(codes.stream().anyMatch(c -> c.startsWith("script.")));
    }

    @Test
    void extensionCodesContainsProviderExtensions() {
        List<String> codes = service.extensionCodes();
        assertTrue(codes.stream().anyMatch(c -> c.startsWith("provider.")));
    }
}
