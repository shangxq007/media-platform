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
 * Reverse lookup: remote (or source) storage URI → delivery jobs that reference it.
 */
@Service

public class DeliveryRemoteUriIndexService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryRemoteUriIndexService.class);

    private final DSLContext dsl;

    public DeliveryRemoteUriIndexService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<DeliveryUriHit> findByRemoteUri(String remoteUri, String projectId, int limit) {
        return findByUriColumn("remote_uri", remoteUri, projectId, limit);
    }

    public List<DeliveryUriHit> findBySourceUri(String sourceUri, String projectId, int limit) {
        return findByUriColumn("source_uri", sourceUri, projectId, limit);
    }

    public List<DeliveryUriHit> findByAnyUri(String storageUri, String projectId, int limit) {
        if (storageUri == null || storageUri.isBlank()) {
            return List.of();
        }
        int cap = Math.min(Math.max(limit, 1), 100);
        List<DeliveryUriHit> hits = new ArrayList<>();
        hits.addAll(findByRemoteUri(storageUri, projectId, cap));
        if (hits.size() < cap) {
            for (DeliveryUriHit hit : findBySourceUri(storageUri, projectId, cap - hits.size())) {
                if (hits.stream().noneMatch(h -> h.deliveryJobId().equals(hit.deliveryJobId()))) {
                    hits.add(hit);
                }
            }
        }
        return hits;
    }

    private List<DeliveryUriHit> findByUriColumn(String column, String uri, String projectId, int limit) {
        List<DeliveryUriHit> hits = new ArrayList<>();
        if (uri == null || uri.isBlank()) {
            return hits;
        }
        int cap = Math.min(Math.max(limit, 1), 100);
        try {
            var condition = DSL.field(DSL.name(column)).eq(uri);
            if (projectId != null && !projectId.isBlank()) {
                condition = condition.and(DELIVERY_JOB.PROJECT_ID.eq(projectId));
            }
            var rows = dsl.select(
                            DELIVERY_JOB.ID,
                            DELIVERY_JOB.TENANT_ID,
                            DELIVERY_JOB.PROJECT_ID,
                            DELIVERY_JOB.RENDER_JOB_ID,
                            DELIVERY_JOB.STATUS,
                            DELIVERY_JOB.SOURCE_URI,
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
                        row.get(DELIVERY_JOB.SOURCE_URI),
                        row.get(DELIVERY_JOB.REMOTE_URI),
                        column,
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
            String sourceUri,
            String remoteUri,
            String matchedColumn,
            LocalDateTime createdAt) {}
}
