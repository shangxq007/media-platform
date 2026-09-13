package com.example.platform.delivery.app;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import static com.example.platform.typedschema.jooq.generated.tables.DeliveryJob.DELIVERY_JOB;


/**
 * Reverse lookup: remote URI or Artifact-owned source placement → delivery jobs that reference it.
 */
@Service

public class DeliveryRemoteUriIndexService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryRemoteUriIndexService.class);

    private final DSLContext dsl;
    private final com.example.platform.artifact.app.ArtifactOutputReferenceIndex artifacts;

    public DeliveryRemoteUriIndexService(DSLContext dsl, com.example.platform.artifact.app.ArtifactOutputReferenceIndex artifacts) {
        this.dsl = dsl;
        this.artifacts = artifacts;
    }

    public List<DeliveryUriHit> findByRemoteUri(String remoteUri, String projectId, int limit) {
        return findByUriColumn("remote_uri", remoteUri, projectId, limit);
    }

    public List<DeliveryUriHit> findByAnyUri(String storageUri,String projectId,int limit) {
        return findByUriColumn("artifact_or_remote",storageUri,projectId,limit);
    }

    private List<DeliveryUriHit> findByUriColumn(String column, String uri, String projectId, int limit) {
        List<DeliveryUriHit> hits = new ArrayList<>();
        if (uri == null || uri.isBlank()) {
            return hits;
        }
        int cap = Math.min(Math.max(limit, 1), 100);
        try {
            var condition = DELIVERY_JOB.REMOTE_URI.eq(uri);
            if(!"remote_uri".equals(column))condition=condition.or(DELIVERY_JOB.ARTIFACT_ID.in(artifacts.findByStorageUri(uri,projectId,cap).stream().map(ref->ref.artifactId().value()).toList()));
            if (projectId != null && !projectId.isBlank()) {
                condition = condition.and(DELIVERY_JOB.PROJECT_ID.eq(projectId));
            }
            var rows = dsl.select(
                            DELIVERY_JOB.ID,
                            DELIVERY_JOB.TENANT_ID,
                            DELIVERY_JOB.PROJECT_ID,
                            DELIVERY_JOB.RENDER_JOB_ID,
                            DELIVERY_JOB.STATUS,
                            DELIVERY_JOB.ARTIFACT_ID,
                            DELIVERY_JOB.REMOTE_URI,
                            DELIVERY_JOB.CREATED_AT)
                    .from(DELIVERY_JOB)
                    .where(condition)
                    .orderBy(DELIVERY_JOB.CREATED_AT.desc())
                    .limit(cap)
                    .fetch();
            for (Record row : rows) {
                hits.add(new DeliveryUriHit(
                        row.get(DELIVERY_JOB.ID),
                        row.get(DELIVERY_JOB.TENANT_ID),
                        row.get(DELIVERY_JOB.PROJECT_ID),
                        row.get(DELIVERY_JOB.RENDER_JOB_ID),
                        row.get(DELIVERY_JOB.STATUS),
                        row.get(DELIVERY_JOB.ARTIFACT_ID),
                        row.get(DELIVERY_JOB.REMOTE_URI),
                        uri.equals(row.get(DELIVERY_JOB.REMOTE_URI)) ? "remote_uri" : "artifact_id",
                        row.get(DELIVERY_JOB.CREATED_AT)));
            }
        } catch (DataAccessException e) {
            log.debug("delivery_job URI index lookup skipped: {}", e.getMessage());
        }
        return hits;
    }

    public record DeliveryUriHit(
            String deliveryJobId,
            String tenantId,
            String projectId,
            String renderJobId,
            String status,
            String artifactId,
            String remoteUri,
            String matchedColumn,
            LocalDateTime createdAt) {}
}
