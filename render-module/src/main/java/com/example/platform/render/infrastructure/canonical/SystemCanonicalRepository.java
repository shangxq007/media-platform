package com.example.platform.render.infrastructure.canonical;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import static com.example.platform.typedschema.jooq.generated.tables.SystemCanonicalEdge.SYSTEM_CANONICAL_EDGE;
import static com.example.platform.typedschema.jooq.generated.tables.SystemCanonicalEvent.SYSTEM_CANONICAL_EVENT;
import static com.example.platform.typedschema.jooq.generated.tables.SystemCanonicalGraph.SYSTEM_CANONICAL_GRAPH;


/**
 * Repository for persisting SystemCanonicalGraph.
 */
@Repository
public class SystemCanonicalRepository {

    private static final Logger log = LoggerFactory.getLogger(SystemCanonicalRepository.class);

    private final DSLContext dsl;

    public SystemCanonicalRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Save a canonical graph.
     */
    public void save(SystemCanonicalGraph graph) {
        // Save graph metadata
        dsl.insertInto(SYSTEM_CANONICAL_GRAPH)
                .columns(
                        SYSTEM_CANONICAL_GRAPH.GRAPH_ID,
                        SYSTEM_CANONICAL_GRAPH.JOB_ID,
                        SYSTEM_CANONICAL_GRAPH.TENANT_ID,
                        SYSTEM_CANONICAL_GRAPH.WORKSPACE_ID,
                        SYSTEM_CANONICAL_GRAPH.STATUS,
                        SYSTEM_CANONICAL_GRAPH.CREATED_AT,
                        SYSTEM_CANONICAL_GRAPH.COMPLETED_AT
                )
                .values(
                        graph.graphId(),
                        graph.jobId(),
                        graph.tenantId(),
                        graph.workspaceId(),
                        graph.status().name(),
                        graph.createdAt() != null ? LocalDateTime.ofInstant(graph.createdAt(), ZoneOffset.UTC) : null,
                        graph.completedAt() != null ? LocalDateTime.ofInstant(graph.completedAt(), ZoneOffset.UTC) : null
                )
                .onConflict(SYSTEM_CANONICAL_GRAPH.GRAPH_ID)
                .doUpdate()
                .set(SYSTEM_CANONICAL_GRAPH.STATUS, graph.status().name())
                .set(SYSTEM_CANONICAL_GRAPH.COMPLETED_AT, graph.completedAt() != null ? LocalDateTime.ofInstant(graph.completedAt(), ZoneOffset.UTC) : null)
                .execute();

        // Save events
        for (SystemCanonicalEvent event : graph.nodes()) {
            saveEvent(graph.graphId(), event);
        }

        // Save edges
        for (SystemCanonicalGraph.CausalEdge edge : graph.edges()) {
            saveEdge(graph.graphId(), edge);
        }

        log.debug("Saved canonical graph: {} with {} events", graph.graphId(), graph.size());
    }

    /**
     * Save a canonical event.
     */
    private void saveEvent(String graphId, SystemCanonicalEvent event) {
        dsl.insertInto(SYSTEM_CANONICAL_EVENT)
                .columns(
                        SYSTEM_CANONICAL_EVENT.EVENT_ID,
                        SYSTEM_CANONICAL_EVENT.GRAPH_ID,
                        SYSTEM_CANONICAL_EVENT.EVENT_TYPE,
                        SYSTEM_CANONICAL_EVENT.TIMESTAMP,
                        SYSTEM_CANONICAL_EVENT.TENANT_ID,
                        SYSTEM_CANONICAL_EVENT.WORKSPACE_ID,
                        SYSTEM_CANONICAL_EVENT.JOB_ID,
                        SYSTEM_CANONICAL_EVENT.SOURCE_SYSTEM,
                        SYSTEM_CANONICAL_EVENT.SEQUENCE_NUMBER,
                        SYSTEM_CANONICAL_EVENT.PAYLOAD
                )
                .values(
                        event.eventId(),
                        graphId,
                        event.eventType(),
                        LocalDateTime.ofInstant(event.timestamp(), ZoneOffset.UTC),
                        event.tenantId(),
                        event.workspaceId(),
                        event.jobId(),
                        event.sourceSystem(),
                        event.sequenceNumber(),
                        serializeMap(event.payload())
                )
                .onConflict(SYSTEM_CANONICAL_EVENT.EVENT_ID)
                .doNothing()
                .execute();
    }

