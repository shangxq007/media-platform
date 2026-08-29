package com.example.platform.extension.runtime.internal;

import com.example.platform.extension.domain.PluginSelectionResult;
import com.example.platform.extension.runtime.CredentialRef;
import com.example.platform.extension.runtime.ExecutionMode;
import com.example.platform.extension.runtime.PluginExecutionRequest;
import com.example.platform.extension.runtime.PluginRuntimeErrorCategory;
import com.example.platform.extension.runtime.PluginRuntimeExecutionException;
import com.example.platform.extension.runtime.ResourceRequirements;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Maps a registry {@link PluginSelectionResult} into a canonical
 * {@link PluginExecutionRequest} (frozen handoff, PRV2-ADR-002).
 *
 * <p>If the matched capability has NO executable runtime binding the mapping
 * rejects explicitly with CAPABILITY_UNSUPPORTED — there is NO silent fallback
 * (PRV2-RED-001/002). The registry itself never invokes providers
 * (AR-PRV2-01).</p>
 */
public final class PluginSelectionToRequestMapper {

    private PluginSelectionToRequestMapper() {
    }

    /**
     * Builds a canonical execution request from a registry selection result.
     *
     * @param selection          registry selection result (matched capability + plugin)
     * @param tenantId           tenant (required)
     * @param actorRef           canonical actor (required)
     * @param operationRef       operation + attempt (required)
     * @param input              typed small input payload (nullable)
     * @param executionMode      requested execution mode
     * @param timeout            bounded timeout
     * @param resourceRequirements resource requirements
     * @param secretRefs         credential references only (empty when none)
     * @return canonical execution request bound to the selected provider
     * @throws PluginRuntimeExecutionException CAPABILITY_UNSUPPORTED when the selected
     *         plugin has no executable runtime binding
     */
    public static PluginExecutionRequest toRequest(
            PluginSelectionResult selection,
            String tenantId,
            com.example.platform.shared.usage.CanonicalActorRef actorRef,
            com.example.platform.shared.usage.OperationRef operationRef,
            Object input,
            ExecutionMode executionMode,
            Duration timeout,
            ResourceRequirements resourceRequirements,
            Set<CredentialRef> secretRefs) {

        Objects.requireNonNull(selection, "selection must not be null");
        Objects.requireNonNull(actorRef, "actorRef must not be null");
        Objects.requireNonNull(operationRef, "operationRef must not be null");
        Objects.requireNonNull(executionMode, "executionMode must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");

        if (selection.pluginId() == null || selection.pluginId().isBlank()) {
            throw new PluginRuntimeExecutionException(
                    PluginRuntimeErrorCategory.CAPABILITY_UNSUPPORTED,
                    "PRV2-404",
                    "Capability '" + selection.capabilityId()
                            + "' has no executable plugin runtime binding (no provider selected)");
        }

        return new PluginExecutionRequest(
                tenantId,
                actorRef,
                operationRef,
                selection.capabilityId(),
                new com.example.platform.shared.usage.ProviderRef(selection.pluginId()),
                input,
                executionMode,
                timeout,
                resourceRequirements != null ? resourceRequirements : ResourceRequirements.defaults(),
                secretRefs != null ? secretRefs : Set.of());
    }
}
