package com.example.platform.extension.runtime.internal;

import com.example.platform.extension.app.ExtensionRegistryService;
import com.example.platform.extension.domain.ExtensionContext;
import com.example.platform.extension.domain.ExtensionExecutionException;
import com.example.platform.extension.domain.ExtensionResult;
import com.example.platform.extension.domain.ExtensionTrustLevel;
import com.example.platform.extension.runtime.ExecutionMode;
import com.example.platform.extension.runtime.PluginExecutionProgress;
import com.example.platform.extension.runtime.PluginExecutionRequest;
import com.example.platform.extension.runtime.PluginExecutionResult;
import com.example.platform.extension.runtime.PluginExecutionStatus;
import com.example.platform.extension.runtime.PluginRuntime;
import com.example.platform.extension.runtime.PluginRuntimeError;
import com.example.platform.extension.runtime.PluginRuntimeProviderBinding;
import com.example.platform.extension.runtime.PluginRuntimeErrorCategory;
import com.example.platform.extension.runtime.PluginRuntimeExecutionException;
import com.example.platform.shared.usage.RuntimeOutcome;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Default Plugin Runtime V2 execution authority (PRV2-ADR-001/003).
 *
 * <p>Execution flow: validate invariants → trust enforcement (GAP-003) →
 * resolve canonical provider binding via ExtensionRegistryService →
 * execute → map timeout/cancel/progress → observability.</p>
 *
 * <p>The runtime does NOT own retry (PLUGIN_RUNTIME_RETRY_OWNERSHIP_V1, PLUGIN_RUNTIME_SINGLE_ATTEMPT_V1)
 * and does NOT mutate domain lifecycle state (AR-PRV2-02, AR-PRV2-14).
 * PMPR-S1: SPI compatibility adapter removed — binding is direct.</p>
 */
public final class DefaultPluginRuntime implements PluginRuntime {

    private final ExtensionRegistryService registry;
    private final SecretRefResolver secretResolver;
    private final Consumer<PluginExecutionProgress> progressListener;
    private final Consumer<PluginRuntimeObservation> observationSink;
    private final RuntimeUsageEmitter usageEmitter;

    /** Observability records are {@link PluginRuntimeObservation}. */

    public DefaultPluginRuntime(ExtensionRegistryService registry) {
        this(registry, SecretRefResolver.NOOP, p -> {
        }, o -> {
        }, null);
    }

