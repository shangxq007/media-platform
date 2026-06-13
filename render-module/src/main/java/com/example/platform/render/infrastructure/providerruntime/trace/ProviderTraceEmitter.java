package com.example.platform.render.infrastructure.providerruntime.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Emits trace nodes for provider runtime decisions.
 * Integrates with the observability system.
 */
@Component
public class ProviderTraceEmitter {

    private static final Logger log = LoggerFactory.getLogger(ProviderTraceEmitter.class);

    private final Map<String, List<Object>> traceNodes = new ConcurrentHashMap<>();

    /**
     * Emit a provider decision trace node.
     */
    public ProviderDecisionTraceNode emitDecision(
            String traceId,
            String jobId,
            String selectedProvider,
            List<String> candidates,
            String selectionReason,
            boolean providerFound
    ) {
        ProviderDecisionTraceNode node = ProviderDecisionTraceNode.create(
                traceId,
                jobId,
                selectedProvider,
                candidates,
                selectionReason,
                providerFound
        );

        recordNode(traceId, node);
        log.info("[{}] Provider decision: selected={} candidates={} reason={}",
                traceId, selectedProvider, candidates, selectionReason);

        return node;
    }

    /**
     * Emit a fallback decision trace node.
     */
    public FallbackDecisionTraceNode emitFallback(
            String traceId,
            String jobId,
            String fromProvider,
            String toProvider,
            String reason
    ) {
        FallbackDecisionTraceNode node = FallbackDecisionTraceNode.create(
                traceId,
                jobId,
                fromProvider,
                toProvider,
                reason
        );

        recordNode(traceId, node);
        log.info("[{}] Fallback: {} -> {} reason={}",
                traceId, fromProvider, toProvider, reason);

        return node;
    }

    /**
     * Emit an execution step trace node.
     */
    public ExecutionStepTraceNode emitExecution(
            String traceId,
            String jobId,
            String providerName,
            boolean success,
            String error
    ) {
        ExecutionStepTraceNode node = ExecutionStepTraceNode.create(
                traceId,
                jobId,
                providerName,
                success,
                error
        );

        recordNode(traceId, node);
        log.info("[{}] Execution: provider={} success={} error={}",
                traceId, providerName, success, error);

        return node;
    }

    /**
     * Emit a health skip trace node.
     */
    public void emitHealthSkip(String traceId, String providerName, String reason) {
        log.info("[{}] Health skip: provider={} reason={}",
                traceId, providerName, reason);
    }

    /**
     * Get all trace nodes for a trace ID.
     */
    public List<Object> getTraceNodes(String traceId) {
        return traceNodes.getOrDefault(traceId, List.of());
    }

    /**
     * Get all trace nodes.
     */
    public Map<String, List<Object>> getAllTraceNodes() {
        return Map.copyOf(traceNodes);
    }

    /**
     * Clear trace nodes for a trace ID.
     */
    public void clearTrace(String traceId) {
        traceNodes.remove(traceId);
    }

    /**
     * Clear all trace nodes.
     */
    public void clearAll() {
        traceNodes.clear();
    }

    private void recordNode(String traceId, Object node) {
        traceNodes.computeIfAbsent(traceId, k -> new CopyOnWriteArrayList<>())
                .add(node);
    }
}
