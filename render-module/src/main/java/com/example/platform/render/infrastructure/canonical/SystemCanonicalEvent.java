package com.example.platform.render.infrastructure.canonical;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * System Canonical Event - the single event type for all subsystems.
 * 
 * <p>ALL subsystems MUST emit this event type through the SystemEventBus.
 * No direct cross-service mutation is allowed.
 * 
 * <p>Event ordering is guaranteed per jobId.
 * Deterministic replay is supported.
 */
public record SystemCanonicalEvent(
        String eventId,
        String eventType,
        Instant timestamp,
        String tenantId,
        String workspaceId,
        String jobId,
        String sourceSystem,
        int sequenceNumber,
        Map<String, Object> payload,
        Map<String, Object> metadata
) {
    /**
     * Create a new canonical event.
     */
    public static SystemCanonicalEvent create(
            String eventType,
            String tenantId,
            String workspaceId,
            String jobId,
            String sourceSystem,
            Map<String, Object> payload) {
        return new SystemCanonicalEvent(
                UUID.randomUUID().toString(),
                eventType,
                Instant.now(),
                tenantId,
                workspaceId,
                jobId,
                sourceSystem,
                0,
                payload,
                Map.of()
        );
    }

    /**
     * Create with sequence number.
     */
    public static SystemCanonicalEvent withSequence(
            SystemCanonicalEvent event,
            int sequenceNumber) {
        return new SystemCanonicalEvent(
                event.eventId,
                event.eventType,
                event.timestamp,
                event.tenantId,
                event.workspaceId,
                event.jobId,
                event.sourceSystem,
                sequenceNumber,
                event.payload,
                event.metadata
        );
    }

    /**
     * Create with additional metadata.
     */
    public static SystemCanonicalEvent withMetadata(
            SystemCanonicalEvent event,
            Map<String, Object> additionalMetadata) {
        Map<String, Object> merged = new java.util.HashMap<>(event.metadata);
        merged.putAll(additionalMetadata);
        return new SystemCanonicalEvent(
                event.eventId,
                event.eventType,
                event.timestamp,
                event.tenantId,
                event.workspaceId,
                event.jobId,
                event.sourceSystem,
                event.sequenceNumber,
                event.payload,
                Map.copyOf(merged)
        );
    }

    /**
     * Get a payload value.
     */
    public Object getPayload(String key) {
        return payload.get(key);
    }

    /**
     * Get a payload value as string.
     */
    public String getStringPayload(String key, String defaultValue) {
        Object val = payload.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    /**
     * Get a payload value as double.
     */
    public double getDoublePayload(String key, double defaultValue) {
        Object val = payload.get(key);
        if (val instanceof Number n) return n.doubleValue();
        return defaultValue;
    }

    /**
     * Get a payload value as boolean.
     */
    public boolean getBooleanPayload(String key, boolean defaultValue) {
        Object val = payload.get(key);
        if (val instanceof Boolean b) return b;
        return defaultValue;
    }

    /**
     * Get a metadata value.
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * Check if this event is from a specific source system.
     */
    public boolean isFrom(String sourceSystem) {
        return this.sourceSystem.equals(sourceSystem);
    }

    /**
     * Get a human-readable description.
     */
    public String getDescription() {
        return String.format("[%s] %s from %s (job=%s, seq=%d)",
                eventId.substring(0, 8), eventType, sourceSystem, jobId, sequenceNumber);
    }

    // ---------------------------------------------------------------------------
    // Event Types
    // ---------------------------------------------------------------------------

    public static final String EXECUTION_STATE_CHANGE = "EXECUTION_STATE_CHANGE";
    public static final String BILLING_DECISION = "BILLING_DECISION";
    public static final String POLICY_DECISION = "POLICY_DECISION";
    public static final String PROVIDER_DECISION = "PROVIDER_DECISION";
    public static final String ARTIFACT_CREATED = "ARTIFACT_CREATED";
    public static final String COLLABORATION_EVENT = "COLLABORATION_EVENT";
    public static final String MARKETPLACE_EVENT = "MARKETPLACE_EVENT";
    public static final String OPTIMIZATION_EVENT = "OPTIMIZATION_EVENT";

    // Strategy Event Types
    public static final String STRATEGY_PLANNED = "STRATEGY_PLANNED";
    public static final String STRATEGY_EXECUTED = "STRATEGY_EXECUTED";
    public static final String STRATEGY_DEVIATION_DETECTED = "STRATEGY_DEVIATION_DETECTED";
    public static final String STRATEGY_ADJUSTED = "STRATEGY_ADJUSTED";
    public static final String STRATEGY_LEARNED = "STRATEGY_LEARNED";

    // Anchor Event Types
    public static final String ANCHOR_EVALUATED = "ANCHOR_EVALUATED";
    public static final String DRIFT_DETECTED = "DRIFT_DETECTED";
    public static final String EVOLUTION_BLOCKED = "EVOLUTION_BLOCKED";
    public static final String IDENTITY_CONSTRAINT_VIOLATION = "IDENTITY_CONSTRAINT_VIOLATION";
    public static final String DRIFT_LOCK_ACTIVATED = "DRIFT_LOCK_ACTIVATED";

    // Source Systems
    public static final String SOURCE_STATE_MACHINE = "StateMachine";
    public static final String SOURCE_BILLING_ENGINE = "BillingDecisionEngine";
    public static final String SOURCE_POLICY_ENGINE = "PolicyEngine";
    public static final String SOURCE_PROVIDER_RUNTIME = "ProviderRuntimeEngine";
    public static final String SOURCE_ARTIFACT_GRAPH = "ArtifactGraph";
    public static final String SOURCE_COLLABORATION = "CollaborationEngine";
    public static final String SOURCE_MARKETPLACE = "Marketplace";
    public static final String SOURCE_ADAPTIVE = "AdaptiveEngine";
    public static final String SOURCE_STRATEGY_PLANNER = "StrategyPlannerEngine";
    public static final String SOURCE_STRATEGY_ADJUSTER = "RuntimeStrategyAdjuster";
    public static final String SOURCE_STRATEGY_FEEDBACK = "StrategyFeedbackLoop";
    public static final String SOURCE_ANCHOR = "SystemAnchorFunction";
    public static final String SOURCE_EVOLUTION_BOUNDARY = "EvolutionBoundaryController";
    public static final String SOURCE_DRIFT_LOCK = "DriftLock";
}
