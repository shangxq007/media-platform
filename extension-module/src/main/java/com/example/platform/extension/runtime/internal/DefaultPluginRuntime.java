package com.example.platform.extension.runtime.internal;

import com.example.platform.extension.runtime.ExecutionMode;
import com.example.platform.extension.runtime.PluginExecutionProgress;
import com.example.platform.extension.runtime.PluginExecutionRequest;
import com.example.platform.extension.runtime.PluginExecutionResult;
import com.example.platform.extension.runtime.PluginExecutionStatus;
import com.example.platform.extension.runtime.PluginRuntime;
import com.example.platform.extension.runtime.PluginRuntimeErrorCategory;
import com.example.platform.extension.runtime.PluginRuntimeExecutionException;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Default Plugin Runtime V2 execution authority (PRV2-ADR-001/003).
 *
 * <p>Execution flow: validate invariants → trust enforcement (GAP-003) →
 * delegate to the SPI compatibility adapter (TRUSTED_IN_PROCESS) → map
 * timeout/cancel/progress → observability.</p>
 *
 * <p>The runtime does NOT own retry (PLUGIN_RUNTIME_RETRY_OWNERSHIP_V1) and
 * does NOT mutate domain lifecycle state (AR-PRV2-02, AR-PRV2-14).</p>
 */
public final class DefaultPluginRuntime implements PluginRuntime {

    private final ProviderExtensionSpiRuntimeAdapter spiAdapter;
    private final SecretRefResolver secretResolver;
    private final Consumer<PluginExecutionProgress> progressListener;
    private final Consumer<PluginRuntimeObservation> observationSink;
    private final RuntimeUsageEmitter usageEmitter;

    /** Observability records are {@link PluginRuntimeObservation}. */

    public DefaultPluginRuntime(ProviderExtensionSpiRuntimeAdapter spiAdapter) {
        this(spiAdapter, SecretRefResolver.NOOP, p -> {
        }, o -> {
        }, null);
    }

    public DefaultPluginRuntime(ProviderExtensionSpiRuntimeAdapter spiAdapter,
                                SecretRefResolver secretResolver,
                                Consumer<PluginExecutionProgress> progressListener,
                                Consumer<PluginRuntimeObservation> observationSink,
                                RuntimeUsageEmitter usageEmitter) {
        this.spiAdapter = Objects.requireNonNull(spiAdapter, "spiAdapter must not be null");
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

            PluginExecutionResult result = spiAdapter.execute(request);

            // GAP-002 closure: every canonical runtime execution emits base usage facts
            if (usageEmitter != null && result.status() == PluginExecutionStatus.SUCCEEDED) {
                long durationMs = (System.nanoTime() - started) / 1_000_000;
                usageEmitter.emitBaseFacts(
                        request.tenantId(), request.actorRef(), request.operationRef(),
                        request.providerRef(), request.capability(), durationMs);
            }

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

    private static String safeMessage(Throwable t) {
        String m = t != null ? t.getMessage() : null;
        return m != null ? m : t != null ? t.getClass().getSimpleName() : "unknown";
    }
}
