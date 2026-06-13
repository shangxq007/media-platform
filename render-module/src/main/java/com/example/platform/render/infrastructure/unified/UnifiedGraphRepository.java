package com.example.platform.render.infrastructure.unified;

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
 * Repository for persisting UnifiedRequestGraph.
 */
@Repository
public class UnifiedGraphRepository {

    private static final Logger log = LoggerFactory.getLogger(UnifiedGraphRepository.class);

    private final DSLContext dsl;

    public UnifiedGraphRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Save a unified request graph.
     */
    public void save(UnifiedRequestGraph graph) {
        // Save graph metadata
        dsl.insertInto(table("unified_request_graph"))
                .columns(
                        field("graph_id"),
                        field("request_id"),
                        field("tenant_id"),
                        field("workspace_id"),
                        field("job_id"),
                        field("root_node_id"),
                        field("status"),
                        field("created_at"),
                        field("completed_at")
                )
                .values(
                        graph.graphId(),
                        graph.requestId(),
                        graph.tenantId(),
                        graph.workspaceId(),
                        graph.jobId(),
                        graph.rootNodeId(),
                        graph.status().name(),
                        OffsetDateTime.from(graph.createdAt()),
                        graph.completedAt() != null ? OffsetDateTime.from(graph.completedAt()) : null
                )
                .onConflict(field("graph_id"))
                .doUpdate()
                .set(field("job_id"), graph.jobId())
                .set(field("root_node_id"), graph.rootNodeId())
                .set(field("status"), graph.status().name())
                .set(field("completed_at"), graph.completedAt() != null ? OffsetDateTime.from(graph.completedAt()) : null)
                .execute();

        // Save nodes
        for (GraphNode node : graph.toNodeList()) {
            saveNode(graph.graphId(), node);
        }

        // Save edges
        for (GraphEdge edge : graph.toEdgeList()) {
            saveEdge(graph.graphId(), edge);
        }

        log.debug("Saved UEEG: graphId={} nodes={} edges={}",
                graph.graphId(), graph.toNodeList().size(), graph.toEdgeList().size());
    }

    /**
     * Save a graph node.
     */
    private void saveNode(String graphId, GraphNode node) {
        dsl.insertInto(table("unified_graph_node"))
                .columns(
                        field("node_id"),
                        field("graph_id"),
                        field("type"),
                        field("subsystem"),
                        field("action"),
                        field("status"),
                        field("data"),
                        field("timestamp")
                )
                .values(
                        node.nodeId(),
                        graphId,
                        node.type().name(),
                        node.subsystem(),
                        node.action(),
                        node.status(),
                        serializeMap(node.data()),
                        OffsetDateTime.from(node.timestamp())
                )
                .onConflict(field("node_id"))
                .doUpdate()
                .set(field("status"), node.status())
                .set(field("data"), serializeMap(node.data()))
                .execute();
    }

    /**
     * Save a graph edge.
     */
    private void saveEdge(String graphId, GraphEdge edge) {
        dsl.insertInto(table("unified_graph_edge"))
                .columns(
                        field("edge_id"),
                        field("graph_id"),
                        field("source_node_id"),
                        field("target_node_id"),
                        field("edge_type"),
                        field("timestamp")
                )
                .values(
                        edge.edgeId(),
                        graphId,
                        edge.sourceNodeId(),
                        edge.targetNodeId(),
                        edge.edgeType(),
                        OffsetDateTime.from(edge.timestamp())
                )
                .onConflict(field("edge_id"))
                .doNothing()
                .execute();
    }

    /**
     * Load a graph by request ID.
     */
    public Optional<UnifiedRequestGraph> loadByRequestId(String requestId) {
        Record graphRecord = dsl.select(
                        field("graph_id"),
                        field("request_id"),
                        field("tenant_id"),
                        field("workspace_id"),
                        field("job_id"),
                        field("root_node_id"),
                        field("status"),
                        field("created_at"),
                        field("completed_at")
                )
                .from(table("unified_request_graph"))
                .where(field("request_id").eq(requestId))
                .fetchOne();

        if (graphRecord == null) {
            return Optional.empty();
        }

        return Optional.of(mapToGraph(graphRecord));
    }

    /**
     * Load a graph by job ID.
     */
    public Optional<UnifiedRequestGraph> loadByJobId(String jobId) {
        Record graphRecord = dsl.select(
                        field("graph_id"),
                        field("request_id"),
                        field("tenant_id"),
                        field("workspace_id"),
                        field("job_id"),
                        field("root_node_id"),
                        field("status"),
                        field("created_at"),
                        field("completed_at")
                )
                .from(table("unified_request_graph"))
                .where(field("job_id").eq(jobId))
                .fetchOne();

        if (graphRecord == null) {
            return Optional.empty();
        }

        return Optional.of(mapToGraph(graphRecord));
    }

    /**
     * Load all nodes for a graph.
     */
    private Map<String, GraphNode> loadNodes(String graphId) {
        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        dsl.select(
                        field("node_id"),
                        field("type"),
                        field("subsystem"),
                        field("action"),
                        field("status"),
                        field("data"),
                        field("timestamp")
                )
                .from(table("unified_graph_node"))
                .where(field("graph_id").eq(graphId))
                .fetch()
                .forEach(record -> {
                    GraphNode node = mapToNode(record);
                    nodes.put(node.nodeId(), node);
                });
        return nodes;
    }

    /**
     * Load all edges for a graph.
     */
    private List<GraphEdge> loadEdges(String graphId) {
        return dsl.select(
                        field("edge_id"),
                        field("source_node_id"),
                        field("target_node_id"),
                        field("edge_type"),
                        field("timestamp")
                )
                .from(table("unified_graph_edge"))
                .where(field("graph_id").eq(graphId))
                .fetch(this::mapToEdge);
    }

    // ---------------------------------------------------------------------------
    // Mapping Helpers
    // ---------------------------------------------------------------------------

    private UnifiedRequestGraph mapToGraph(Record record) {
        String graphId = record.get(field("graph_id", String.class));
        Map<String, GraphNode> nodes = loadNodes(graphId);
        List<GraphEdge> edges = loadEdges(graphId);

        OffsetDateTime completedAt = record.get(field("completed_at"), OffsetDateTime.class);

        return new UnifiedRequestGraph(
                graphId,
                record.get(field("request_id", String.class)),
                record.get(field("tenant_id", String.class)),
                record.get(field("workspace_id", String.class)),
                record.get(field("job_id", String.class)),
                nodes,
                edges,
                record.get(field("root_node_id", String.class)),
                UnifiedRequestGraph.GraphStatus.valueOf(record.get(field("status", String.class))),
                record.get(field("created_at", OffsetDateTime.class)).toInstant(),
                completedAt != null ? completedAt.toInstant() : null,
                Map.of()
        );
    }

    private GraphNode mapToNode(Record record) {
        return new GraphNode(
                record.get(field("node_id", String.class)),
                UnifiedRequestGraph.NodeType.valueOf(record.get(field("type", String.class))),
                record.get(field("subsystem", String.class)),
                record.get(field("action", String.class)),
                record.get(field("status", String.class)),
                deserializeMap(record.get(field("data", String.class))),
                record.get(field("timestamp", OffsetDateTime.class)).toInstant(),
                Map.of()
        );
    }

    private GraphEdge mapToEdge(Record record) {
        return new GraphEdge(
                record.get(field("edge_id", String.class)),
                record.get(field("source_node_id", String.class)),
                record.get(field("target_node_id", String.class)),
                record.get(field("edge_type", String.class)),
                record.get(field("timestamp", OffsetDateTime.class)).toInstant(),
                null
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
