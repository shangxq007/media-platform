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

import org.springframework.stereotype.Component;
import static com.example.platform.typedschema.jooq.generated.tables.DeliveryJob.DELIVERY_JOB;


@Component

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
            var condition = DELIVERY_JOB.SOURCE_URI.eq(storageUri).or(DELIVERY_JOB.REMOTE_URI.eq(storageUri));
            if (projectId != null && !projectId.isBlank()) {
                condition = condition.and(DELIVERY_JOB.PROJECT_ID.eq(projectId));
            }
            var rows = dsl.select(
                            DELIVERY_JOB.ID,
                            DELIVERY_JOB.PROJECT_ID,
                            DELIVERY_JOB.RENDER_JOB_ID,
                            DELIVERY_JOB.STATUS,
                            DELIVERY_JOB.SOURCE_URI,
                            DELIVERY_JOB.REMOTE_URI)
                    .from(DELIVERY_JOB)
                    .where(condition)
                    .limit(50)
                    .fetch();
            for (Record row : rows) {
                Map<String, String> details = new LinkedHashMap<>();
                details.put("projectId", row.get(DELIVERY_JOB.PROJECT_ID));
                details.put("renderJobId", row.get(DELIVERY_JOB.RENDER_JOB_ID));
                details.put("status", row.get(DELIVERY_JOB.STATUS));
                details.put("sourceUri", row.get(DELIVERY_JOB.SOURCE_URI));
                String remote = row.get(DELIVERY_JOB.REMOTE_URI);
                if (remote != null) {
                    details.put("remoteUri", remote);
                }
                hits.add(new StorageUriReferenceHit(
                        "delivery_job",
                        row.get(DELIVERY_JOB.ID),
                        "Delivery job references storage URI",
                        details));
            }
        } catch (DataAccessException e) {
            log.debug("delivery_job reference scan skipped: {}", e.getMessage());
        }
        return hits;
    }
}
