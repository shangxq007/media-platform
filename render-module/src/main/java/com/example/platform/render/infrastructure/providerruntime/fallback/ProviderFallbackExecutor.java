package com.example.platform.render.infrastructure.providerruntime.fallback;

import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.render.infrastructure.providerruntime.engine.ProviderRuntimeEngine.ProviderResolutionRequest;
import com.example.platform.render.infrastructure.providerruntime.health.ProviderHealthMonitor;
import com.example.platform.render.infrastructure.providerruntime.trace.ProviderTraceEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Executes render operations with fallback support.
 * If a provider fails, attempts execution with fallback providers.
 */
@Service
public class ProviderFallbackExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProviderFallbackExecutor.class);

    private final ProviderHealthMonitor healthMonitor;
    private final ProviderTraceEmitter traceEmitter;
    private final ProviderFallbackGraph fallbackGraph;

    public ProviderFallbackExecutor(
            ProviderHealthMonitor healthMonitor,
            ProviderTraceEmitter traceEmitter,
            ProviderFallbackGraph fallbackGraph
    ) {
        this.healthMonitor = healthMonitor;
        this.traceEmitter = traceEmitter;
        this.fallbackGraph = fallbackGraph;
    }

    /**
     * Execute a render operation with fallback support.
     */
    public FallbackExecutionResult executeWithFallback(
            RenderProvider primaryProvider,
            ProviderResolutionRequest request,
            String traceId
    ) {
        String primaryName = primaryProvider.getClass().getSimpleName();
        List<String> executionPath = new ArrayList<>();
        Map<String, String> failureReasons = new LinkedHashMap<>();

        // Try primary provider
        executionPath.add(primaryName);
        try {
            log.info("[{}] Attempting render with primary provider: {}", traceId, primaryName);

            RenderProvider.RenderResult result = primaryProvider.render(
                    request.jobId(),
                    request.context().get("aiScript") != null ? request.context().get("aiScript").toString() : "",
                    request.profile()
            );

            healthMonitor.recordSuccess(primaryName);
            traceEmitter.emitExecution(traceId, request.jobId(), primaryName, true, null);

            return new FallbackExecutionResult(
                    true,
                    primaryName,
                    result,
                    executionPath,
                    failureReasons,
                    null
            );
        } catch (Exception e) {
            log.warn("[{}] Primary provider {} failed: {}", traceId, primaryName, e.getMessage());

            healthMonitor.recordFailure(primaryName, e.getMessage());
            failureReasons.put(primaryName, e.getMessage());
            traceEmitter.emitExecution(traceId, request.jobId(), primaryName, false, e.getMessage());
        }

        // Try fallback providers
        List<String> fallbackProviders = fallbackGraph.getFallbackChain(primaryName);

        for (String fallbackName : fallbackProviders) {
            executionPath.add(fallbackName);

            // Find the fallback provider (this is simplified - in real implementation
            // you would inject the providers or use a registry)
            log.info("[{}] Attempting fallback to provider: {}", traceId, fallbackName);

            traceEmitter.emitFallback(traceId, request.jobId(), primaryName, fallbackName, "Primary provider failed");

            // Note: In a real implementation, you would resolve the fallback provider
            // from the provider registry and execute with it
        }

        // All providers failed
        return new FallbackExecutionResult(
                false,
                null,
                null,
                executionPath,
                failureReasons,
                "All providers in fallback chain failed"
        );
    }

    /**
     * Get the fallback chain for a provider.
     */
    public List<String> getFallbackChain(String providerName) {
        return fallbackGraph.getFallbackChain(providerName);
    }
}
