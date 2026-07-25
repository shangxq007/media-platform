package com.example.platform.render.infrastructure.artifact;

import com.example.platform.render.domain.artifact.ArtifactGraph;
import com.example.platform.render.domain.artifact.ArtifactNode;
import com.example.platform.render.domain.artifact.ArtifactNodeType;
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
import static com.example.platform.typedschema.jooq.generated.tables.ArtifactGraph.ARTIFACT_GRAPH;
import static com.example.platform.typedschema.jooq.generated.tables.ArtifactNode.ARTIFACT_NODE;


/**
 * Repository for artifact DAG nodes.
 * Stores and retrieves ArtifactNode records from the artifact_node table.
 */
@Repository
public class ArtifactGraphRepository {

    private static final Logger log = LoggerFactory.getLogger(ArtifactGraphRepository.class);

    private final DSLContext dsl;

    public ArtifactGraphRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Save an artifact node.
     */
    public void saveNode(ArtifactNode node) {
        dsl.insertInto(ARTIFACT_NODE)
                .columns(
                        ARTIFACT_NODE.ID,
                        ARTIFACT_NODE.JOB_ID,
                        ARTIFACT_NODE.TYPE,
                        ARTIFACT_NODE.URI,
                        ARTIFACT_NODE.PARENT_ARTIFACT_IDS,
                        ARTIFACT_NODE.VERSION,
                        ARTIFACT_NODE.HASH,
                        ARTIFACT_NODE.METADATA,
                        ARTIFACT_NODE.CREATED_AT
                )
                .values(
                        node.id(),
                        node.jobId(),
                        node.type().name(),
                        node.uri(),
                        String.join(",", node.parentArtifactIds()),
                        node.version(),
                        node.hash(),
                        serializeMetadata(node.metadata()),
                        java.time.LocalDateTime.ofInstant(node.createdAt(), java.time.ZoneOffset.UTC)
                )
                .onConflict(ARTIFACT_NODE.ID)
                .doUpdate()
                .set(ARTIFACT_NODE.URI, node.uri())
                .set(ARTIFACT_NODE.VERSION, node.version())
                .set(ARTIFACT_NODE.HASH, node.hash())
                .set(ARTIFACT_NODE.METADATA, serializeMetadata(node.metadata()))
                .execute();

        log.debug("Saved artifact node: id={} jobId={} type={}", node.id(), node.jobId(), node.type());
    }

    /**
     * Save multiple artifact nodes in batch.
     */
    public void saveNodes(List<ArtifactNode> nodes) {
        for (ArtifactNode node : nodes) {
            saveNode(node);
        }
    }

    /**
     * Save an artifact graph (all nodes).
     */
    public void saveGraph(ArtifactGraph graph) {
        saveNodes(graph.toList());

        // Also save the graph metadata
        dsl.insertInto(ARTIFACT_GRAPH)
                .columns(
                        ARTIFACT_GRAPH.GRAPH_ID,
                        ARTIFACT_GRAPH.JOB_ID,
                        ARTIFACT_GRAPH.ROOT_ARTIFACT_ID,
                        ARTIFACT_GRAPH.VERSION,
                        ARTIFACT_GRAPH.CREATED_AT
                )
                .values(
                        graph.graphId(),
                        graph.jobId(),
                        graph.rootArtifactId(),
                        graph.version(),
                        LocalDateTime.ofInstant(graph.createdAt(), java.time.ZoneOffset.UTC)
                )
                .onConflict(ARTIFACT_GRAPH.GRAPH_ID)
                .doUpdate()
                .set(ARTIFACT_GRAPH.ROOT_ARTIFACT_ID, graph.rootArtifactId())
                .set(ARTIFACT_GRAPH.VERSION, graph.version())
                .execute();

        log.info("Saved artifact graph: graphId={} jobId={} nodes={}",
                graph.graphId(), graph.jobId(), graph.size());
    }

    /**
     * Load an artifact graph by job ID.
     */
    public Optional<ArtifactGraph> loadGraphByJobId(String jobId) {
        // Load graph metadata
        Record graphRecord = dsl.select(
                        ARTIFACT_GRAPH.GRAPH_ID,
                        ARTIFACT_GRAPH.JOB_ID,
                        ARTIFACT_GRAPH.ROOT_ARTIFACT_ID,
                        ARTIFACT_GRAPH.VERSION,
                        ARTIFACT_GRAPH.CREATED_AT
                )
                .from(ARTIFACT_GRAPH)
                .where(ARTIFACT_GRAPH.JOB_ID.eq(jobId))
                .orderBy(ARTIFACT_GRAPH.VERSION.desc())
                .limit(1)
                .fetchOne();

        if (graphRecord == null) {
            return Optional.empty();
        }

        String graphId = graphRecord.get(ARTIFACT_GRAPH.GRAPH_ID);
        String rootArtifactId = graphRecord.get(ARTIFACT_GRAPH.ROOT_ARTIFACT_ID);
        int version = graphRecord.get(ARTIFACT_GRAPH.VERSION);
        Instant createdAt = graphRecord.get(ARTIFACT_GRAPH.CREATED_AT).toInstant(ZoneOffset.UTC);

        // Load all nodes for this job
        List<ArtifactNode> nodes = loadNodesByJobId(jobId);

        Map<String, ArtifactNode> nodeMap = new HashMap<>();
        for (ArtifactNode node : nodes) {
            nodeMap.put(node.id(), node);
        }

        return Optional.of(new ArtifactGraph(
                graphId,
                jobId,
                rootArtifactId,
                Map.copyOf(nodeMap),
                createdAt,
                version
        ));
    }

