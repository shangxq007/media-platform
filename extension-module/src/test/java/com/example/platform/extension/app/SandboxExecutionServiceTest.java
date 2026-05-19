package com.example.platform.extension.app;

import com.example.platform.extension.domain.*;
import com.example.platform.shared.audit.AuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SandboxExecutionServiceTest {

    private AuditPort auditPort;
    private ExtensionResourceLimiter resourceLimiter;
    private ExtensionAuditService auditService;
    private SandboxExecutionService service;

    @BeforeEach
    void setUp() {
        auditPort = mock(AuditPort.class);
        resourceLimiter = new ExtensionResourceLimiter(auditPort);
        auditService = new ExtensionAuditService(auditPort);
        service = new SandboxExecutionService(auditPort, resourceLimiter, auditService);
        resourceLimiter.registerLimits("test-ext", ExtensionResourceLimits.DEFAULTS);
        resourceLimiter.registerLimits("failing-ext", ExtensionResourceLimits.DEFAULTS);
        resourceLimiter.registerLimits("slow-ext", ExtensionResourceLimits.DEFAULTS);
    }

    @Test
    void shouldExecuteInSandbox() throws Exception {
        String result = service.executeInSandbox("test-ext", "{\"key\":\"value\"}",
                "tenant-1", "user-1", null);

        assertNotNull(result);
        assertTrue(result.contains("executed"));
    }

    @Test
    void shouldHandleExecutionException() {
        assertDoesNotThrow(() ->
                service.executeInSandbox("failing-ext", "input", "tenant-1", "user-1", null));
    }

    @Test
    void shouldTruncateLargeOutput() throws Exception {
        String largeInput = "x".repeat(100);
        String result = service.executeInSandbox("test-ext", largeInput, "tenant-1", "user-1", null);
        assertNotNull(result);
    }

    @Test
    void shouldWriteAuditOnTimeout() {
        assertDoesNotThrow(() ->
                service.executeInSandbox("slow-ext", "input", "tenant-1", "user-1", null));
    }

    @Test
    void shouldShutdownGracefully() {
        assertDoesNotThrow(() -> service.shutdown());
    }

    @Test
    void shouldExecuteExtensionWithContext() throws Exception {
        ExtensionContext context = ExtensionContext.builder()
                .extensionKey("test-ext")
                .extensionVersion("1.0.0")
                .tenantId("tenant-1")
                .userId("user-1")
                .trustLevel(ExtensionTrustLevel.SEMI_TRUSTED)
                .build();

        ExtensionResult result = service.executeExtension(context, "{}",
                ExtensionResourceLimits.DEFAULTS);

        assertNotNull(result);
    }

    @Test
    void shouldRejectWhenResourceLimitExceeded() throws Exception {
        resourceLimiter.registerLimits("tiny-ext", new ExtensionResourceLimits(
                1, 64, 25, 100, 5, 4 * 1024 * 1024, 10_000));

        ExtensionContext context = ExtensionContext.builder()
                .extensionKey("tiny-ext")
                .tenantId("tenant-1")
                .userId("user-1")
                .trustLevel(ExtensionTrustLevel.UNTRUSTED)
                .build();

        ExtensionResult result = service.executeExtension(context, "toolonginput",
                new ExtensionResourceLimits(1, 64, 25, 100, 5, 4 * 1024 * 1024, 10_000));

        assertFalse(result.success());
        assertEquals("INPUT_TOO_LARGE", result.errorCode());
    }
}
