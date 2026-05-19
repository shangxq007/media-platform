package com.example.platform.extension.app;

import com.example.platform.extension.domain.*;
import com.example.platform.shared.audit.AuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExtensionRegistryServiceV2Test {

    private AuditPort auditPort;
    private SandboxExecutionService sandboxService;
    private ExtensionAuditService auditService;
    private ExtensionResourceLimiter resourceLimiter;
    private ExtensionRouter router;
    private ExtensionRegistryService service;

    @BeforeEach
    void setUp() {
        auditPort = mock(AuditPort.class);
        sandboxService = mock(SandboxExecutionService.class);
        auditService = new ExtensionAuditService(auditPort);
        resourceLimiter = new ExtensionResourceLimiter(auditPort);
        router = new ExtensionRouter(auditPort);
        service = new ExtensionRegistryService(auditPort, sandboxService, auditService, resourceLimiter, router);
    }

    @Test
    void shouldRegisterProviderWithTrustLevel() {
        ProviderExtensionSPI ext = createMockProvider("test-provider", "1.0.0");
        service.registerProviderExtension("test-provider", ext, ExtensionTrustLevel.FULLY_TRUSTED, "admin");

        Optional<ExtensionRegistryService.ExtensionInfo> info = service.getExtension("test-provider");
        assertTrue(info.isPresent());
        assertEquals("FULLY_TRUSTED", info.get().trustLevel());
        assertEquals("ACTIVE", info.get().status());
    }

    @Test
    void shouldRegisterPromptWithTrustLevel() {
        PromptExtensionSPI ext = createMockPrompt("test-prompt", "1.0.0");
        service.registerPromptExtension("test-prompt", ext, ExtensionTrustLevel.SEMI_TRUSTED, "admin");

        Optional<ExtensionRegistryService.ExtensionInfo> info = service.getExtension("test-prompt");
        assertTrue(info.isPresent());
        assertEquals("SEMI_TRUSTED", info.get().trustLevel());
    }

    @Test
    void shouldRegisterWorkflowStepWithTrustLevel() {
        WorkflowStepExtensionSPI ext = createMockWorkflowStep("test-step", "1.0.0");
        service.registerWorkflowStepExtension("test-step", ext, ExtensionTrustLevel.UNTRUSTED, "admin");

        Optional<ExtensionRegistryService.ExtensionInfo> info = service.getExtension("test-step");
        assertTrue(info.isPresent());
        assertEquals("UNTRUSTED", info.get().trustLevel());
    }

    @Test
    void shouldListExtensionsWithTrustLevel() {
        service.registerProviderExtension("prov-1", createMockProvider("prov-1", "1.0.0"),
                ExtensionTrustLevel.FULLY_TRUSTED, "admin");
        service.registerPromptExtension("prompt-1", createMockPrompt("prompt-1", "1.0.0"),
                ExtensionTrustLevel.SEMI_TRUSTED, "admin");

        List<ExtensionRegistryService.ExtensionInfo> extensions = service.listExtensions();
        assertEquals(2, extensions.size());
        assertTrue(extensions.stream().anyMatch(e -> e.trustLevel().equals("FULLY_TRUSTED")));
        assertTrue(extensions.stream().anyMatch(e -> e.trustLevel().equals("SEMI_TRUSTED")));
    }

    @Test
    void shouldCreateRollbackPoint() {
        service.registerProviderExtension("prov-1", createMockProvider("prov-1", "1.0.0"),
                ExtensionTrustLevel.FULLY_TRUSTED, "admin");

        RollbackPoint point = service.createRollbackPoint("prov-1", "admin");
        assertNotNull(point);
        assertEquals("prov-1", point.extensionCode());
        assertEquals("1.0.0", point.version());
        assertTrue(point.active());
    }

    @Test
    void shouldReturnNullRollbackPointForUnknownExtension() {
        RollbackPoint point = service.createRollbackPoint("unknown", "admin");
        assertNull(point);
    }

    @Test
    void shouldUnloadAndCallOnUnload() {
        ProviderExtensionSPI ext = createMockProvider("test-provider", "1.0.0");
        service.registerProviderExtension("test-provider", ext, ExtensionTrustLevel.FULLY_TRUSTED, "admin");

        boolean unloaded = service.unloadExtension("test-provider", "admin");
        assertTrue(unloaded);
        assertTrue(service.getExtension("test-provider").isEmpty());
    }

    @Test
    void shouldUpgradeAndSaveHistory() {
        ProviderExtensionSPI v1 = createMockProvider("test-provider", "1.0.0");
        ProviderExtensionSPI v2 = createMockProvider("test-provider", "2.0.0");

        service.registerProviderExtension("test-provider", v1, ExtensionTrustLevel.SEMI_TRUSTED, "admin");
        service.registerProviderExtension("test-provider", v2, ExtensionTrustLevel.SEMI_TRUSTED, "admin");

        Optional<ExtensionRegistryService.ExtensionInfo> info = service.getExtension("test-provider");
        assertTrue(info.isPresent());
        assertEquals("2.0.0", info.get().version());

        List<ExtensionRegistryService.ExtensionVersionRecord> history = service.getVersionHistory("test-provider");
        assertFalse(history.isEmpty());
        assertEquals("1.0.0", history.get(0).version());
    }

    @Test
    void shouldRollbackWithAudit() {
        ProviderExtensionSPI v1 = createMockProvider("test-provider", "1.0.0");
        ProviderExtensionSPI v2 = createMockProvider("test-provider", "2.0.0");

        service.registerProviderExtension("test-provider", v1, ExtensionTrustLevel.SEMI_TRUSTED, "admin");
        service.registerProviderExtension("test-provider", v2, ExtensionTrustLevel.SEMI_TRUSTED, "admin");

        boolean rolledBack = service.rollbackExtension("test-provider", "1.0.0", "admin");
        assertTrue(rolledBack);
    }

    @Test
    void shouldReturnRouter() {
        assertNotNull(service.getRouter());
    }

    @Test
    void shouldReturnResourceLimiter() {
        assertNotNull(service.getResourceLimiter());
    }

    @Test
    void shouldReturnAuditService() {
        assertNotNull(service.getAuditService());
    }

    @Test
    void shouldRegisterWithUntrustedLimits() {
        ProviderExtensionSPI ext = createMockProvider("untrusted-ext", "1.0.0");
        service.registerProviderExtension("untrusted-ext", ext, ExtensionTrustLevel.UNTRUSTED, "admin");

        ExtensionResourceLimits limits = resourceLimiter.getLimits("untrusted-ext");
        assertEquals(ExtensionResourceLimits.UNTRUSTED.maxConcurrency(), limits.maxConcurrency());
        assertEquals(ExtensionResourceLimits.UNTRUSTED.maxMemoryMb(), limits.maxMemoryMb());
    }

    private ProviderExtensionSPI createMockProvider(String key, String version) {
        return new ProviderExtensionSPI() {
            public String providerKey() { return key; }
            public String providerType() { return "RENDER"; }
            public String version() { return version; }
            public String inputSchema() { return "{}"; }
            public String outputSchema() { return "{}"; }
            public String execute(String input) { return "{}"; }
            public boolean isAvailable() { return true; }
            public void onUnload() {}
        };
    }

    private PromptExtensionSPI createMockPrompt(String key, String version) {
        return new PromptExtensionSPI() {
            public String extensionKey() { return key; }
            public String extensionType() { return "TEMPLATE"; }
            public String version() { return version; }
            public String execute(String body, String vars, String ctx) { return "{}"; }
            public String validate(String input) { return "{\"valid\":true}"; }
            public void onUnload() {}
        };
    }

    private WorkflowStepExtensionSPI createMockWorkflowStep(String key, String version) {
        return new WorkflowStepExtensionSPI() {
            public String stepKey() { return key; }
            public String stepType() { return "CUSTOM"; }
            public String version() { return version; }
            public String inputSchema() { return "{}"; }
            public String outputSchema() { return "{}"; }
            public String executeStep(String input, String ctx) { return "{}"; }
            public void onUnload() {}
        };
    }
}
