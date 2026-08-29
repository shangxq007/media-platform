package com.example.platform.extension.runtime.internal;

import com.example.platform.shared.usage.CanonicalActorRef;
import com.example.platform.shared.usage.OperationRef;
import com.example.platform.extension.domain.PluginHealth;
import com.example.platform.extension.domain.PluginSelectionResult;
import com.example.platform.extension.runtime.ExecutionMode;
import com.example.platform.extension.runtime.PluginExecutionRequest;
import com.example.platform.extension.runtime.PluginRuntimeErrorCategory;
import com.example.platform.extension.runtime.PluginRuntimeExecutionException;
import com.example.platform.extension.runtime.ResourceRequirements;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginSelectionToRequestMapperTest {

    private static final CanonicalActorRef ACTOR = new CanonicalActorRef("u-1", "USER");
    private static final OperationRef OP = OperationRef.of("op-1", "attempt-1");
    private static final ResourceRequirements RES = ResourceRequirements.defaults();

    @Test
    void mapsSelectionToRequest() {
        PluginSelectionResult selection = new PluginSelectionResult(
                "plugin-whisper", "1.0.0", "cap-stt", "1", "media.audio", PluginHealth.State.HEALTHY);
        PluginExecutionRequest req = PluginSelectionToRequestMapper.toRequest(
                selection, "tenant-1", ACTOR, OP, null,
                ExecutionMode.TRUSTED_IN_PROCESS, Duration.ofSeconds(30), RES, java.util.Set.of());
        assertEquals("tenant-1", req.tenantId());
        assertEquals("cap-stt", req.capability());
        assertEquals("plugin-whisper", req.providerRef().providerId());
        assertEquals(ExecutionMode.TRUSTED_IN_PROCESS, req.executionMode());
        assertEquals(OP, req.operationRef());
    }

    @Test
    void capabilityWithoutBindingRejectsExplicitly() {
        // capabilityId present but pluginId null/blank => no executable binding
        PluginSelectionResult selection = new PluginSelectionResult(
                null, null, "cap-unknown", "1", null, PluginHealth.State.UNKNOWN);
        PluginRuntimeExecutionException ex = assertThrows(PluginRuntimeExecutionException.class,
                () -> PluginSelectionToRequestMapper.toRequest(
                        selection, "tenant-1", ACTOR, OP, null,
                        ExecutionMode.TRUSTED_IN_PROCESS, Duration.ofSeconds(30), RES, java.util.Set.of()));
        assertEquals(PluginRuntimeErrorCategory.CAPABILITY_UNSUPPORTED, ex.category());
        assertTrue(ex.getMessage().contains("no executable plugin runtime binding"));
    }
}
