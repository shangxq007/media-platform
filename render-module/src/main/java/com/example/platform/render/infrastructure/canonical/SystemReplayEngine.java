package com.example.platform.render.infrastructure.canonical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * System Replay Engine - enables deterministic replay of full system execution.
 * 
 * <p>Capabilities:
 * <ul>
 *   <li>Replay full system execution</li>
 *   <li>Reconstruct billing decisions</li>
 *   <li>Reconstruct provider selection</li>
 *   <li>Reconstruct artifact generation</li>
 *   <li>Reconstruct marketplace/collaboration events</li>
 * </ul>
 */
@Service
public class SystemReplayEngine {

    private static final Logger log = LoggerFactory.getLogger(SystemReplayEngine.class);

    private final SystemEventBus eventBus;

    public SystemReplayEngine(SystemEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Replay a job's execution from its canonical graph.
     */
    public ReplayResult replay(String jobId) {
        log.info("Starting replay for job {}", jobId);

        List<SystemCanonicalEvent> events = eventBus.getEventsForJob(jobId);
        if (events.isEmpty()) {
            return ReplayResult.failure(jobId, "No events found for job");
        }

        SystemCanonicalGraph graph = SystemCanonicalGraph.fromEvents(jobId, events);

        // Reconstruct each subsystem state
        ExecutionReconstruction execution = reconstructExecution(graph);
        BillingReconstruction billing = reconstructBilling(graph);
        ProviderReconstruction provider = reconstructProvider(graph);
        ArtifactReconstruction artifacts = reconstructArtifacts(graph);

        ReplayResult result = new ReplayResult(
                jobId,
                true,
                graph,
                execution,
                billing,
                provider,
                artifacts,
                graph.size(),
                Instant.now()
        );

        log.info("Replay completed for job {}: {} events", jobId, graph.size());
        return result;
    }

    /**
     * Replay from a list of events (for external replay).
     */
    public ReplayResult replayFromEvents(String jobId, List<SystemCanonicalEvent> events) {
        log.info("Starting external replay for job {} with {} events", jobId, events.size());

        SystemCanonicalGraph graph = SystemCanonicalGraph.fromEvents(jobId, events);

        ExecutionReconstruction execution = reconstructExecution(graph);
        BillingReconstruction billing = reconstructBilling(graph);
        ProviderReconstruction provider = reconstructProvider(graph);
        ArtifactReconstruction artifacts = reconstructArtifacts(graph);

        return new ReplayResult(
                jobId,
                true,
                graph,
                execution,
                billing,
                provider,
                artifacts,
                events.size(),
                Instant.now()
        );
    }

    /**
     * Reconstruct execution state from graph.
     */
    private ExecutionReconstruction reconstructExecution(SystemCanonicalGraph graph) {
        List<SystemCanonicalEvent> stateEvents = graph.getExecutionStateEvents();

        String currentState = "UNKNOWN";
        String previousState = null;

        for (SystemCanonicalEvent event : stateEvents) {
            previousState = currentState;
            currentState = event.getStringPayload("toState", "UNKNOWN");
        }

        return new ExecutionReconstruction(
                currentState,
                previousState,
                stateEvents.size(),
                stateEvents.stream()
                        .map(e -> e.getStringPayload("reason", ""))
                        .toList()
        );
    }

    /**
     * Reconstruct billing decisions from graph.
     */
    private BillingReconstruction reconstructBilling(SystemCanonicalGraph graph) {
        List<SystemCanonicalEvent> billingEvents = graph.getBillingDecisionEvents();

        String decision = "UNKNOWN";
        String reasonCode = null;
        double estimatedCost = 0;

        for (SystemCanonicalEvent event : billingEvents) {
            decision = event.getStringPayload("decision", "UNKNOWN");
            reasonCode = event.getStringPayload("reasonCode", null);
            estimatedCost = event.getDoublePayload("estimatedCost", 0);
        }

        return new BillingReconstruction(
                decision,
                reasonCode,
                estimatedCost,
                billingEvents.size()
        );
    }

    /**
     * Reconstruct provider selection from graph.
     */
    private ProviderReconstruction reconstructProvider(SystemCanonicalGraph graph) {
        List<SystemCanonicalEvent> providerEvents = graph.getProviderDecisionEvents();

        String selectedProvider = null;
        boolean fallbackTriggered = false;

        for (SystemCanonicalEvent event : providerEvents) {
            selectedProvider = event.getStringPayload("selectedProvider", null);
            fallbackTriggered = event.getBooleanPayload("fallbackTriggered", false);
        }

        return new ProviderReconstruction(
                selectedProvider,
                fallbackTriggered,
                providerEvents.size()
        );
    }

    /**
     * Reconstruct artifact generation from graph.
     */
    private ArtifactReconstruction reconstructArtifacts(SystemCanonicalGraph graph) {
        List<SystemCanonicalEvent> artifactEvents = graph.getArtifactEvents();

        return new ArtifactReconstruction(
                artifactEvents.size(),
                artifactEvents.stream()
                        .map(e -> e.getStringPayload("artifactId", ""))
                        .toList(),
                artifactEvents.stream()
                        .map(e -> e.getStringPayload("uri", ""))
                        .toList()
        );
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record ReplayResult(
            String jobId,
            boolean success,
            SystemCanonicalGraph graph,
            ExecutionReconstruction execution,
            BillingReconstruction billing,
            ProviderReconstruction provider,
            ArtifactReconstruction artifacts,
            int totalEvents,
            Instant replayedAt
    ) {
        public static ReplayResult failure(String jobId, String reason) {
            return new ReplayResult(
                    jobId, false, SystemCanonicalGraph.empty(jobId),
                    null, null, null, null, 0, Instant.now()
            );
        }

        public String getSummary() {
            if (!success) return "Replay failed";
            return String.format("Replay: %s -> %s via %s ($%.4f, %d artifacts)",
                    execution.previousState(), execution.currentState(),
                    provider.selectedProvider(), billing.estimatedCost(),
                    artifacts.count());
        }
    }

    public record ExecutionReconstruction(
            String currentState,
            String previousState,
            int transitionCount,
            List<String> transitionReasons
    ) {}

    public record BillingReconstruction(
            String decision,
            String reasonCode,
            double estimatedCost,
            int decisionCount
    ) {}

    public record ProviderReconstruction(
            String selectedProvider,
            boolean fallbackTriggered,
            int decisionCount
    ) {}

    public record ArtifactReconstruction(
            int count,
            List<String> artifactIds,
            List<String> uris
    ) {}
}
