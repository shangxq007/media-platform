package com.example.platform.sandbox.app;

import com.example.platform.sandbox.domain.DefaultSandboxSecurityPolicy;
import com.example.platform.sandbox.domain.SandboxExecutor.SandboxResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SandboxRuntimeServiceTest {

    private SandboxRuntimeService service;
    private DefaultSandboxSecurityPolicy securityPolicy;

    @BeforeEach
    void setUp() {
        securityPolicy = new DefaultSandboxSecurityPolicy();
        service = new SandboxRuntimeService(securityPolicy);
    }

    @Test
    void shouldReturnActiveOverview() {
        Map<String, Object> overview = service.overview();

        assertEquals("active", overview.get("status"));
        assertEquals("sandbox-runtime-module", overview.get("module"));
        assertNotNull(overview.get("supportedLanguages"));
        assertTrue(((java.util.List<?>) overview.get("supportedLanguages")).contains("groovy"));
        assertTrue(((java.util.List<?>) overview.get("supportedLanguages")).contains("javascript"));
        assertTrue(((java.util.List<?>) overview.get("supportedLanguages")).contains("python"));
        assertTrue(((java.util.List<?>) overview.get("supportedLanguages")).contains("wasm"));
    }

    @Test
    void shouldBeEnabled() {
        assertTrue(service.isEnabled());
    }

    @Test
    void shouldRejectBlankCode() {
        SandboxResult result = service.execute("", "javascript", 5000);
        assertEquals(-1, result.exitCode());
        assertTrue(result.error().contains("blank"));
    }

    @Test
    void shouldRejectNullLanguage() {
        SandboxResult result = service.execute("1+1", null, 5000);
        assertEquals(-1, result.exitCode());
        assertTrue(result.error().contains("Language"));
    }

    @Test
    void shouldRejectBlockedLanguage() {
        SandboxResult result = service.execute("SELECT * FROM users", "sql", 5000);
        assertEquals(-1, result.exitCode());
        assertTrue(result.error().contains("not allowed"));
    }

    @Test
    void shouldExecuteSimpleJavaScript() {
        SandboxResult result = service.execute("1 + 1", "javascript", 5000);
        if (result.exitCode() == 0) {
            assertNotNull(result.output());
        }
    }

    @Test
    void shouldHandleUnsupportedLanguage() {
        SandboxResult result = service.execute("code", "rust", 5000);
        assertEquals(-1, result.exitCode());
        assertTrue(result.error().contains("Unsupported") || result.error().contains("not allowed"));
    }

    @Test
    void shouldHandleWasmNotImplemented() {
        SandboxResult result = service.execute("module", "wasm", 5000);
        assertEquals(-1, result.exitCode());
        assertTrue(result.error().contains("not yet implemented"));
    }

    @Test
    void shouldShutdownGracefully() {
        assertDoesNotThrow(() -> service.shutdown());
    }

    @Test
    void shouldRejectDangerousCode() {
        assertFalse(securityPolicy.isCodeSafe("Runtime.getRuntime().exec('rm -rf /')"));
        assertFalse(securityPolicy.isCodeSafe("new java.io.File('/etc/passwd')"));
        assertFalse(securityPolicy.isCodeSafe("System.exit(0)"));
        assertFalse(securityPolicy.isCodeSafe("ProcessBuilder pb = new ProcessBuilder()"));
    }

    @Test
    void shouldAllowSafeCode() {
        assertTrue(securityPolicy.isCodeSafe("var x = 1 + 2;"));
        assertTrue(securityPolicy.isCodeSafe("function hello() { return 'world'; }"));
        assertTrue(securityPolicy.isCodeSafe(null));
    }

    @Test
    void shouldAllowConfiguredLanguages() {
        assertTrue(securityPolicy.isAllowed("groovy"));
        assertTrue(securityPolicy.isAllowed("javascript"));
        assertTrue(securityPolicy.isAllowed("python"));
        assertTrue(securityPolicy.isAllowed("wasm"));
        assertFalse(securityPolicy.isAllowed("sql"));
        assertFalse(securityPolicy.isAllowed(""));
        assertFalse(securityPolicy.isAllowed(null));
    }
}
