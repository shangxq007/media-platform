package com.example.platform.render.domain;

import com.example.platform.render.infrastructure.providerruntime.trace.ProviderTraceEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Integrates the state machine with the observability trace system.
 * Emits state transition trace nodes to the ProviderTraceEmitter.
 */
@Component
public class StateMachineTraceEmitter {

    private static final Logger log = LoggerFactory.getLogger(StateMachineTraceEmitter.class);

    private final ProviderTraceEmitter providerTraceEmitter;

    public StateMachineTraceEmitter(ProviderTraceEmitter providerTraceEmitter) {
        this.providerTraceEmitter = providerTraceEmitter;
    }

    /**
     * Emit a state transition trace node to the observability system.
     */
    public void emitTransition(StateTransitionTraceNode traceNode) {
        log.debug("Emitting state transition trace: {}", traceNode.getDescription());

        // Store in provider trace emitter for retrieval
        String traceId = traceNode.traceId();
        if (traceId != null) {
            providerTraceEmitter.emitExecution(
                    traceId,
                    traceNode.jobId(),
                    "STATE_MACHINE",
                    true,
                    null
            );
        }
    }

    /**
     * Get all state transitions for a trace ID.
     */
    public List<StateTransitionTraceNode> getTransitionsForTrace(String traceId) {
        // This would retrieve from a dedicated store in production
        return List.of();
    }
}
