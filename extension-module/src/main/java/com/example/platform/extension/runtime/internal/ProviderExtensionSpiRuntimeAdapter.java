package com.example.platform.extension.runtime.internal;

import com.example.platform.extension.app.ExtensionRegistryService;
import com.example.platform.extension.domain.ExtensionContext;
import com.example.platform.extension.domain.ExtensionExecutionException;
import com.example.platform.extension.domain.ExtensionResult;
import com.example.platform.extension.domain.ProviderExtensionSPI;
import com.example.platform.extension.runtime.PluginExecutionRequest;
import com.example.platform.extension.runtime.PluginExecutionResult;
import com.example.platform.extension.runtime.PluginExecutionStatus;
import com.example.platform.extension.runtime.PluginRuntimeError;
import com.example.platform.extension.runtime.PluginRuntimeErrorCategory;

import java.util.Map;
import java.util.Objects;

/**
 * Compatibility adapter from the existing {@link ProviderExtensionSPI} to the
 * Plugin Runtime V2 execution contract (PRV2-ADR-004, ADAPTER FIRST).
 *
 * <p>The SPI remains the compatibility surface; existing providers are NOT
 * required to migrate. All provider SDK exceptions are wrapped into canonical
 * runtime errors and never cross the public contract (AR-PRV2-08,
 * PRV2-RED-012).</p>
 */
public final class ProviderExtensionSpiRuntimeAdapter {

    private final ExtensionRegistryService registry;

    public ProviderExtensionSpiRuntimeAdapter(ExtensionRegistryService registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    /**
     * Executes the selected provider extension SPI synchronously.
     *
     * @param request canonical runtime request
     * @return canonical runtime result
     */
    public PluginExecutionResult execute(PluginExecutionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String providerId = request.providerRef().providerId();

        ProviderExtensionSPI spi = registry.findSpiInstance(providerId);
        if (spi == null) {
            return PluginExecutionResult.failed(
                    PluginRuntimeErrorCategory.CAPABILITY_UNSUPPORTED,
                    "PRV2-404",
                    "No executable provider runtime binding for '" + providerId + "'");
        }

        ExtensionContext context = ExtensionContext.builder()
                .extensionKey(providerId)
                .extensionVersion(spi.version())
                .tenantId(request.tenantId())
                .userId(request.actorRef().actorId())
                .traceId("prv2-" + request.operationRef().operationId())
                .trustLevel(spi.trustLevel())
                .build();

        String inputJson = request.input() != null ? request.input().toString() : "{}";

        try {
            ExtensionResult result = spi.execute(context, inputJson);
            return toRuntimeResult(result);
        } catch (ExtensionExecutionException e) {
            return PluginExecutionResult.failed(mapError(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            // SDK / native / HTTP exceptions are wrapped — never leaked (AR-PRV2-08)
            return PluginExecutionResult.failed(
                    PluginRuntimeErrorCategory.EXECUTION_FAILED,
                    "PRV2-500",
                    "Provider execution failed: " + safeMessage(e));
        }
    }

    private PluginExecutionResult toRuntimeResult(ExtensionResult result) {
        if (result == null) {
            return PluginExecutionResult.failed(
                    PluginRuntimeErrorCategory.EXECUTION_FAILED,
                    "PRV2-500",
                    "Provider returned null result");
        }
        if (result.success()) {
            Object observations = result.metrics().isEmpty() ? null : Map.copyOf(result.metrics());
            return PluginExecutionResult.succeeded(result.outputJson(), java.util.List.of(), observations);
        }
        return PluginExecutionResult.failed(mapError(result.errorCode(), result.errorMessage()));
    }

    private static PluginRuntimeError mapError(String errorCode, String message) {
        String code = errorCode != null ? errorCode : "PRV2-500";
        String msg = message != null ? message : "Provider execution failed";
        PluginRuntimeErrorCategory category;
        if (code != null && code.contains("404")) {
            category = PluginRuntimeErrorCategory.CAPABILITY_UNSUPPORTED;
        } else if (code != null && code.contains("408")) {
            category = PluginRuntimeErrorCategory.TIMEOUT;
        } else if (code != null && code.contains("429")) {
            category = PluginRuntimeErrorCategory.RATE_LIMITED;
        } else if (code != null && (code.contains("RESOURCE") || code.contains("LIMIT"))) {
            category = PluginRuntimeErrorCategory.RESOURCE_UNAVAILABLE;
        } else {
            category = PluginRuntimeErrorCategory.EXECUTION_FAILED;
        }
        return PluginRuntimeError.of(category, code, msg);
    }

    private static String safeMessage(Throwable t) {
        String m = t != null ? t.getMessage() : null;
        return m != null ? m : t != null ? t.getClass().getSimpleName() : "unknown";
    }

    /** Exposed for package-internal tests. */
    static PluginExecutionStatus statusOf(PluginRuntimeErrorCategory c) {
        return switch (c) {
            case TIMEOUT -> PluginExecutionStatus.TIMED_OUT;
            case CANCELLED -> PluginExecutionStatus.CANCELLED;
            default -> PluginExecutionStatus.FAILED;
        };
    }
}
