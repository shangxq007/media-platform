package com.example.platform.render.infrastructure.providerruntime.engine;

import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.render.infrastructure.providerruntime.capability.CapabilityDescriptor;
import com.example.platform.render.infrastructure.providerruntime.capability.CapabilityNegotiationResult;
import com.example.platform.render.infrastructure.providerruntime.capability.CapabilityNegotiationService;
import com.example.platform.render.infrastructure.providerruntime.fallback.FallbackExecutionResult;
import com.example.platform.render.infrastructure.providerruntime.fallback.ProviderFallbackExecutor;
import com.example.platform.render.infrastructure.providerruntime.health.ProviderHealthMonitor;
import com.example.platform.render.infrastructure.providerruntime.health.ProviderHealthStatus;
import com.example.platform.render.infrastructure.providerruntime.trace.ProviderDecisionTraceNode;
import com.example.platform.render.infrastructure.providerruntime.trace.ProviderTraceEmitter;
import com.example.platform.render.infrastructure.RenderProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core provider runtime engine that replaces implicit provider selection
 * with explicit, deterministic, observable provider resolution.
 */
@Service
public class ProviderRuntimeEngine {

    private static final Logger log = LoggerFactory.getLogger(ProviderRuntimeEngine.class);

    private final RenderProviderRegistry registry;
    private final CapabilityNegotiationService capabilityService;
    private final ProviderHealthMonitor healthMonitor;
    private final ProviderFallbackExecutor fallbackExecutor;
    private final ProviderTraceEmitter traceEmitter;

    public ProviderRuntimeEngine(
            RenderProviderRegistry registry,
            CapabilityNegotiationService capabilityService,
            ProviderHealthMonitor healthMonitor,
            ProviderFallbackExecutor fallbackExecutor,
            ProviderTraceEmitter traceEmitter
    ) {
        this.registry = registry;
        this.capabilityService = capabilityService;
        this.healthMonitor = healthMonitor;
        this.fallbackExecutor = fallbackExecutor;
        this.traceEmitter = traceEmitter;

        log.info("ProviderRuntimeEngine initialized with {} providers: {}",
                registry.getProviderMap().size(), registry.getProviderMap().keySet());
    }

    /**
     * Resolve the best provider for a given render request.
     * This is the main entry point for provider selection.
     */
    public ProviderResolutionResult resolveProvider(ProviderResolutionRequest request) {
        String traceId = request.traceId() != null ? request.traceId() : UUID.randomUUID().toString();
        Instant startTime = Instant.now();

        log.info("[{}] Resolving provider for job {} with capabilities: {}",
                traceId, request.jobId(), request.requiredCapabilities());

        // Step 1: Get all candidate providers
        List<ProviderCandidate> candidates = identifyCandidates(request);

        // Step 2: Filter by health status
        candidates = filterByHealth(candidates, traceId);

        // Step 3: Negotiate capabilities
        CapabilityNegotiationResult negotiationResult = capabilityService.negotiate(
                candidates.stream()
                        .map(c -> c.descriptor())
                        .collect(Collectors.toList()),
                request.requiredCapabilities()
        );

        // Step 4: Select provider (deterministic)
        ProviderCandidate selected = selectProvider(candidates, negotiationResult, request);

        // Step 5: Emit trace
        ProviderDecisionTraceNode traceNode = traceEmitter.emitDecision(
                traceId,
                request.jobId(),
                selected != null ? selected.providerName() : null,
                candidates.stream().map(ProviderCandidate::providerName).collect(Collectors.toList()),
                negotiationResult.selectionReason(),
                selected != null
        );

        // Step 6: Execute with fallback if needed
        FallbackExecutionResult executionResult = null;
        if (selected != null) {
            executionResult = fallbackExecutor.executeWithFallback(
                    selected.provider(),
                    request,
                    traceId
            );
        }

        return new ProviderResolutionResult(
                traceId,
                selected != null ? selected.providerName() : null,
                selected != null ? selected.provider() : null,
                candidates.stream().map(ProviderCandidate::providerName).collect(Collectors.toList()),
                negotiationResult,
                executionResult,
                traceNode,
                Instant.now().toEpochMilli() - startTime.toEpochMilli()
        );
    }

