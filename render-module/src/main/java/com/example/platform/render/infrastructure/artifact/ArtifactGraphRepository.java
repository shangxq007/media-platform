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
import java.time.OffsetDateTime;
import java.util.*;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

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
        dsl.insertInto(table("artifact_node"))
                .columns(
                        field("id"),
                        field("job_id"),
                        field("type"),
                        field("uri"),
                        field("parent_artifact_ids"),
                        field("version"),
                        field("hash"),
                        field("metadata"),
                        field("created_at")
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
                        node.createdAt().atOffset(java.time.ZoneOffset.UTC)
                )
                .onConflict(field("id"))
                .doUpdate()
                .set(field("uri"), node.uri())
                .set(field("version"), node.version())
                .set(field("hash"), node.hash())
                .set(field("metadata"), serializeMetadata(node.metadata()))
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
        dsl.insertInto(table("artifact_graph"))
                .columns(
                        field("graph_id"),
                        field("job_id"),
                        field("root_artifact_id"),
                        field("version"),
                        field("created_at")
                )
                .values(
                        graph.graphId(),
                        graph.jobId(),
                        graph.rootArtifactId(),
                        graph.version(),
                        graph.createdAt().atOffset(java.time.ZoneOffset.UTC)
                )
                .onConflict(field("graph_id"))
                .doUpdate()
                .set(field("root_artifact_id"), graph.rootArtifactId())
                .set(field("version"), graph.version())
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
                        field("graph_id"),
                        field("job_id"),
                        field("root_artifact_id"),
                        field("version"),
                        field("created_at")
                )
                .from(table("artifact_graph"))
                .where(field("job_id").eq(jobId))
                .orderBy(field("version").desc())
                .limit(1)
                .fetchOne();

        if (graphRecord == null) {
            return Optional.empty();
        }

        String graphId = graphRecord.get(field("graph_id", String.class));
        String rootArtifactId = graphRecord.get(field("root_artifact_id", String.class));
        int version = graphRecord.get(field("version", Integer.class));
        Instant createdAt = graphRecord.get(field("created_at", OffsetDateTime.class)).toInstant();

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
                        field("id"),
                        field("job_id"),
                        field("type"),
                        field("uri"),
                        field("parent_artifact_ids"),
                        field("version"),
                        field("hash"),
                        field("metadata"),
                        field("created_at")
                )
                .from(table("artifact_node"))
                .where(field("job_id").eq(jobId))
                .fetch(this::mapToNode);
    }

    /**
     * Load a single node by ID.
     */
    public Optional<ArtifactNode> loadNodeById(String nodeId) {
        Record record = dsl.select(
                        field("id"),
                        field("job_id"),
                        field("type"),
                        field("uri"),
                        field("parent_artifact_ids"),
                        field("version"),
                        field("hash"),
                        field("metadata"),
                        field("created_at")
                )
                .from(table("artifact_node"))
                .where(field("id").eq(nodeId))
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
                        field("id"),
                        field("job_id"),
                        field("type"),
                        field("uri"),
                        field("parent_artifact_ids"),
                        field("version"),
                        field("hash"),
                        field("metadata"),
                        field("created_at")
                )
                .from(table("artifact_node"))
                .where(field("hash").eq(hash))
                .fetch(this::mapToNode);
    }

    /**
     * Delete all nodes for a job.
     */
    public void deleteByJobId(String jobId) {
        dsl.deleteFrom(table("artifact_node"))
                .where(field("job_id").eq(jobId))
                .execute();

        dsl.deleteFrom(table("artifact_graph"))
                .where(field("job_id").eq(jobId))
                .execute();
    }

    /**
     * Check if a node exists.
     */
    public boolean exists(String nodeId) {
        return dsl.selectCount()
                .from(table("artifact_node"))
                .where(field("id").eq(nodeId))
                .fetchOne(0, int.class) > 0;
    }

    // --- Private helpers ---

    private ArtifactNode mapToNode(Record record) {
        String parentIdsStr = record.get(field("parent_artifact_ids", String.class));
        List<String> parentIds = parentIdsStr != null && !parentIdsStr.isEmpty()
                ? List.of(parentIdsStr.split(","))
                : List.of();

        return new ArtifactNode(
                record.get(field("id", String.class)),
                record.get(field("job_id", String.class)),
                ArtifactNodeType.valueOf(record.get(field("type", String.class))),
                record.get(field("uri", String.class)),
                parentIds,
                record.get(field("created_at", OffsetDateTime.class)).toInstant(),
                record.get(field("version", Integer.class)),
                record.get(field("hash", String.class)),
                deserializeMetadata(record.get(field("metadata", String.class)))
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