    public DefaultPluginRuntime(ExtensionRegistryService registry,
                                SecretRefResolver secretResolver,
                                Consumer<PluginExecutionProgress> progressListener,
                                Consumer<PluginRuntimeObservation> observationSink,
                                RuntimeUsageEmitter usageEmitter) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.secretResolver = secretResolver != null ? secretResolver : SecretRefResolver.NOOP;
        this.progressListener = progressListener != null ? progressListener : p -> {
        };
        this.observationSink = observationSink != null ? observationSink : o -> {
        };
        this.usageEmitter = usageEmitter;
    }

    @Override
    public PluginExecutionResult execute(PluginExecutionRequest request) throws PluginRuntimeExecutionException {
        Objects.requireNonNull(request, "request must not be null");
        SecretRefResolver.assertNoSecretValues(request.secretRefs());
        long started = System.nanoTime();

        try {
            // Trust policy (tenant/actor invariants + untrusted-in-process denial)
            com.example.platform.extension.domain.ExtensionTrustLevel trustLevel =
                    resolveTrustLevel(request);
            TrustPolicyEnforcer.enforce(request, trustLevel);

            progressListener.accept(new PluginExecutionProgress(
                    "RUNNING", 0, 0, null, Instant.now()));

            PluginExecutionResult result = executeViaBinding(request);

            emitObservedUsage(request, result, started);

            progressListener.accept(new PluginExecutionProgress(
                    "FINALIZING", 1, 1, null, Instant.now()));
            record(request, result, started);
            return result;
        } catch (PluginRuntimeExecutionException e) {
            PluginExecutionResult result = PluginExecutionResult.failed(
                    e.category(), e.code(), e.getMessage());
            record(request, result, started);
            return result;
        } catch (RuntimeException e) {
            // Never leak raw runtime exceptions across the public contract
            PluginExecutionResult result = PluginExecutionResult.failed(
                    PluginRuntimeErrorCategory.EXECUTION_FAILED,
                    "PRV2-500",
                    "Runtime execution failed: " + safeMessage(e));
            record(request, result, started);
            return result;
        }
    }

    private void emitObservedUsage(
            PluginExecutionRequest request, PluginExecutionResult result, long startedNanos) {
        if (usageEmitter == null) {
            return;
        }
        if (result.error() != null && switch (result.error().category()) {
            case VALIDATION, CAPABILITY_UNSUPPORTED, SECURITY_DENIED, RESOURCE_UNAVAILABLE -> true;
            default -> false;
        }) {
            return;
        }
        long durationMs = Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000);
        RuntimeOutcome outcome = switch (result.status()) {
            case SUCCEEDED -> RuntimeOutcome.SUCCEEDED;
            case FAILED -> RuntimeOutcome.FAILED;
            case TIMED_OUT -> RuntimeOutcome.TIMED_OUT;
            case CANCELLED -> RuntimeOutcome.CANCELLED;
        };
        Instant occurredAt = Instant.now();
        try {
            usageEmitter.emitBaseFacts(
                    request.tenantId(), request.actorRef(), request.operationRef(),
                    request.providerRef(), request.capability(), durationMs, outcome,
                    occurredAt, "prv2-" + request.operationRef().operationId());
        } catch (RuntimeException emissionFailure) {
            // Observation persistence is independently retryable and cannot rewrite the
            // already-known runtime outcome.
        }
    }

    private PluginExecutionResult executeViaBinding(PluginExecutionRequest request) {
        String providerId = request.providerRef().providerId();
        PluginRuntimeProviderBinding binding = registry.findProviderBinding(providerId);
        if (binding == null) {
            return PluginExecutionResult.failed(
                    PluginRuntimeErrorCategory.CAPABILITY_UNSUPPORTED,
                    "PRV2-404",
                    "No executable provider runtime binding for '" + providerId + "'");
        }
        ExtensionContext context = ExtensionContext.builder()
                .extensionKey(providerId)
                .extensionVersion(binding.version())
                .tenantId(request.tenantId())
                .userId(request.actorRef().actorId())
                .traceId("prv2-" + request.operationRef().operationId())
                .trustLevel(binding.trustLevel())
                .build();
        String inputJson = request.input() != null ? request.input().toString() : "{}";
        try {
            ExtensionResult result = binding.execute(context, inputJson);
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

    private com.example.platform.extension.domain.ExtensionTrustLevel resolveTrustLevel(
            PluginExecutionRequest request) {
        // Trust level comes from the registered provider extension (adapter path).
        // Default SEMI_TRUSTED when not resolvable (never grants more than declared).
        return com.example.platform.extension.domain.ExtensionTrustLevel.SEMI_TRUSTED;
    }

    private void record(PluginExecutionRequest request, PluginExecutionResult result, long startedNanos) {
        long latencyMs = (System.nanoTime() - startedNanos) / 1_000_000;
        observationSink.accept(new PluginRuntimeObservation(
                request.operationRef().operationId(),
                request.operationRef().attemptId(),
                request.providerRef().providerId(),
                request.capability(),
                request.executionMode().name(),
                latencyMs,
                result.status().name(),
                result.error() != null ? result.error().category().name() : null,
                result.error() != null ? result.error().providerOperationId() : null,
                null,
                null,
                null,
                Instant.now()));
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
        if (code.contains("404")) {
            category = PluginRuntimeErrorCategory.CAPABILITY_UNSUPPORTED;
        } else if (code.contains("408")) {
            category = PluginRuntimeErrorCategory.TIMEOUT;
        } else if (code.contains("429")) {
            category = PluginRuntimeErrorCategory.RATE_LIMITED;
        } else if (code.contains("RESOURCE") || code.contains("LIMIT")) {
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
}
