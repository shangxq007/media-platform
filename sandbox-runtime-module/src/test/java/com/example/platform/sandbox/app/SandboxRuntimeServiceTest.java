package com.example.platform.sandbox.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SandboxRuntimeServiceTest {

    private SandboxRuntimeService service;

    @BeforeEach
    void setUp() {
        service = new SandboxRuntimeService();
    }

    @Test
    void overviewReturnsDisabledStatus() {
        Map<String, Object> overview = service.overview();
        assertEquals("sandbox-runtime-module", overview.get("module"));
        assertEquals("disabled", overview.get("status"));
        assertNotNull(overview.get("description"));
    }

    @Test
    void executeReturnsDisabledResponse() {
        Map<String, Object> result = service.execute("print('hello')", "python", 5000L);
        assertEquals("disabled", result.get("status"));
        assertTrue(result.get("message").toString().contains("not enabled"));
    }

    @Test
    void isEnabledReturnsFalse() {
        assertFalse(service.isEnabled());
    }

    @Test
    void executeWithNullCodeReturnsDisabled() {
        Map<String, Object> result = service.execute(null, "wasm", 1000L);
        assertEquals("disabled", result.get("status"));
    }

    @Test
    void executeWithEmptyCodeReturnsDisabled() {
        Map<String, Object> result = service.execute("", "javascript", 5000L);
        assertEquals("disabled", result.get("status"));
    }

    @Test
    void overviewContainsModuleDescription() {
        Map<String, Object> overview = service.overview();
        String description = (String) overview.get("description");
        assertNotNull(description);
        assertFalse(description.isBlank());
    }
}
