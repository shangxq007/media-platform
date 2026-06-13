package com.example.platform.render.infrastructure.canonical;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

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
        dsl.insertInto(table("system_canonical_graph"))
                .columns(
                        field("graph_id"),
                        field("job_id"),
                        field("tenant_id"),
                        field("workspace_id"),
                        field("status"),
                        field("created_at"),
                        field("completed_at")
                )
                .values(
                        graph.graphId(),
                        graph.jobId(),
                        graph.tenantId(),
                        graph.workspaceId(),
                        graph.status().name(),
                        OffsetDateTime.from(graph.createdAt()),
                        graph.completedAt() != null ? OffsetDateTime.from(graph.completedAt()) : null
                )
                .onConflict(field("graph_id"))
                .doUpdate()
                .set(field("status"), graph.status().name())
                .set(field("completed_at"), graph.completedAt() != null ? OffsetDateTime.from(graph.completedAt()) : null)
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
        dsl.insertInto(table("system_canonical_event"))
                .columns(
                        field("event_id"),
                        field("graph_id"),
                        field("event_type"),
                        field("timestamp"),
                        field("tenant_id"),
                        field("workspace_id"),
                        field("job_id"),
                        field("source_system"),
                        field("sequence_number"),
                        field("payload")
                )
                .values(
                        event.eventId(),
                        graphId,
                        event.eventType(),
                        OffsetDateTime.from(event.timestamp()),
                        event.tenantId(),
                        event.workspaceId(),
                        event.jobId(),
                        event.sourceSystem(),
                        event.sequenceNumber(),
                        serializeMap(event.payload())
                )
                .onConflict(field("event_id"))
                .doNothing()
                .execute();
    }

    /**
     * Save a causal edge.
     */
    private void saveEdge(String graphId, SystemCanonicalGraph.CausalEdge edge) {
        dsl.insertInto(table("system_canonical_edge"))
                .columns(
                        field("edge_id"),
                        field("graph_id"),
                        field("source_event_id"),
                        field("target_event_id"),
                        field("edge_type"),
                        field("timestamp")
                )
                .values(
                        edge.edgeId(),
                        graphId,
                        edge.sourceEventId(),
                        edge.targetEventId(),
                        edge.edgeType(),
                        OffsetDateTime.from(edge.timestamp())
                )
                .onConflict(field("edge_id"))
                .doNothing()
                .execute();
    }

    /**
     * Load a graph by job ID.
     */
    public Optional<SystemCanonicalGraph> loadByJobId(String jobId) {
        Record graphRecord = dsl.select(
                        field("graph_id"),
                        field("job_id"),
                        field("tenant_id"),
                        field("workspace_id"),
                        field("status"),
                        field("created_at"),
                        field("completed_at")
                )
                .from(table("system_canonical_graph"))
                .where(field("job_id").eq(jobId))
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
                        field("event_id"),
                        field("event_type"),
                        field("timestamp"),
                        field("tenant_id"),
                        field("workspace_id"),
                        field("job_id"),
                        field("source_system"),
                        field("sequence_number"),
                        field("payload")
                )
                .from(table("system_canonical_event"))
                .where(field("graph_id").eq(graphId))
                .orderBy(field("sequence_number").asc())
                .fetch(this::mapToEvent);
    }

    /**
     * Load edges for a graph.
     */
    private List<SystemCanonicalGraph.CausalEdge> loadEdges(String graphId) {
        return dsl.select(
                        field("edge_id"),
                        field("source_event_id"),
                        field("target_event_id"),
                        field("edge_type"),
                        field("timestamp")
                )
                .from(table("system_canonical_edge"))
                .where(field("graph_id").eq(graphId))
                .fetch(this::mapToEdge);
    }

    // ---------------------------------------------------------------------------
    // Mapping Helpers
    // ---------------------------------------------------------------------------

    private SystemCanonicalGraph mapToGraph(Record record) {
        String graphId = record.get(field("graph_id", String.class));
        List<SystemCanonicalEvent> events = loadEvents(graphId);
        List<SystemCanonicalGraph.CausalEdge> edges = loadEdges(graphId);

        OffsetDateTime completedAt = record.get(field("completed_at"), OffsetDateTime.class);

        return new SystemCanonicalGraph(
                graphId,
                record.get(field("job_id", String.class)),
                record.get(field("tenant_id", String.class)),
                record.get(field("workspace_id", String.class)),
                events,
                edges,
                SystemCanonicalGraph.GraphStatus.valueOf(record.get(field("status", String.class))),
                record.get(field("created_at", OffsetDateTime.class)).toInstant(),
                completedAt != null ? completedAt.toInstant() : null,
                Map.of()
        );
    }

    private SystemCanonicalEvent mapToEvent(Record record) {
        return new SystemCanonicalEvent(
                record.get(field("event_id", String.class)),
                record.get(field("event_type", String.class)),
                record.get(field("timestamp", OffsetDateTime.class)).toInstant(),
                record.get(field("tenant_id", String.class)),
                record.get(field("workspace_id", String.class)),
                record.get(field("job_id", String.class)),
                record.get(field("source_system", String.class)),
                record.get(field("sequence_number", Integer.class)),
                deserializeMap(record.get(field("payload", String.class))),
                Map.of()
        );
    }

    private SystemCanonicalGraph.CausalEdge mapToEdge(Record record) {
        return new SystemCanonicalGraph.CausalEdge(
                record.get(field("edge_id", String.class)),
                record.get(field("source_event_id", String.class)),
                record.get(field("target_event_id", String.class)),
                record.get(field("edge_type", String.class)),
                record.get(field("timestamp", OffsetDateTime.class)).toInstant()
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
