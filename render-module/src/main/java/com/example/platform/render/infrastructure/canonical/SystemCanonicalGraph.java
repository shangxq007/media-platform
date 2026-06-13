package com.example.platform.render.infrastructure.canonical;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * System Canonical Graph - replaces all subsystem graphs with a single canonical model.
 * 
 * <p>Replaces:
 * <ul>
 *   <li>UEEG</li>
 *   <li>ArtifactGraph</li>
 *   <li>BillingGraph</li>
 *   <li>StateMachineHistory</li>
 * </ul>
 * 
 * <p>The entire system can be replayed from this graph.
 */
public record SystemCanonicalGraph(
        String graphId,
        String jobId,
        String tenantId,
        String workspaceId,
        List<SystemCanonicalEvent> nodes,
        List<CausalEdge> edges,
        GraphStatus status,
        Instant createdAt,
        Instant completedAt,
        Map<String, Object> metadata
) {
    /**
     * Create a new canonical graph from events.
     */
    public static SystemCanonicalGraph fromEvents(String jobId, List<SystemCanonicalEvent> events) {
        if (events.isEmpty()) {
            return empty(jobId);
        }

        SystemCanonicalEvent first = events.get(0);
        List<CausalEdge> edges = buildCausalEdges(events);

        return new SystemCanonicalGraph(
                "graph-" + jobId,
                jobId,
                first.tenantId(),
                first.workspaceId(),
                List.copyOf(events),
                edges,
                GraphStatus.ACTIVE,
                first.timestamp(),
                null,
                Map.of()
        );
    }

    /**
     * Create an empty graph.
     */
    public static SystemCanonicalGraph empty(String jobId) {
        return new SystemCanonicalGraph(
                "graph-" + jobId,
                jobId,
                null, null,
                List.of(), List.of(),
                GraphStatus.ACTIVE,
                Instant.now(), null,
                Map.of()
        );
    }

    /**
     * Mark the graph as completed.
     */
    public SystemCanonicalGraph complete() {
        return new SystemCanonicalGraph(
                graphId, jobId, tenantId, workspaceId,
                nodes, edges, GraphStatus.COMPLETED,
                createdAt, Instant.now(), metadata
        );
    }

    /**
     * Mark the graph as failed.
     */
    public SystemCanonicalGraph fail() {
        return new SystemCanonicalGraph(
                graphId, jobId, tenantId, workspaceId,
                nodes, edges, GraphStatus.FAILED,
                createdAt, Instant.now(), metadata
        );
    }

    /**
     * Get all events of a specific type.
     */
    public List<SystemCanonicalEvent> getEventsByType(String eventType) {
        return nodes.stream()
                .filter(e -> e.eventType().equals(eventType))
                .toList();
    }

    /**
     * Get all events from a specific source system.
     */
    public List<SystemCanonicalEvent> getEventsBySource(String sourceSystem) {
        return nodes.stream()
                .filter(e -> e.sourceSystem().equals(sourceSystem))
                .toList();
    }

    /**
     * Get the execution state events.
     */
    public List<SystemCanonicalEvent> getExecutionStateEvents() {
        return getEventsByType(SystemCanonicalEvent.EXECUTION_STATE_CHANGE);
    }

    /**
     * Get the billing decision events.
     */
    public List<SystemCanonicalEvent> getBillingDecisionEvents() {
        return getEventsByType(SystemCanonicalEvent.BILLING_DECISION);
    }

    /**
     * Get the policy decision events.
     */
    public List<SystemCanonicalEvent> getPolicyDecisionEvents() {
        return getEventsByType(SystemCanonicalEvent.POLICY_DECISION);
    }

    /**
     * Get the provider decision events.
     */
    public List<SystemCanonicalEvent> getProviderDecisionEvents() {
        return getEventsByType(SystemCanonicalEvent.PROVIDER_DECISION);
    }

    /**
     * Get the artifact events.
     */
    public List<SystemCanonicalEvent> getArtifactEvents() {
        return getEventsByType(SystemCanonicalEvent.ARTIFACT_CREATED);
    }

    /**
     * Get the collaboration events.
     */
    public List<SystemCanonicalEvent> getCollaborationEvents() {
        return getEventsByType(SystemCanonicalEvent.COLLABORATION_EVENT);
    }

    /**
     * Get the marketplace events.
     */
    public List<SystemCanonicalEvent> getMarketplaceEvents() {
        return getEventsByType(SystemCanonicalEvent.MARKETPLACE_EVENT);
    }

    /**
     * Get the optimization events.
     */
    public List<SystemCanonicalEvent> getOptimizationEvents() {
        return getEventsByType(SystemCanonicalEvent.OPTIMIZATION_EVENT);
    }

    /**
     * Get the last event of a specific type.
     */
    public Optional<SystemCanonicalEvent> getLastEventByType(String eventType) {
        return nodes.stream()
                .filter(e -> e.eventType().equals(eventType))
                .reduce((a, b) -> b);
    }

    /**
     * Get the execution path (ordered events).
     */
    public List<SystemCanonicalEvent> getExecutionPath() {
        return nodes.stream()
                .sorted(Comparator.comparingInt(SystemCanonicalEvent::sequenceNumber))
                .toList();
    }

    /**
     * Get the causal chain for an event.
     */
    public List<SystemCanonicalEvent> getCausalChain(String eventId) {
        List<SystemCanonicalEvent> chain = new ArrayList<>();
        String currentId = eventId;

        while (currentId != null) {
            final String searchId = currentId;
            SystemCanonicalEvent event = nodes.stream()
                    .filter(e -> e.eventId().equals(searchId))
                    .findFirst()
                    .orElse(null);
            if (event == null) break;

            chain.add(0, event);

            // Find parent
            final String targetId = event.eventId();
            currentId = edges.stream()
                    .filter(e -> e.targetEventId().equals(targetId))
                    .map(CausalEdge::sourceEventId)
                    .findFirst()
                    .orElse(null);
        }

        return chain;
    }

    /**
     * Get the number of events.
     */
    public int size() {
        return nodes.size();
    }

    /**
     * Check if the graph is empty.
     */
    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    /**
     * Get a summary of the graph.
     */
    public String getSummary() {
        Map<String, Long> eventsByType = nodes.stream()
                .collect(Collectors.groupingBy(SystemCanonicalEvent::eventType, Collectors.counting()));

        return String.format("CanonicalGraph[%s] job=%s events=%d types=%s status=%s",
                graphId, jobId, nodes.size(), eventsByType, status);
    }

    // ---------------------------------------------------------------------------
    // Causal Edge
    // ---------------------------------------------------------------------------

    public record CausalEdge(
            String edgeId,
            String sourceEventId,
            String targetEventId,
            String edgeType,
            Instant timestamp
    ) {
        public static CausalEdge create(String sourceId, String targetId, String edgeType) {
            return new CausalEdge(
                    "edge-" + sourceId + "-" + targetId,
                    sourceId, targetId, edgeType, Instant.now()
            );
        }
    }

    // ---------------------------------------------------------------------------
    // Graph Status
    // ---------------------------------------------------------------------------

    public enum GraphStatus {
        ACTIVE,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private static List<CausalEdge> buildCausalEdges(List<SystemCanonicalEvent> events) {
        List<CausalEdge> edges = new ArrayList<>();

        // Build sequential edges
        for (int i = 1; i < events.size(); i++) {
            SystemCanonicalEvent prev = events.get(i - 1);
            SystemCanonicalEvent curr = events.get(i);
            edges.add(CausalEdge.create(prev.eventId(), curr.eventId(), "SEQUENCE"));
        }

        // Build causal edges based on event types
        for (SystemCanonicalEvent event : events) {
            // Billing depends on execution state
            if (event.eventType().equals(SystemCanonicalEvent.BILLING_DECISION)) {
                events.stream()
                        .filter(e -> e.eventType().equals(SystemCanonicalEvent.EXECUTION_STATE_CHANGE))
                        .filter(e -> e.sequenceNumber() < event.sequenceNumber())
                        .reduce((a, b) -> b)
                        .ifPresent(dep -> edges.add(CausalEdge.create(dep.eventId(), event.eventId(), "DEPENDS_ON")));
            }

            // Policy depends on billing
            if (event.eventType().equals(SystemCanonicalEvent.POLICY_DECISION)) {
                events.stream()
                        .filter(e -> e.eventType().equals(SystemCanonicalEvent.BILLING_DECISION))
                        .filter(e -> e.sequenceNumber() < event.sequenceNumber())
                        .reduce((a, b) -> b)
                        .ifPresent(dep -> edges.add(CausalEdge.create(dep.eventId(), event.eventId(), "VALIDATES")));
            }

            // Provider depends on policy
            if (event.eventType().equals(SystemCanonicalEvent.PROVIDER_DECISION)) {
                events.stream()
                        .filter(e -> e.eventType().equals(SystemCanonicalEvent.POLICY_DECISION))
                        .filter(e -> e.sequenceNumber() < event.sequenceNumber())
                        .reduce((a, b) -> b)
                        .ifPresent(dep -> edges.add(CausalEdge.create(dep.eventId(), event.eventId(), "CONSUMES")));
            }

            // Artifact depends on provider
            if (event.eventType().equals(SystemCanonicalEvent.ARTIFACT_CREATED)) {
                events.stream()
                        .filter(e -> e.eventType().equals(SystemCanonicalEvent.PROVIDER_DECISION))
                        .filter(e -> e.sequenceNumber() < event.sequenceNumber())
                        .reduce((a, b) -> b)
                        .ifPresent(dep -> edges.add(CausalEdge.create(dep.eventId(), event.eventId(), "PRODUCES")));
            }
        }

        return edges;
    }
}