    /**
     * Identify all candidate providers that could potentially handle the request.
     */
    private List<ProviderCandidate> identifyCandidates(ProviderResolutionRequest request) {
        List<ProviderCandidate> candidates = new ArrayList<>();

        for (Map.Entry<String, RenderProvider> entry : registry.getProviderMap().entrySet()) {
            RenderProvider provider = entry.getValue();

            // Check if provider is enabled
            if (!isProviderEnabled(provider)) {
                continue;
            }

            // Create capability descriptor
            CapabilityDescriptor descriptor = capabilityService.describeProvider(provider);

            candidates.add(new ProviderCandidate(
                    entry.getKey(),
                    provider,
                    descriptor
            ));
        }

        return candidates;
    }

    /**
     * Filter candidates by health status.
     */
    private List<ProviderCandidate> filterByHealth(List<ProviderCandidate> candidates, String traceId) {
        List<ProviderCandidate> healthy = new ArrayList<>();

        for (ProviderCandidate candidate : candidates) {
            ProviderHealthStatus status = healthMonitor.checkHealth(candidate.providerName());

            if (status.isHealthy()) {
                healthy.add(candidate);
            } else {
                log.warn("[{}] Provider {} is unhealthy: {}",
                        traceId, candidate.providerName(), status.reason());
                traceEmitter.emitHealthSkip(traceId, candidate.providerName(), status.reason());
            }
        }

        // If no healthy providers, fall back to all candidates
        if (healthy.isEmpty() && !candidates.isEmpty()) {
            log.warn("[{}] No healthy providers found, falling back to all candidates", traceId);
            return candidates;
        }

        return healthy;
    }

    /**
     * Select the best provider from candidates.
     * Selection is deterministic based on priority and capability match.
     */
    private ProviderCandidate selectProvider(
            List<ProviderCandidate> candidates,
            CapabilityNegotiationResult negotiationResult,
            ProviderResolutionRequest request
    ) {
        if (candidates.isEmpty()) {
            return null;
        }

        // Filter to candidates that support required capabilities
        List<ProviderCandidate> capable = candidates.stream()
                .filter(c -> negotiationResult.supportedProviders().contains(c.providerName()))
                .collect(Collectors.toList());

        if (capable.isEmpty()) {
            // Fall back to any candidate
            capable = candidates;
        }

        // Sort by priority (deterministic)
        capable.sort((a, b) -> {
            int priorityA = getProviderPriority(a.provider());
            int priorityB = getProviderPriority(b.provider());
            return Integer.compare(priorityA, priorityB);
        });

        return capable.get(0);
    }

    private boolean isProviderEnabled(RenderProvider provider) {
        return provider.getStatus() == com.example.platform.render.infrastructure.ProviderStatus.PRODUCTION
                || provider.getStatus() == com.example.platform.render.infrastructure.ProviderStatus.POC
                || provider.getStatus() == com.example.platform.render.infrastructure.ProviderStatus.OPTIONAL;
    }

    private int getProviderPriority(RenderProvider provider) {
        try {
            String priority = provider.getPriority();
            return Integer.parseInt(priority.replace("P", ""));
        } catch (Exception e) {
            return 999;
        }
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record ProviderResolutionRequest(
            String jobId,
            String traceId,
            Set<String> requiredCapabilities,
            String profile,
            Map<String, Object> context
    ) {}

    public record ProviderCandidate(
            String providerName,
            RenderProvider provider,
            CapabilityDescriptor descriptor
    ) {}

    public record ProviderResolutionResult(
            String traceId,
            String selectedProviderName,
            RenderProvider selectedProvider,
            List<String> candidateNames,
            CapabilityNegotiationResult negotiationResult,
            FallbackExecutionResult executionResult,
            ProviderDecisionTraceNode traceNode,
            long resolutionTimeMs
    ) {
        public boolean isSuccess() {
            return selectedProvider != null;
        }
    }
}
