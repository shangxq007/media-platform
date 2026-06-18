package com.example.platform.shared.capability.registry;

import com.example.platform.shared.capability.*;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for capability registries.
 *
 * <p>These tests verify registry behavior without implementing runtime execution.</p>
 */
class CapabilityRegistriesTest {

    // ===== SystemActionRegistry Tests =====

    @Test
    void systemActionRegistryRegistersAndFindsAction() {
        InMemorySystemActionRegistry registry = new InMemorySystemActionRegistry();

        SystemAction action = createTestAction("render.create_job", "1.0.0");
        registry.register(action);

        assertTrue(registry.contains("render.create_job"));
        assertEquals(action, registry.findByKey("render.create_job").orElse(null));
    }

    @Test
    void systemActionRegistryRejectsDuplicateKey() {
        InMemorySystemActionRegistry registry = new InMemorySystemActionRegistry();

        SystemAction action1 = createTestAction("render.create_job", "1.0.0");
        SystemAction action2 = createTestAction("render.create_job", "2.0.0");

        registry.register(action1);

        CapabilityRegistryException ex = assertThrows(
            CapabilityRegistryException.class,
            () -> registry.register(action2)
        );

        assertEquals(CapabilityErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void systemActionRegistryRejectsBlankKey() {
        InMemorySystemActionRegistry registry = new InMemorySystemActionRegistry();

        SystemAction action = createTestAction("", "1.0.0");

        CapabilityRegistryException ex = assertThrows(
            CapabilityRegistryException.class,
            () -> registry.register(action)
        );

        assertEquals(CapabilityErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    void systemActionRegistryReturnsImmutableList() {
        InMemorySystemActionRegistry registry = new InMemorySystemActionRegistry();

        SystemAction action = createTestAction("render.create_job", "1.0.0");
        registry.register(action);

        List<SystemAction> list = registry.list();

        assertThrows(UnsupportedOperationException.class, () -> {
            list.add(createTestAction("media.generate_thumbnail", "1.0.0"));
        });
    }

    // ===== ExtensionPointRegistry Tests =====

    @Test
    void extensionPointRegistryRegistersAndFinds() {
        InMemoryExtensionPointRegistry registry = new InMemoryExtensionPointRegistry();

        ExtensionPoint ep = createTestExtensionPoint("ai.transcribe", "1.0.0");
        registry.register(ep);

        assertTrue(registry.contains("ai.transcribe", "1.0.0"));
        assertEquals(ep, registry.find("ai.transcribe", "1.0.0").orElse(null));
    }

    @Test
    void extensionPointRegistryRejectsDuplicateKeyVersion() {
        InMemoryExtensionPointRegistry registry = new InMemoryExtensionPointRegistry();

        ExtensionPoint ep1 = createTestExtensionPoint("ai.transcribe", "1.0.0");
        ExtensionPoint ep2 = createTestExtensionPoint("ai.transcribe", "1.0.0");

        registry.register(ep1);

        CapabilityRegistryException ex = assertThrows(
            CapabilityRegistryException.class,
            () -> registry.register(ep2)
        );

        assertEquals(CapabilityErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void extensionPointRegistryRejectsBlankKey() {
        InMemoryExtensionPointRegistry registry = new InMemoryExtensionPointRegistry();

        ExtensionPoint ep = createTestExtensionPoint("", "1.0.0");

        CapabilityRegistryException ex = assertThrows(
            CapabilityRegistryException.class,
            () -> registry.register(ep)
        );

        assertEquals(CapabilityErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    void extensionPointRegistryReturnsImmutableList() {
        InMemoryExtensionPointRegistry registry = new InMemoryExtensionPointRegistry();

        ExtensionPoint ep = createTestExtensionPoint("ai.transcribe", "1.0.0");
        registry.register(ep);

        List<ExtensionPoint> list = registry.list();

        assertThrows(UnsupportedOperationException.class, () -> {
            list.add(createTestExtensionPoint("ai.translate", "1.0.0"));
        });
    }

    // ===== ExtensionProviderRegistry Tests =====

    @Test
    void extensionProviderRegistryRegistersAndFinds() {
        InMemoryExtensionProviderRegistry registry = new InMemoryExtensionProviderRegistry();

        ExtensionProvider provider = createTestProvider("provider-1", Set.of("ai.transcribe"));
        registry.register(provider);

        assertTrue(registry.contains("provider-1"));
        assertEquals(provider, registry.findByProviderId("provider-1").orElse(null));
    }

    @Test
    void extensionProviderRegistryRejectsDuplicateId() {
        InMemoryExtensionProviderRegistry registry = new InMemoryExtensionProviderRegistry();

        ExtensionProvider provider1 = createTestProvider("provider-1", Set.of("ai.transcribe"));
        ExtensionProvider provider2 = createTestProvider("provider-1", Set.of("ai.translate"));

        registry.register(provider1);

        CapabilityRegistryException ex = assertThrows(
            CapabilityRegistryException.class,
            () -> registry.register(provider2)
        );

        assertEquals(CapabilityErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void extensionProviderRegistryRejectsBlankId() {
        InMemoryExtensionProviderRegistry registry = new InMemoryExtensionProviderRegistry();

        ExtensionProvider provider = createTestProvider("", Set.of("ai.transcribe"));

        CapabilityRegistryException ex = assertThrows(
            CapabilityRegistryException.class,
            () -> registry.register(provider)
        );

        assertEquals(CapabilityErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    void extensionProviderRegistryFindSupportingReturnsMatching() {
        InMemoryExtensionProviderRegistry registry = new InMemoryExtensionProviderRegistry();

        ExtensionProvider provider1 = createTestProvider("provider-1", Set.of("ai.transcribe", "ai.translate"));
        ExtensionProvider provider2 = createTestProvider("provider-2", Set.of("ai.translate"));
        ExtensionProvider provider3 = createTestProvider("provider-3", Set.of("media.generate_thumbnail"));

        registry.register(provider1);
        registry.register(provider2);
        registry.register(provider3);

        List<ExtensionProvider> supporting = registry.findSupporting("ai.translate");

        assertEquals(2, supporting.size());
        assertTrue(supporting.stream().anyMatch(p -> p.providerId().equals("provider-1")));
        assertTrue(supporting.stream().anyMatch(p -> p.providerId().equals("provider-2")));
    }

    @Test
    void extensionProviderRegistryReturnsImmutableList() {
        InMemoryExtensionProviderRegistry registry = new InMemoryExtensionProviderRegistry();

        ExtensionProvider provider = createTestProvider("provider-1", Set.of("ai.transcribe"));
        registry.register(provider);

        List<ExtensionProvider> list = registry.list();

        assertThrows(UnsupportedOperationException.class, () -> {
            list.add(createTestProvider("provider-2", Set.of("ai.translate")));
        });
    }

    @Test
    void extensionProviderRegistryDoesNotExecuteProvider() {
        InMemoryExtensionProviderRegistry registry = new InMemoryExtensionProviderRegistry();

        ExtensionProvider provider = createTestProvider("provider-1", Set.of("ai.transcribe"));
        registry.register(provider);

        // Registry should not invoke provider.invoke()
        // This test verifies that registration does not trigger execution
        assertTrue(registry.contains("provider-1"));
    }

    // ===== Helper Methods =====

    private SystemAction createTestAction(String key, String version) {
        return new SystemAction() {
            @Override public String actionKey() { return key; }
            @Override public String version() { return version; }
            @Override public String inputSchemaRef() { return null; }
            @Override public String outputSchemaRef() { return null; }
            @Override public Set<String> requiredPermissions() { return Set.of(); }
            @Override public Duration timeout() { return Duration.ofSeconds(30); }
            @Override public boolean isIdempotent() { return true; }
            @Override public CapabilityStability stability() { return CapabilityStability.STABLE; }
            @Override public String description() { return "Test action"; }
        };
    }

    private ExtensionPoint createTestExtensionPoint(String key, String version) {
        return new ExtensionPoint() {
            @Override public String key() { return key; }
            @Override public String version() { return version; }
            @Override public String inputSchemaRef() { return null; }
            @Override public String outputSchemaRef() { return null; }
            @Override public Set<String> requiredPermissions() { return Set.of(); }
            @Override public CapabilityStability stability() { return CapabilityStability.STABLE; }
            @Override public Duration timeout() { return Duration.ofSeconds(30); }
            @Override public Set<ProviderRuntimeType> allowedProviderTypes() { return Set.of(ProviderRuntimeType.BUILTIN); }
            @Override public String description() { return "Test extension point"; }
        };
    }

    private ExtensionProvider createTestProvider(String providerId, Set<String> supportedExtensionPoints) {
        return new ExtensionProvider() {
            @Override public String providerId() { return providerId; }
            @Override public String providerName() { return "Test Provider"; }
            @Override public Set<String> supportedExtensionPoints() { return supportedExtensionPoints; }
            @Override public ProviderCapabilities capabilities() {
                return new ProviderCapabilities(Set.of(), supportedExtensionPoints, 1024, 30000, false, false, false, false);
            }
            @Override public ProviderRuntimeType runtimeType() { return ProviderRuntimeType.BUILTIN; }
            @Override public boolean isEnabled() { return true; }
            @Override public InvocationResult invoke(InvocationContext context, String extensionPointKey, Object request) {
                throw new UnsupportedOperationException("Not implemented");
            }
        };
    }
}
