package com.example.platform.extension.runtime.internal;

import com.example.platform.billing.usage.CanonicalActorRef;
import com.example.platform.billing.usage.OperationRef;
import com.example.platform.billing.usage.ProviderRef;
import com.example.platform.extension.app.ExtensionRegistryService;
import com.example.platform.extension.domain.ExtensionContext;
import com.example.platform.extension.domain.ExtensionExecutionException;
import com.example.platform.extension.domain.ExtensionResult;
import com.example.platform.extension.domain.ExtensionTrustLevel;
import com.example.platform.extension.domain.ProviderExtensionSPI;
import com.example.platform.extension.runtime.ExecutionMode;
import com.example.platform.extension.runtime.PluginExecutionRequest;
import com.example.platform.extension.runtime.PluginExecutionResult;
import com.example.platform.extension.runtime.PluginExecutionStatus;
import com.example.platform.extension.runtime.PluginRuntimeErrorCategory;
import com.example.platform.extension.runtime.ResourceRequirements;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProviderExtensionSpiRuntimeAdapterTest {

    private static final CanonicalActorRef ACTOR = new CanonicalActorRef("u-1", "USER");
    private static final OperationRef OP = OperationRef.of("op-1", "attempt-1");
    private static final ResourceRequirements RES = ResourceRequirements.defaults();

    private PluginExecutionRequest request(String providerId) {
        return new PluginExecutionRequest(
                "tenant-1", ACTOR, OP, "cap-1", new ProviderRef(providerId), null,
                ExecutionMode.TRUSTED_IN_PROCESS, Duration.ofSeconds(30), RES, java.util.Set.of());
    }

    @Test
    void missingBindingRejectedWithCapabilityUnsupported() {
        ExtensionRegistryService registry = mock(ExtensionRegistryService.class);
        when(registry.findSpiInstance("ghost")).thenReturn(null);
        ProviderExtensionSpiRuntimeAdapter adapter = new ProviderExtensionSpiRuntimeAdapter(registry);
        PluginExecutionResult result = adapter.execute(request("ghost"));
        assertEquals(PluginExecutionStatus.FAILED, result.status());
        assertEquals(PluginRuntimeErrorCategory.CAPABILITY_UNSUPPORTED, result.error().category());
    }

    @Test
    void successMapsToSucceeded() throws Exception {
        ProviderExtensionSPI spi = mock(ProviderExtensionSPI.class);
        when(spi.version()).thenReturn("1.0.0");
        when(spi.trustLevel()).thenReturn(ExtensionTrustLevel.FULLY_TRUSTED);
        when(spi.execute(org.mockito.ArgumentMatchers.any(ExtensionContext.class),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(ExtensionResult.success("{\"ok\":true}"));
        ExtensionRegistryService registry = mock(ExtensionRegistryService.class);
        when(registry.findSpiInstance("spi-a")).thenReturn(spi);
        ProviderExtensionSpiRuntimeAdapter adapter = new ProviderExtensionSpiRuntimeAdapter(registry);
        PluginExecutionResult result = adapter.execute(request("spi-a"));
        assertEquals(PluginExecutionStatus.SUCCEEDED, result.status());
        assertNull(result.error());
        assertTrue(result.output().toString().contains("ok"));
    }

    @Test
    void spiFailureMappedToCanonicalError() throws Exception {
        ProviderExtensionSPI spi = mock(ProviderExtensionSPI.class);
        when(spi.version()).thenReturn("1.0.0");
        when(spi.trustLevel()).thenReturn(ExtensionTrustLevel.SEMI_TRUSTED);
        when(spi.execute(org.mockito.ArgumentMatchers.any(ExtensionContext.class),
                org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new ExtensionExecutionException("spi-a", "EXT-408", "timed out"));
        ExtensionRegistryService registry = mock(ExtensionRegistryService.class);
        when(registry.findSpiInstance("spi-b")).thenReturn(spi);
        ProviderExtensionSpiRuntimeAdapter adapter = new ProviderExtensionSpiRuntimeAdapter(registry);
        PluginExecutionResult result = adapter.execute(request("spi-b"));
        assertEquals(PluginExecutionStatus.FAILED, result.status());
        assertNotNull(result.error());
        assertEquals(PluginRuntimeErrorCategory.TIMEOUT, result.error().category());
    }

    @Test
    void sdkExceptionNeverLeaksRaw() throws Exception {
        ProviderExtensionSPI spi = mock(ProviderExtensionSPI.class);
        when(spi.version()).thenReturn("1.0.0");
        when(spi.trustLevel()).thenReturn(ExtensionTrustLevel.SEMI_TRUSTED);
        when(spi.execute(org.mockito.ArgumentMatchers.any(ExtensionContext.class),
                org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("openai sdk exploded"));
        ExtensionRegistryService registry = mock(ExtensionRegistryService.class);
        when(registry.findSpiInstance("spi-c")).thenReturn(spi);
        ProviderExtensionSpiRuntimeAdapter adapter = new ProviderExtensionSpiRuntimeAdapter(registry);
        PluginExecutionResult result = adapter.execute(request("spi-c"));
        assertEquals(PluginExecutionStatus.FAILED, result.status());
        assertEquals(PluginRuntimeErrorCategory.EXECUTION_FAILED, result.error().category());
        assertTrue(!result.error().message().contains("IllegalStateException"));
    }
}
