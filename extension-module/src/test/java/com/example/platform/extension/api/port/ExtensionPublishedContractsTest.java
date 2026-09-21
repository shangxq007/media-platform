package com.example.platform.extension.api.port;

import com.example.platform.extension.app.*;
import com.example.platform.extension.domain.*;
import com.example.platform.extension.runtime.PluginRuntimeProviderBinding;
import com.example.platform.auditcontract.api.AuditPort;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExtensionPublishedContractsTest {
    @Test void publicToolCatalogKeepsValidationAndImmutableDefinitions() {
        ToolCatalog catalog=new ToolRegistry();
        assertThrows(IllegalArgumentException.class,()->catalog.registerExecutable("bad","../bin/tool"));
        catalog.registerExecutable("tool","/usr/bin/test-tool");
        List<ToolCapability> capabilities=new ArrayList<>(List.of(new ToolCapability("read", "Read")));
        var definition=new ToolDefinition("tool","Tool","test","/usr/bin/test-tool",capabilities,ToolExecutionSafetyPolicy.defaults());
        catalog.registerTool(definition);capabilities.clear();assertEquals(1,catalog.findTool("tool").orElseThrow().capabilities().size());
        assertEquals("/usr/bin/test-tool",catalog.resolveExecutable("tool"));assertTrue(catalog.isAllowedExecutable("/usr/bin/test-tool"));
        assertThrows(UnsupportedOperationException.class,()->catalog.findTool("tool").orElseThrow().capabilities().add(null));
        assertThrows(UnsupportedOperationException.class,()->catalog.listTools().clear());
        assertThrows(UnsupportedOperationException.class,()->catalog.validateEnvironment().tools().clear());
        assertThrows(IllegalArgumentException.class,()->catalog.registerTool(new ToolDefinition("denied","Denied","test","/usr/bin/unlisted",List.of(),ToolExecutionSafetyPolicy.defaults())));
        assertEquals(1,catalog.listTools().size());assertTrue(catalog.findTool("denied").isEmpty());
    }
    @Test void providerCallbackAndAuditFailureDoNotPublishOrReplaceABinding() {
        var audit=mock(AuditPort.class);var limiter=new ExtensionResourceLimiter(audit);
        var events=new ExtensionAuditService(audit);var owner=new ExtensionRegistryService(audit,events,limiter,new ExtensionRouter(audit));
        ProviderContributions registrations=owner;ExtensionQueries queries=owner;
        var first=binding("1.0.0");registrations.registerProviderExtension("test",first,ExtensionTrustLevel.FULLY_TRUSTED,"actor");
        var invalid=binding("2.0.0");when(invalid.resourceLimits()).thenThrow(new IllegalArgumentException("invalid limits"));
        assertThrows(IllegalArgumentException.class,()->registrations.registerProviderExtension("test",invalid,ExtensionTrustLevel.FULLY_TRUSTED,"actor"));
        assertSame(first,registrations.findProviderBinding("test"));assertEquals("1.0.0",queries.getExtension("test").orElseThrow().version());
        var auditFailure=new IllegalStateException("audit unavailable");doThrow(auditFailure).when(audit).record(any(),any(),any(),any(),any(),any());
        assertSame(auditFailure,assertThrows(IllegalStateException.class,()->registrations.registerProviderExtension("test",binding("2.0.0"),ExtensionTrustLevel.FULLY_TRUSTED,"actor")));
        assertSame(first,registrations.findProviderBinding("test"));assertEquals("1.0.0",queries.getExtension("test").orElseThrow().version());
        assertEquals(1,events.getEventsByExtension("test").size());
        reset(audit);registrations.registerProviderExtension("test",binding("2.0.0"),ExtensionTrustLevel.FULLY_TRUSTED,"actor");
        assertEquals("2.0.0",queries.getExtension("test").orElseThrow().version());assertThrows(UnsupportedOperationException.class,()->queries.listExtensions().clear());
        assertTrue(owner.unloadExtension("test","actor"));assertNull(registrations.findProviderBinding("test"));assertTrue(queries.listExtensions().isEmpty());
    }
    private PluginRuntimeProviderBinding binding(String version) {
        var binding=mock(PluginRuntimeProviderBinding.class);when(binding.version()).thenReturn(version);when(binding.providerType()).thenReturn("test");
        when(binding.resourceLimits()).thenReturn(ExtensionResourceLimits.DEFAULTS);return binding;
    }
}