    /**
     * Save a causal edge.
     */
    private void saveEdge(String graphId, SystemCanonicalGraph.CausalEdge edge) {
        dsl.insertInto(SYSTEM_CANONICAL_EDGE)
                .columns(
                        SYSTEM_CANONICAL_EDGE.EDGE_ID,
                        SYSTEM_CANONICAL_EDGE.GRAPH_ID,
                        SYSTEM_CANONICAL_EDGE.SOURCE_EVENT_ID,
                        SYSTEM_CANONICAL_EDGE.TARGET_EVENT_ID,
                        SYSTEM_CANONICAL_EDGE.EDGE_TYPE,
                        SYSTEM_CANONICAL_EDGE.TIMESTAMP
                )
                .values(
                        edge.edgeId(),
                        graphId,
                        edge.sourceEventId(),
                        edge.targetEventId(),
                        edge.edgeType(),
                        LocalDateTime.ofInstant(edge.timestamp(), ZoneOffset.UTC)
                )
                .onConflict(SYSTEM_CANONICAL_EDGE.EDGE_ID)
                .doNothing()
                .execute();
    }

    /**
     * Load a graph by job ID.
     */
    public Optional<SystemCanonicalGraph> loadByJobId(String jobId) {
        Record graphRecord = dsl.select(
                        SYSTEM_CANONICAL_GRAPH.GRAPH_ID,
                        SYSTEM_CANONICAL_GRAPH.JOB_ID,
                        SYSTEM_CANONICAL_GRAPH.TENANT_ID,
                        SYSTEM_CANONICAL_GRAPH.WORKSPACE_ID,
                        SYSTEM_CANONICAL_GRAPH.STATUS,
                        SYSTEM_CANONICAL_GRAPH.CREATED_AT,
                        SYSTEM_CANONICAL_GRAPH.COMPLETED_AT
                )
                .from(SYSTEM_CANONICAL_GRAPH)
                .where(SYSTEM_CANONICAL_GRAPH.JOB_ID.eq(jobId))
                .fetchOne();

        if (graphRecord == null) {
            return Optional.empty();
        }

        return Optional.of(mapToGraph(graphRecord));
    }

    /**
     * Load events for a graph.
     */
    private List<SystemCanonicalEvent> loadEvents(String graphId) {
        return dsl.select(
                        SYSTEM_CANONICAL_EVENT.EVENT_ID,
                        SYSTEM_CANONICAL_EVENT.EVENT_TYPE,
                        SYSTEM_CANONICAL_EVENT.TIMESTAMP,
                        SYSTEM_CANONICAL_EVENT.TENANT_ID,
                        SYSTEM_CANONICAL_EVENT.WORKSPACE_ID,
                        SYSTEM_CANONICAL_EVENT.JOB_ID,
                        SYSTEM_CANONICAL_EVENT.SOURCE_SYSTEM,
                        SYSTEM_CANONICAL_EVENT.SEQUENCE_NUMBER,
                        SYSTEM_CANONICAL_EVENT.PAYLOAD
                )
                .from(SYSTEM_CANONICAL_EVENT)
                .where(SYSTEM_CANONICAL_EVENT.GRAPH_ID.eq(graphId))
                .orderBy(SYSTEM_CANONICAL_EVENT.SEQUENCE_NUMBER.asc())
                .fetch(this::mapToEvent);
    }

