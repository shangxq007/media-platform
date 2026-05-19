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

class ExtensionRegistryServiceTest {

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
    void shouldRegisterProviderExtension() {
        ProviderExtensionSPI extension = createMockProvider("test-provider", "1.0.0");
        service.registerProviderExtension("test-provider", extension, ExtensionTrustLevel.FULLY_TRUSTED, "admin");

        Optional<ExtensionRegistryService.ExtensionInfo> info = service.getExtension("test-provider");
        assertTrue(info.isPresent());
        assertEquals("1.0.0", info.get().version());
        assertEquals("PROVIDER", info.get().category());
    }

    @Test
    void shouldRegisterPromptExtension() {
        PromptExtensionSPI extension = createMockPrompt("test-prompt", "1.0.0");
        service.registerPromptExtension("test-prompt", extension, ExtensionTrustLevel.SEMI_TRUSTED, "admin");

        Optional<ExtensionRegistryService.ExtensionInfo> info = service.getExtension("test-prompt");
        assertTrue(info.isPresent());
        assertEquals("PROMPT", info.get().category());
    }

    @Test
    void shouldRegisterWorkflowStepExtension() {
        WorkflowStepExtensionSPI extension = createMockWorkflowStep("test-step", "1.0.0");
        service.registerWorkflowStepExtension("test-step", extension, ExtensionTrustLevel.UNTRUSTED, "admin");

        Optional<ExtensionRegistryService.ExtensionInfo> info = service.getExtension("test-step");
        assertTrue(info.isPresent());
        assertEquals("WORKFLOW_STEP", info.get().category());
    }

    @Test
    void shouldListAllExtensions() {
        service.registerProviderExtension("prov-1", createMockProvider("prov-1", "1.0.0"), ExtensionTrustLevel.FULLY_TRUSTED, "admin");
        service.registerPromptExtension("prompt-1", createMockPrompt("prompt-1", "1.0.0"), ExtensionTrustLevel.SEMI_TRUSTED, "admin");
        service.registerWorkflowStepExtension("step-1", createMockWorkflowStep("step-1", "1.0.0"), ExtensionTrustLevel.UNTRUSTED, "admin");

        List<ExtensionRegistryService.ExtensionInfo> extensions = service.listExtensions();
        assertEquals(3, extensions.size());
    }

    @Test
    void shouldUnloadExtension() {
        service.registerProviderExtension("test-provider", createMockProvider("test-provider", "1.0.0"), ExtensionTrustLevel.FULLY_TRUSTED, "admin");
        assertTrue(service.getExtension("test-provider").isPresent());

        boolean unloaded = service.unloadExtension("test-provider", "admin");
        assertTrue(unloaded);
        assertTrue(service.getExtension("test-provider").isEmpty());
    }

    @Test
    void shouldReturnFalseWhenUnloadingNonExistentExtension() {
        boolean unloaded = service.unloadExtension("non-existent", "admin");
        assertFalse(unloaded);
    }

    @Test
    void shouldUpgradeExtensionAndSaveHistory() {
        ProviderExtensionSPI v1 = createMockProvider("test-provider", "1.0.0");
        ProviderExtensionSPI v2 = createMockProvider("test-provider", "2.0.0");

        service.registerProviderExtension("test-provider", v1, ExtensionTrustLevel.SEMI_TRUSTED, "admin");
        service.registerProviderExtension("test-provider", v2, ExtensionTrustLevel.SEMI_TRUSTED, "admin");

        Optional<ExtensionRegistryService.ExtensionInfo> info = service.getExtension("test-provider");
        assertTrue(info.isPresent());
        assertEquals("2.0.0", info.get().version());

        List<ExtensionRegistryService.ExtensionVersionRecord> history = service.getVersionHistory("test-provider");
        assertFalse(history.isEmpty());
    }

    @Test
    void shouldRollbackExtension() {
        ProviderExtensionSPI v1 = createMockProvider("test-provider", "1.0.0");
        ProviderExtensionSPI v2 = createMockProvider("test-provider", "2.0.0");

        service.registerProviderExtension("test-provider", v1, ExtensionTrustLevel.SEMI_TRUSTED, "admin");
        service.registerProviderExtension("test-provider", v2, ExtensionTrustLevel.SEMI_TRUSTED, "admin");

        boolean rolledBack = service.rollbackExtension("test-provider", "1.0.0", "admin");
        assertTrue(rolledBack);
    }

    @Test
    void shouldReturnFalseWhenRollingBackToNonExistentVersion() {
        ProviderExtensionSPI v1 = createMockProvider("test-provider", "1.0.0");
        service.registerProviderExtension("test-provider", v1, ExtensionTrustLevel.SEMI_TRUSTED, "admin");

        boolean rolledBack = service.rollbackExtension("test-provider", "non-existent", "admin");
        assertFalse(rolledBack);
    }

    @Test
    void shouldThrowOnNullKey() {
        assertThrows(IllegalArgumentException.class, () ->
                service.registerProviderExtension(null, createMockProvider("x", "1.0"), ExtensionTrustLevel.FULLY_TRUSTED, "admin"));
    }

    @Test
    void shouldThrowOnEmptyKey() {
        assertThrows(IllegalArgumentException.class, () ->
                service.registerProviderExtension("", createMockProvider("x", "1.0"), ExtensionTrustLevel.FULLY_TRUSTED, "admin"));
    }

    @Test
    void shouldThrowOnNullExtension() {
        assertThrows(IllegalArgumentException.class, () ->
                service.registerProviderExtension("test", null, ExtensionTrustLevel.FULLY_TRUSTED, "admin"));
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
