package com.example.platform.render.infrastructure.unified;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import static com.example.platform.typedschema.jooq.generated.tables.UnifiedGraphEdge.UNIFIED_GRAPH_EDGE;
import static com.example.platform.typedschema.jooq.generated.tables.UnifiedGraphNode.UNIFIED_GRAPH_NODE;
import static com.example.platform.typedschema.jooq.generated.tables.UnifiedRequestGraph.UNIFIED_REQUEST_GRAPH;


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
        dsl.insertInto(UNIFIED_REQUEST_GRAPH)
                .columns(
                        UNIFIED_REQUEST_GRAPH.GRAPH_ID,
                        UNIFIED_REQUEST_GRAPH.REQUEST_ID,
                        UNIFIED_REQUEST_GRAPH.TENANT_ID,
                        UNIFIED_REQUEST_GRAPH.WORKSPACE_ID,
                        UNIFIED_REQUEST_GRAPH.JOB_ID,
                        UNIFIED_REQUEST_GRAPH.ROOT_NODE_ID,
                        UNIFIED_REQUEST_GRAPH.STATUS,
                        UNIFIED_REQUEST_GRAPH.CREATED_AT,
                        UNIFIED_REQUEST_GRAPH.COMPLETED_AT
                )
                .values(
                        graph.graphId(),
                        graph.requestId(),
                        graph.tenantId(),
                        graph.workspaceId(),
                        graph.jobId(),
                        graph.rootNodeId(),
                        graph.status().name(),
                        graph.createdAt() != null ? graph.createdAt().atOffset(ZoneOffset.UTC).toLocalDateTime() : null,
                        graph.completedAt() != null ? graph.completedAt().atOffset(ZoneOffset.UTC).toLocalDateTime() : null
                )
                .onConflict(UNIFIED_REQUEST_GRAPH.GRAPH_ID)
                .doUpdate()
                .set(UNIFIED_REQUEST_GRAPH.JOB_ID, graph.jobId())
                .set(UNIFIED_REQUEST_GRAPH.ROOT_NODE_ID, graph.rootNodeId())
                .set(UNIFIED_REQUEST_GRAPH.STATUS, graph.status().name())
                .set(UNIFIED_REQUEST_GRAPH.COMPLETED_AT, graph.completedAt() != null ? graph.completedAt().atOffset(ZoneOffset.UTC).toLocalDateTime() : null)
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
        dsl.insertInto(UNIFIED_GRAPH_NODE)
                .columns(
                        UNIFIED_GRAPH_NODE.NODE_ID,
                        UNIFIED_GRAPH_NODE.GRAPH_ID,
                        UNIFIED_GRAPH_NODE.TYPE,
                        UNIFIED_GRAPH_NODE.SUBSYSTEM,
                        UNIFIED_GRAPH_NODE.ACTION,
                        UNIFIED_GRAPH_NODE.STATUS,
                        UNIFIED_GRAPH_NODE.DATA,
                        UNIFIED_GRAPH_NODE.TIMESTAMP
                )
                .values(
                        node.nodeId(),
                        graphId,
                        node.type().name(),
                        node.subsystem(),
                        node.action(),
                        node.status(),
                        serializeMap(node.data()),
                        node.timestamp().atOffset(ZoneOffset.UTC).toLocalDateTime()
                )
                .onConflict(UNIFIED_GRAPH_NODE.NODE_ID)
                .doUpdate()
                .set(UNIFIED_GRAPH_NODE.STATUS, node.status())
                .set(UNIFIED_GRAPH_NODE.DATA, serializeMap(node.data()))
                .execute();
    }

    /**
     * Save a graph edge.
     */
    private void saveEdge(String graphId, GraphEdge edge) {
        dsl.insertInto(UNIFIED_GRAPH_EDGE)
                .columns(
                        UNIFIED_GRAPH_EDGE.EDGE_ID,
                        UNIFIED_GRAPH_EDGE.GRAPH_ID,
                        UNIFIED_GRAPH_EDGE.SOURCE_NODE_ID,
                        UNIFIED_GRAPH_EDGE.TARGET_NODE_ID,
                        UNIFIED_GRAPH_EDGE.EDGE_TYPE,
                        UNIFIED_GRAPH_EDGE.TIMESTAMP
                )
                .values(
                        edge.edgeId(),
                        graphId,
                        edge.sourceNodeId(),
                        edge.targetNodeId(),
                        edge.edgeType(),
                        edge.timestamp().atOffset(ZoneOffset.UTC).toLocalDateTime()
                )
                .onConflict(UNIFIED_GRAPH_EDGE.EDGE_ID)
                .doNothing()
                .execute();
    }

    /**
     * Load a graph by request ID.
     */
    public Optional<UnifiedRequestGraph> loadByRequestId(String requestId) {
        Record graphRecord = dsl.select(
                        UNIFIED_REQUEST_GRAPH.GRAPH_ID,
                        UNIFIED_REQUEST_GRAPH.REQUEST_ID,
                        UNIFIED_REQUEST_GRAPH.TENANT_ID,
                        UNIFIED_REQUEST_GRAPH.WORKSPACE_ID,
                        UNIFIED_REQUEST_GRAPH.JOB_ID,
                        UNIFIED_REQUEST_GRAPH.ROOT_NODE_ID,
                        UNIFIED_REQUEST_GRAPH.STATUS,
                        UNIFIED_REQUEST_GRAPH.CREATED_AT,
                        UNIFIED_REQUEST_GRAPH.COMPLETED_AT
                )
                .from(UNIFIED_REQUEST_GRAPH)
                .where(UNIFIED_REQUEST_GRAPH.REQUEST_ID.eq(requestId))
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
                        UNIFIED_REQUEST_GRAPH.GRAPH_ID,
                        UNIFIED_REQUEST_GRAPH.REQUEST_ID,
                        UNIFIED_REQUEST_GRAPH.TENANT_ID,
                        UNIFIED_REQUEST_GRAPH.WORKSPACE_ID,
                        UNIFIED_REQUEST_GRAPH.JOB_ID,
                        UNIFIED_REQUEST_GRAPH.ROOT_NODE_ID,
                        UNIFIED_REQUEST_GRAPH.STATUS,
                        UNIFIED_REQUEST_GRAPH.CREATED_AT,
                        UNIFIED_REQUEST_GRAPH.COMPLETED_AT
                )
                .from(UNIFIED_REQUEST_GRAPH)
                .where(UNIFIED_REQUEST_GRAPH.JOB_ID.eq(jobId))
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
                        UNIFIED_GRAPH_NODE.NODE_ID,
                        UNIFIED_GRAPH_NODE.TYPE,
                        UNIFIED_GRAPH_NODE.SUBSYSTEM,
                        UNIFIED_GRAPH_NODE.ACTION,
                        UNIFIED_GRAPH_NODE.STATUS,
                        UNIFIED_GRAPH_NODE.DATA,
                        UNIFIED_GRAPH_NODE.TIMESTAMP
                )
                .from(UNIFIED_GRAPH_NODE)
                .where(UNIFIED_GRAPH_NODE.GRAPH_ID.eq(graphId))
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
                        UNIFIED_GRAPH_EDGE.EDGE_ID,
                        UNIFIED_GRAPH_EDGE.SOURCE_NODE_ID,
                        UNIFIED_GRAPH_EDGE.TARGET_NODE_ID,
                        UNIFIED_GRAPH_EDGE.EDGE_TYPE,
                        UNIFIED_GRAPH_EDGE.TIMESTAMP
                )
                .from(UNIFIED_GRAPH_EDGE)
                .where(UNIFIED_GRAPH_EDGE.GRAPH_ID.eq(graphId))
                .fetch(this::mapToEdge);
    }

    // ---------------------------------------------------------------------------
    // Mapping Helpers
    // ---------------------------------------------------------------------------

    private UnifiedRequestGraph mapToGraph(Record record) {
        String graphId = record.get(UNIFIED_REQUEST_GRAPH.GRAPH_ID);
        Map<String, GraphNode> nodes = loadNodes(graphId);
        List<GraphEdge> edges = loadEdges(graphId);

        LocalDateTime completedAtLdt = record.get(UNIFIED_REQUEST_GRAPH.COMPLETED_AT);
        OffsetDateTime completedAt = completedAtLdt != null ? completedAtLdt.atOffset(ZoneOffset.UTC) : null;

        return new UnifiedRequestGraph(
                graphId,
                record.get(UNIFIED_REQUEST_GRAPH.REQUEST_ID),
                record.get(UNIFIED_REQUEST_GRAPH.TENANT_ID),
                record.get(UNIFIED_REQUEST_GRAPH.WORKSPACE_ID),
                record.get(UNIFIED_REQUEST_GRAPH.JOB_ID),
                nodes,
                edges,
                record.get(UNIFIED_REQUEST_GRAPH.ROOT_NODE_ID),
                UnifiedRequestGraph.GraphStatus.valueOf(record.get(UNIFIED_REQUEST_GRAPH.STATUS)),
                record.get(UNIFIED_REQUEST_GRAPH.CREATED_AT).atOffset(ZoneOffset.UTC).toInstant(),
                completedAt != null ? completedAt.toInstant() : null,
                Map.of()
        );
    }

    private GraphNode mapToNode(Record record) {
        return new GraphNode(
                record.get(UNIFIED_GRAPH_NODE.NODE_ID),
                UnifiedRequestGraph.NodeType.valueOf(record.get(UNIFIED_GRAPH_NODE.TYPE)),
                record.get(UNIFIED_GRAPH_NODE.SUBSYSTEM),
                record.get(UNIFIED_GRAPH_NODE.ACTION),
                record.get(UNIFIED_GRAPH_NODE.STATUS),
                deserializeMap(record.get(UNIFIED_GRAPH_NODE.DATA)),
                record.get(UNIFIED_GRAPH_NODE.TIMESTAMP).atOffset(ZoneOffset.UTC).toInstant(),
                Map.of()
        );
    }

    private GraphEdge mapToEdge(Record record) {
        return new GraphEdge(
                record.get(UNIFIED_GRAPH_EDGE.EDGE_ID),
                record.get(UNIFIED_GRAPH_EDGE.SOURCE_NODE_ID),
                record.get(UNIFIED_GRAPH_EDGE.TARGET_NODE_ID),
                record.get(UNIFIED_GRAPH_EDGE.EDGE_TYPE),
                record.get(UNIFIED_GRAPH_EDGE.TIMESTAMP).atOffset(ZoneOffset.UTC).toInstant(),
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