    /**
     * Load edges for a graph.
     */
    private List<SystemCanonicalGraph.CausalEdge> loadEdges(String graphId) {
        return dsl.select(
                        SYSTEM_CANONICAL_EDGE.EDGE_ID,
                        SYSTEM_CANONICAL_EDGE.SOURCE_EVENT_ID,
                        SYSTEM_CANONICAL_EDGE.TARGET_EVENT_ID,
                        SYSTEM_CANONICAL_EDGE.EDGE_TYPE,
                        SYSTEM_CANONICAL_EDGE.TIMESTAMP
                )
                .from(SYSTEM_CANONICAL_EDGE)
                .where(SYSTEM_CANONICAL_EDGE.GRAPH_ID.eq(graphId))
                .fetch(this::mapToEdge);
    }

    // ---------------------------------------------------------------------------
    // Mapping Helpers
    // ---------------------------------------------------------------------------

    private SystemCanonicalGraph mapToGraph(Record record) {
        String graphId = record.get(SYSTEM_CANONICAL_GRAPH.GRAPH_ID);
        List<SystemCanonicalEvent> events = loadEvents(graphId);
        List<SystemCanonicalGraph.CausalEdge> edges = loadEdges(graphId);

        LocalDateTime completedAtLdt = record.get(SYSTEM_CANONICAL_GRAPH.COMPLETED_AT);
        OffsetDateTime completedAt = completedAtLdt != null ? completedAtLdt.atOffset(ZoneOffset.UTC) : null;

        return new SystemCanonicalGraph(
                graphId,
                record.get(SYSTEM_CANONICAL_GRAPH.JOB_ID),
                record.get(SYSTEM_CANONICAL_GRAPH.TENANT_ID),
                record.get(SYSTEM_CANONICAL_GRAPH.WORKSPACE_ID),
                events,
                edges,
                SystemCanonicalGraph.GraphStatus.valueOf(record.get(SYSTEM_CANONICAL_GRAPH.STATUS)),
                record.get(SYSTEM_CANONICAL_GRAPH.CREATED_AT).atOffset(ZoneOffset.UTC).toInstant(),
                completedAt != null ? completedAt.toInstant() : null,
                Map.of()
        );
    }

    private SystemCanonicalEvent mapToEvent(Record record) {
        return new SystemCanonicalEvent(
                record.get(SYSTEM_CANONICAL_EVENT.EVENT_ID),
                record.get(SYSTEM_CANONICAL_EVENT.EVENT_TYPE),
                record.get(SYSTEM_CANONICAL_EVENT.TIMESTAMP).atOffset(ZoneOffset.UTC).toInstant(),
                record.get(SYSTEM_CANONICAL_EVENT.TENANT_ID),
                record.get(SYSTEM_CANONICAL_EVENT.WORKSPACE_ID),
                record.get(SYSTEM_CANONICAL_EVENT.JOB_ID),
                record.get(SYSTEM_CANONICAL_EVENT.SOURCE_SYSTEM),
                record.get(SYSTEM_CANONICAL_EVENT.SEQUENCE_NUMBER),
                deserializeMap(record.get(SYSTEM_CANONICAL_EVENT.PAYLOAD)),
                Map.of()
        );
    }

    private SystemCanonicalGraph.CausalEdge mapToEdge(Record record) {
        return new SystemCanonicalGraph.CausalEdge(
                record.get(SYSTEM_CANONICAL_EDGE.EDGE_ID),
                record.get(SYSTEM_CANONICAL_EDGE.SOURCE_EVENT_ID),
                record.get(SYSTEM_CANONICAL_EDGE.TARGET_EVENT_ID),
                record.get(SYSTEM_CANONICAL_EDGE.EDGE_TYPE),
                record.get(SYSTEM_CANONICAL_EDGE.TIMESTAMP).atOffset(ZoneOffset.UTC).toInstant()
        );
    }

    private String serializeMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private Map<String, Object> deserializeMap(String json) {
        if (json == null || json.isEmpty() || json.equals("{}")) return Map.of();
        Map<String, Object> result = new HashMap<>();
        String content = json.substring(1, json.length() - 1);
        for (String pair : content.split(",")) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"", "");
                String value = kv[1].trim().replace("\"", "");
                result.put(key, value);
            }
        }
        return Map.copyOf(result);
    }
}
