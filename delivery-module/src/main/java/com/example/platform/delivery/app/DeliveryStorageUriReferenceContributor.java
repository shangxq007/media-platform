package com.example.platform.delivery.app;

import com.example.platform.shared.asset.StorageUriReferenceContributor;
import com.example.platform.shared.asset.StorageUriReferenceHit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Component
@ConditionalOnBean(DSLContext.class)
public class DeliveryStorageUriReferenceContributor implements StorageUriReferenceContributor {

    private static final Logger log = LoggerFactory.getLogger(DeliveryStorageUriReferenceContributor.class);

    private final DSLContext dsl;

    public DeliveryStorageUriReferenceContributor(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public String contributorId() {
        return "delivery";
    }

    @Override
    public List<StorageUriReferenceHit> findReferences(String storageUri, String projectId) {
        List<StorageUriReferenceHit> hits = new ArrayList<>();
        if (storageUri == null || storageUri.isBlank()) {
            return hits;
        }
        try {
            var condition = field("source_uri").eq(storageUri).or(field("remote_uri").eq(storageUri));
            if (projectId != null && !projectId.isBlank()) {
                condition = condition.and(field("project_id").eq(projectId));
            }
            var rows = dsl.select(
                            field("id", String.class),
                            field("project_id", String.class),
                            field("render_job_id", String.class),
                            field("status", String.class),
                            field("source_uri", String.class),
                            field("remote_uri", String.class))
                    .from(table("delivery_job"))
                    .where(condition)
                    .limit(50)
                    .fetch();
            for (Record row : rows) {
                Map<String, String> details = new LinkedHashMap<>();
                details.put("projectId", row.get(field("project_id", String.class)));
                details.put("renderJobId", row.get(field("render_job_id", String.class)));
                details.put("status", row.get(field("status", String.class)));
                details.put("sourceUri", row.get(field("source_uri", String.class)));
                String remote = row.get(field("remote_uri", String.class));
                if (remote != null) {
                    details.put("remoteUri", remote);
                }
                hits.add(new StorageUriReferenceHit(
                        "delivery_job",
                        row.get(field("id", String.class)),
                        "Delivery job references storage URI",
                        details));
            }
        } catch (DataAccessException e) {
            log.debug("delivery_job reference scan skipped: {}", e.getMessage());
        }
        return hits;
    }
}