    /**
     * Load all artifact nodes for a job.
     */
    public List<ArtifactNode> loadNodesByJobId(String jobId) {
        return dsl.select(
                        ARTIFACT_NODE.ID,
                        ARTIFACT_NODE.JOB_ID,
                        ARTIFACT_NODE.TYPE,
                        ARTIFACT_NODE.URI,
                        ARTIFACT_NODE.PARENT_ARTIFACT_IDS,
                        ARTIFACT_GRAPH.VERSION,
                        ARTIFACT_NODE.HASH,
                        ARTIFACT_NODE.METADATA,
                        ARTIFACT_GRAPH.CREATED_AT
                )
                .from(ARTIFACT_NODE)
                .where(ARTIFACT_NODE.JOB_ID.eq(jobId))
                .fetch(this::mapToNode);
    }

    /**
     * Load a single node by ID.
     */
    public Optional<ArtifactNode> loadNodeById(String nodeId) {
        Record record = dsl.select(
                        ARTIFACT_NODE.ID,
                        ARTIFACT_NODE.JOB_ID,
                        ARTIFACT_NODE.TYPE,
                        ARTIFACT_NODE.URI,
                        ARTIFACT_NODE.PARENT_ARTIFACT_IDS,
                        ARTIFACT_GRAPH.VERSION,
                        ARTIFACT_NODE.HASH,
                        ARTIFACT_NODE.METADATA,
                        ARTIFACT_GRAPH.CREATED_AT
                )
                .from(ARTIFACT_NODE)
                .where(ARTIFACT_NODE.ID.eq(nodeId))
                .fetchOne();

        if (record == null) {
            return Optional.empty();
        }

        return Optional.of(mapToNode(record));
    }

    /**
     * Find artifact nodes by hash (for deduplication).
     */
    public List<ArtifactNode> loadNodesByHash(String hash) {
        return dsl.select(
                        ARTIFACT_NODE.ID,
                        ARTIFACT_NODE.JOB_ID,
                        ARTIFACT_NODE.TYPE,
                        ARTIFACT_NODE.URI,
                        ARTIFACT_NODE.PARENT_ARTIFACT_IDS,
                        ARTIFACT_GRAPH.VERSION,
                        ARTIFACT_NODE.HASH,
                        ARTIFACT_NODE.METADATA,
                        ARTIFACT_GRAPH.CREATED_AT
                )
                .from(ARTIFACT_NODE)
                .where(ARTIFACT_NODE.HASH.eq(hash))
                .fetch(this::mapToNode);
    }

    /**
     * Delete all nodes for a job.
     */
    public void deleteByJobId(String jobId) {
        dsl.deleteFrom(ARTIFACT_NODE)
                .where(ARTIFACT_NODE.JOB_ID.eq(jobId))
                .execute();

        dsl.deleteFrom(ARTIFACT_GRAPH)
                .where(ARTIFACT_GRAPH.JOB_ID.eq(jobId))
                .execute();
    }

    /**
     * Check if a node exists.
     */
    public boolean exists(String nodeId) {
        return dsl.selectCount()
                .from(ARTIFACT_NODE)
                .where(ARTIFACT_NODE.ID.eq(nodeId))
                .fetchOne(0, int.class) > 0;
    }

    // --- Private helpers ---

    private ArtifactNode mapToNode(Record record) {
        String parentIdsStr = record.get(ARTIFACT_NODE.PARENT_ARTIFACT_IDS);
        List<String> parentIds = parentIdsStr != null && !parentIdsStr.isEmpty()
                ? List.of(parentIdsStr.split(","))
                : List.of();

        return new ArtifactNode(
                record.get(ARTIFACT_NODE.ID),
                record.get(ARTIFACT_NODE.JOB_ID),
                ArtifactNodeType.valueOf(record.get(ARTIFACT_NODE.TYPE)),
                record.get(ARTIFACT_NODE.URI),
                parentIds,
                record.get(ARTIFACT_NODE.CREATED_AT).toInstant(ZoneOffset.UTC),
                record.get(ARTIFACT_NODE.VERSION),
                record.get(ARTIFACT_NODE.HASH),
                deserializeMetadata(record.get(ARTIFACT_NODE.METADATA))
        );
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        // Simple JSON serialization (in production, use Jackson)
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private Map<String, Object> deserializeMetadata(String json) {
        if (json == null || json.isEmpty() || json.equals("{}")) {
            return Map.of();
        }
        // Simple deserialization (in production, use Jackson)
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
