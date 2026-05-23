package com.example.platform.delivery.app;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * Reverse lookup: remote (or source) storage URI → delivery jobs that reference it.
 */
@Service
@ConditionalOnBean(DSLContext.class)
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
            var condition = field(column).eq(uri);
            if (projectId != null && !projectId.isBlank()) {
                condition = condition.and(field("project_id").eq(projectId));
            }
            var rows = dsl.select(
                            field("id", String.class),
                            field("tenant_id", String.class),
                            field("project_id", String.class),
                            field("render_job_id", String.class),
                            field("status", String.class),
                            field("source_uri", String.class),
                            field("remote_uri", String.class),
                            field("created_at", OffsetDateTime.class))
                    .from(table("delivery_job"))
                    .where(condition)
                    .orderBy(field("created_at").desc())
                    .limit(cap)
                    .fetch();
            for (Record row : rows) {
                hits.add(new DeliveryUriHit(
                        row.get(field("id", String.class)),
                        row.get(field("tenant_id", String.class)),
                        row.get(field("project_id", String.class)),
                        row.get(field("render_job_id", String.class)),
                        row.get(field("status", String.class)),
                        row.get(field("source_uri", String.class)),
                        row.get(field("remote_uri", String.class)),
                        column,
                        row.get(field("created_at", OffsetDateTime.class))));
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
            OffsetDateTime createdAt) {}
}
