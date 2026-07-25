package com.example.platform.render.infrastructure;

import java.nio.file.Path;
import java.nio.file.Files;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import static com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT;
import static com.example.platform.typedschema.jooq.generated.tables.RenderJob.RENDER_JOB;
import static com.example.platform.typedschema.jooq.generated.tables.RenderJobLifecycleEvents.RENDER_JOB_LIFECYCLE_EVENTS;
import static com.example.platform.typedschema.jooq.generated.tables.StorageReference.STORAGE_REFERENCE;


/**
 * Report-only service for StorageRuntime/Product/Artifact/RenderJob consistency.
 *
 * NEVER deletes anything. NEVER mutates state.
 * Only reports inconsistencies for operational visibility.
 */
@Service
public class StorageRuntimeOrphanReportService {

    private static final Logger log = LoggerFactory.getLogger(StorageRuntimeOrphanReportService.class);

    private final DSLContext dsl;

    public StorageRuntimeOrphanReportService(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Generate consistency report. Report-only, never deletes.
     */
    public Map<String, Object> generateReport(int limit) {
        Instant now = Instant.now();
        List<Map<String, Object>> issues = new ArrayList<>();

        // Check Products with missing storage references
        checkProductStorageReferences(issues, limit);

        // Check COMPLETED RenderJobs without outputProductId
        checkCompletedRenderJobsWithoutOutput(issues, limit);

        // Check RenderJobs with outputProductId pointing to missing Products
        checkRenderJobOutputProducts(issues, limit);

        // Summarize
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("generatedAt", now.toString());
        summary.put("reportOnly", true);
        summary.put("destructive", false);
        summary.put("limit", limit);
        summary.put("issueCount", issues.size());
        summary.put("issues", issues);
        return summary;
    }

    private void checkProductStorageReferences(List<Map<String, Object>> issues, int limit) {
        try {
            // Find Products with storageReferenceId but no matching StorageReference
            var p = PRODUCT.as("p");
            var results = dsl.select(
                    p.PRODUCT_ID,
                    p.STATUS,
                    p.STORAGE_REFERENCE_ID)
                .from(p)
                .where(p.STORAGE_REFERENCE_ID.isNotNull())
                .and(p.STORAGE_REFERENCE_ID.notIn(
                    dsl.select(STORAGE_REFERENCE.STORAGE_REFERENCE_ID).from(STORAGE_REFERENCE)))
                .limit(limit)
                .fetch();

            for (var row : results) {
                Map<String, Object> issue = new LinkedHashMap<>();
                issue.put("issueType", "PRODUCT_STORAGE_REFERENCE_MISSING");
                issue.put("severity", "HIGH");
                issue.put("entityType", "Product");
                issue.put("entityId", row.get(p.PRODUCT_ID));
                issue.put("status", row.get(p.STATUS));
                issue.put("message", "Product references missing StorageReference");
                issue.put("recommendedAction", "Investigate Product/StorageReference relationship");
                issue.put("safeToAutoDelete", false);
                issues.add(issue);
            }
        } catch (Exception e) {
            log.debug("Product storage reference check skipped: {}", e.getMessage());
        }
    }

    private void checkCompletedRenderJobsWithoutOutput(List<Map<String, Object>> issues, int limit) {
        try {
            // COMPLETED RenderJobs where no lifecycle event has a non-null output_product_id
            var results = dsl.select(RENDER_JOB.ID)
                .from(RENDER_JOB)
                .where(RENDER_JOB.STATUS.eq("COMPLETED"))
                .and(RENDER_JOB.ID.notIn(
                    dsl.select(RENDER_JOB_LIFECYCLE_EVENTS.RENDER_JOB_ID)
                        .from(RENDER_JOB_LIFECYCLE_EVENTS)
                        .where(RENDER_JOB_LIFECYCLE_EVENTS.OUTPUT_PRODUCT_ID.isNotNull())))
                .limit(limit)
                .fetch();

            for (var row : results) {
                Map<String, Object> issue = new LinkedHashMap<>();
                issue.put("issueType", "COMPLETED_RENDER_JOB_WITHOUT_OUTPUT_PRODUCT");
                issue.put("severity", "MEDIUM");
                issue.put("entityType", "RenderJob");
                issue.put("entityId", row.get(RENDER_JOB.ID));
                issue.put("message", "COMPLETED RenderJob has no outputProductId");
                issue.put("recommendedAction", "Verify if output was created correctly");
                issue.put("safeToAutoDelete", false);
                issues.add(issue);
            }
        } catch (Exception e) {
            log.debug("RenderJob output check skipped: {}", e.getMessage());
        }
    }

    private void checkRenderJobOutputProducts(List<Map<String, Object>> issues, int limit) {
        try {
            // RenderJobs where outputProductId points to a non-existent Product
            var results = dsl.select(
                    RENDER_JOB.ID,
                    RENDER_JOB_LIFECYCLE_EVENTS.OUTPUT_PRODUCT_ID)
                .from(RENDER_JOB)
                .join(RENDER_JOB_LIFECYCLE_EVENTS).on(RENDER_JOB.ID.eq(RENDER_JOB_LIFECYCLE_EVENTS.RENDER_JOB_ID))
                .where(RENDER_JOB_LIFECYCLE_EVENTS.OUTPUT_PRODUCT_ID.isNotNull())
                .and(RENDER_JOB_LIFECYCLE_EVENTS.OUTPUT_PRODUCT_ID.notIn(
                    dsl.select(PRODUCT.PRODUCT_ID).from(PRODUCT)))
                .limit(limit)
                .fetch();

            for (var row : results) {
                Map<String, Object> issue = new LinkedHashMap<>();
                issue.put("issueType", "RENDER_JOB_OUTPUT_PRODUCT_MISSING");
                issue.put("severity", "HIGH");
                issue.put("entityType", "RenderJob");
                issue.put("entityId", row.get(RENDER_JOB.ID));
                issue.put("message", "RenderJob outputProductId points to missing Product");
                issue.put("recommendedAction", "Investigate Product deletion or RenderJob corruption");
                issue.put("safeToAutoDelete", false);
                issues.add(issue);
            }
        } catch (Exception e) {
            log.debug("RenderJob output product check skipped: {}", e.getMessage());
        }
    }


    /**
     * Check physical existence of objects referenced by StorageReferences.
     * Report-only, never deletes or mutates.
     */
    public Map<String, Object> generatePhysicalReport(Path storageRoot, int limit) {
        Instant now = Instant.now();
        List<Map<String, Object>> issues = new ArrayList<>();
        int checked = 0;
        int found = 0;
        int missing = 0;

        // Get StorageReference IDs from Products
        try {
            var sr = STORAGE_REFERENCE.as("sr");
            var p = PRODUCT.as("p");
            var refs = dsl.select(
                    sr.STORAGE_REFERENCE_ID,
                    sr.RELATIVE_PATH)
                .from(sr)
                .join(p)
                .on(p.STORAGE_REFERENCE_ID.eq(sr.STORAGE_REFERENCE_ID))
                .limit(limit)
                .fetch();

            for (var row : refs) {
                checked++;
                String refId = row.get(sr.STORAGE_REFERENCE_ID);
                String storagePath = row.get(sr.RELATIVE_PATH);

                if (storagePath != null) {
                    Path filePath = storageRoot.resolve(storagePath);
                    if (Files.exists(filePath) && !Files.isSymbolicLink(filePath)) {
                        found++;
                    } else {
                        missing++;
                        Map<String, Object> issue = new LinkedHashMap<>();
                        issue.put("issueType", "STORAGE_OBJECT_MISSING");
                        issue.put("severity", "HIGH");
                        issue.put("entityType", "StorageReference");
                        issue.put("entityId", refId);
                        issue.put("message", "Referenced storage object does not exist");
                        issue.put("recommendedAction", "Investigate missing storage object");
                        issue.put("safeToAutoDelete", false);
                        issue.put("destructiveActionAvailable", false);
                        issues.add(issue);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Physical check skipped: {}", e.getMessage());
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("generatedAt", now.toString());
        summary.put("reportOnly", true);
        summary.put("destructive", false);
        summary.put("physicalChecksEnabled", true);
        summary.put("storageRoot", storageRoot.toString());
        summary.put("limit", limit);
        summary.put("referencesChecked", checked);
        summary.put("objectsFound", found);
        summary.put("objectsMissing", missing);
        summary.put("issueCount", issues.size());
        summary.put("issues", issues);
        return summary;
    }
}
