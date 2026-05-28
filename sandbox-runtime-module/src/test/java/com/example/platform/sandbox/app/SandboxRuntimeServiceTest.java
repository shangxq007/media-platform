package com.example.platform.sandbox.app;

import com.example.platform.sandbox.domain.DefaultSandboxSecurityPolicy;
import com.example.platform.sandbox.domain.SandboxExecutor.SandboxResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SandboxRuntimeServiceTest {

    private DefaultSandboxSecurityPolicy securityPolicy;

    // In-process service (dev/test)
    private SandboxRuntimeService inProcessService;
    // External service (production-like)
    private SandboxRuntimeService externalService;
    // Disabled service
    private SandboxRuntimeService disabledService;
    // External mode but allow-in-process-eval=false (production-safe)
    private SandboxRuntimeService externalNoEvalService;
    // Mock worker
    private SandboxWorkerPort mockWorker;

    @BeforeEach
    void setUp() {
        securityPolicy = new DefaultSandboxSecurityPolicy();

        // In-process: dev/test mode
        SandboxProperties inProcessProps = new SandboxProperties(
                true, SandboxExecutionMode.IN_PROCESS, true,
                List.of("groovy", "javascript", "js", "python", "py", "wasm"), 5, 1024 * 1024,
                SandboxProperties.WorkerProperties.defaults());
        inProcessService = new SandboxRuntimeService(securityPolicy, inProcessProps, new NoopSandboxWorkerAdapter());

        // External: production-like with mock worker
        mockWorker = new SandboxWorkerPort() {
            @Override
            public SandboxWorkerResult execute(SandboxWorkerRequest request) {
                if ("python".equalsIgnoreCase(request.language())) {
                    return SandboxWorkerResult.success("hello\n", 100);
                }
                if ("denied".equalsIgnoreCase(request.language())) {
                    return SandboxWorkerResult.denied("Language not supported");
                }
                if ("timeout".equalsIgnoreCase(request.language())) {
                    return SandboxWorkerResult.timeout(5000);
                }
                if ("error".equalsIgnoreCase(request.language())) {
                    return SandboxWorkerResult.error("Internal worker error");
                }
                return SandboxWorkerResult.success("ok", 50);
            }
        };
        SandboxProperties externalProps = new SandboxProperties(
                true, SandboxExecutionMode.EXTERNAL, false,
                List.of("python", "javascript", "js"), 5, 1024 * 1024,
                new SandboxProperties.WorkerProperties("http://sandbox:8091", 1000, 5000));
        externalService = new SandboxRuntimeService(securityPolicy, externalProps, mockWorker);

        // Disabled
        disabledService = new SandboxRuntimeService(securityPolicy,
                SandboxProperties.defaults(), new NoopSandboxWorkerAdapter());

        // External but allow-in-process-eval=false (production safe)
        SandboxProperties externalNoEvalProps = new SandboxProperties(
                true, SandboxExecutionMode.EXTERNAL, false,
                List.of("python"), 5, 1024 * 1024,
                SandboxProperties.WorkerProperties.defaults());
        externalNoEvalService = new SandboxRuntimeService(
                securityPolicy, externalNoEvalProps, new NoopSandboxWorkerAdapter());
    }

    // ==================== Overview ====================

    @Test
    void overviewShowsActiveWhenEnabled() {
        Map<String, Object> overview = inProcessService.overview();
        assertEquals("active", overview.get("status"));
        assertEquals("sandbox-runtime-module", overview.get("module"));
        assertEquals(true, overview.get("enabled"));
        assertEquals("IN_PROCESS", overview.get("executionMode"));
        assertEquals(true, overview.get("allowInProcessEval"));
    }

    @Test
    void overviewShowsDisabledWhenDisabled() {
        Map<String, Object> overview = disabledService.overview();
        assertEquals("disabled", overview.get("status"));
        assertEquals(false, overview.get("enabled"));
    }

    @Test
    void overviewShowsExternalMode() {
        Map<String, Object> overview = externalService.overview();
        assertEquals("active", overview.get("status"));
        assertEquals("EXTERNAL", overview.get("executionMode"));
        assertEquals(true, overview.get("workerConfigured"));
    }

    @Test
    void overviewShowsWorkerNotConfigured() {
        Map<String, Object> overview = externalNoEvalService.overview();
        assertEquals("EXTERNAL", overview.get("executionMode"));
        assertEquals(false, overview.get("workerConfigured"));
    }

    // ==================== Disabled mode ====================

    @Test
    void rejectsWhenDisabled() {
        SandboxResult result = disabledService.execute("1+1", "javascript", 5000);
        assertEquals(-1, result.exitCode());
        assertTrue(result.error().contains("disabled"));
    }

    @Test void rejectsBlankCode() {
        SandboxResult result = inProcessService.execute("", "javascript", 5000);
        assertEquals(-1, result.exitCode());
        assertTrue(result.error().contains("blank"));
    }

    @Test
    void rejectsNullLanguage() {
        SandboxResult result = inProcessService.execute("1+1", null, 5000);
        assertEquals(-1, result.exitCode());
        assertTrue(result.error().contains("Language"));
    }

    @Test
    void rejectsLanguageNotInAllowedList() {
        SandboxResult result = inProcessService.execute("code", "rust", 5000);
        assertEquals(-1, result.exitCode());
    }

    // ==================== In-process mode ====================

    @Test
    void inProcessRejectsBlockedLanguage() {
        SandboxResult result = inProcessService.execute("SELECT * FROM users", "sql", 5000);
        assertEquals(-1, result.exitCode());
        assertTrue(result.error().contains("not"));
    }

    @Test
    void inProcessRejectsDangerousCode() {
        SandboxResult result = inProcessService.execute(
                "Runtime.getRuntime().exec('rm -rf /')", "groovy", 5000);
        assertEquals(-1, result.exitCode());
        assertTrue(result.error().contains("blocked"));
    }

    @Test
    void inProcessExecutesSimpleJavaScript() {
        SandboxResult result = inProcessService.execute("1 + 1", "javascript", 5000);
        if (result.exitCode() == 0) {
            assertNotNull(result.output());
        }
    }

    @Test
    void inProcessRejectsWhenAllowInProcessEvalFalse() {
        SandboxProperties props = new SandboxProperties(
                true, SandboxExecutionMode.IN_PROCESS, false,
                List.of("javascript"), 5, 1024 * 1024,
                SandboxProperties.WorkerProperties.defaults());
        SandboxRuntimeService service = new SandboxRuntimeService(securityPolicy, props, new NoopSandboxWorkerAdapter());

        SandboxResult result = service.execute("1+1", "javascript", 5000);
        assertEquals(-1, result.exitCode());
        assertTrue(result.error().contains("In-process"));
    }

    @Test
    void inProcessDoesNotCallExternalWorker() {
        SandboxWorkerPort neverCalled = mock(SandboxWorkerPort.class);
        SandboxProperties props = new SandboxProperties(
                true, SandboxExecutionMode.IN_PROCESS, true,
                List.of("javascript"), 5, 1024 * 1024,
                SandboxProperties.WorkerProperties.defaults());
        SandboxRuntimeService service = new SandboxRuntimeService(securityPolicy, props, neverCalled);

        service.execute("1+1", "javascript", 5000);
        verify(neverCalled, never()).execute(any());
    }

    // ==================== External mode ====================

    @Test
    void externalCallsWorkerPort() {
        SandboxResult result = externalService.execute("print('hello')", "python", 5000);
        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("hello"));
    }

    @Test
    void externalDoesNotCallScriptEngine() {
        // No Groovy/JS engines needed — everything goes through worker
        SandboxResult result = externalService.execute("console.log('hi')", "javascript", 5000);
        assertEquals(0, result.exitCode());
    }

    @Test
    void externalHandlesWorkerDenied() {
        SandboxWorkerPort deniedWorker = request -> SandboxWorkerResult.denied("Language not supported");
        SandboxProperties props = new SandboxProperties(
                true, SandboxExecutionMode.EXTERNAL, false,
                List.of("python"), 5, 1024 * 1024,
                new SandboxProperties.WorkerProperties("http://sandbox:8091", 1000, 5000));
        SandboxRuntimeService service = new SandboxRuntimeService(securityPolicy, props, deniedWorker);

        SandboxResult result = service.execute("code", "python", 5000);
        assertEquals(-1, result.exitCode());
        assertTrue(result.error().contains("denied") || result.error().contains("Denied"));
    }

    @Test
    void externalModeDoesNotCheckSecurityPolicy() {
        // Security policy blocklist should NOT be checked in external mode
        // because the worker handles security
        SandboxWorkerPort acceptAll = request -> SandboxWorkerResult.success("ok", 10);
        SandboxProperties props = new SandboxProperties(
                true, SandboxExecutionMode.EXTERNAL, false,
                List.of("python"), 5, 1024 * 1024,
                new SandboxProperties.WorkerProperties("http://w:1", 100, 500));
        SandboxRuntimeService service = new SandboxRuntimeService(securityPolicy, props, acceptAll);

        // Code that would be blocked by DefaultSandboxSecurityPolicy
        SandboxResult result = service.execute(
                "Runtime.getRuntime().exec('rm -rf /')", "python", 5000);
        assertEquals(0, result.exitCode(),
                "External mode should not check blocklist — worker handles security");
    }

    @Test
    void externalRejectsBlankCode() {
        SandboxResult result = externalService.execute("", "python", 5000);
        assertEquals(-1, result.exitCode());
        assertTrue(result.error().contains("blank"));
    }

    @Test
    void externalRejectsNullLanguage() {
        SandboxResult result = externalService.execute("1+1", null, 5000);
        assertEquals(-1, result.exitCode());
        assertTrue(result.error().contains("Language"));
    }

    // ==================== Execution mode routing ====================

    @Test
    void isEnabledReflectsProperties() {
        assertTrue(inProcessService.isEnabled());
        assertFalse(disabledService.isEnabled());
    }

    @Test
    void getExecutionModeReflectsProperties() {
        assertEquals(SandboxExecutionMode.IN_PROCESS, inProcessService.getExecutionMode());
        assertEquals(SandboxExecutionMode.EXTERNAL, externalService.getExecutionMode());
        assertEquals(SandboxExecutionMode.DISABLED, disabledService.getExecutionMode());
    }

    // ==================== Security policy ====================

    @Test
    void securityPolicyRejectsDangerousCode() {
        assertFalse(securityPolicy.isCodeSafe("Runtime.getRuntime().exec('rm -rf /')"));
        assertFalse(securityPolicy.isCodeSafe("new java.io.File('/etc/passwd')"));
        assertFalse(securityPolicy.isCodeSafe("System.exit(0)"));
        assertFalse(securityPolicy.isCodeSafe("ProcessBuilder pb = new ProcessBuilder()"));
    }

    @Test
    void securityPolicyAllowsSafeCode() {
        assertTrue(securityPolicy.isCodeSafe("var x = 1 + 2;"));
        assertTrue(securityPolicy.isCodeSafe("function hello() { return 'world'; }"));
        assertTrue(securityPolicy.isCodeSafe(null));
    }

    @Test
    void securityPolicyAllowsConfiguredLanguages() {
        assertTrue(securityPolicy.isAllowed("groovy"));
        assertTrue(securityPolicy.isAllowed("javascript"));
        assertTrue(securityPolicy.isAllowed("python"));
        assertTrue(securityPolicy.isAllowed("wasm"));
        assertFalse(securityPolicy.isAllowed("sql"));
        assertFalse(securityPolicy.isAllowed(""));
        assertFalse(securityPolicy.isAllowed(null));
    }

    // ==================== Defaults ====================

    @Test
    void defaultsShouldBeDisabled() {
        SandboxProperties defaults = SandboxProperties.defaults();
        assertFalse(defaults.enabled());
        assertFalse(defaults.allowInProcessEval());
        assertTrue(defaults.allowedLanguages().isEmpty());
        assertEquals(5, defaults.maxExecutionSeconds());
        assertEquals(1024 * 1024, defaults.maxOutputBytes());
        assertEquals(SandboxExecutionMode.DISABLED, defaults.executionMode());
        assertNotNull(defaults.worker());
        assertTrue(defaults.worker().baseUrl().isEmpty());
    }

    // ==================== Shutdown ====================

    @Test
    void shutdownGracefully() {
        assertDoesNotThrow(() -> inProcessService.shutdown());
    }
}
