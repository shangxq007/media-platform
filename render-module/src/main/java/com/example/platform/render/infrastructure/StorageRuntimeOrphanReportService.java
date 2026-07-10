package com.example.platform.render.infrastructure;
import java.nio.file.Path;
import java.nio.file.Files;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

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
            var results = dsl.select(
                    DSL.field("p.id"),
                    DSL.field("p.status"),
                    DSL.field("p.storage_reference_id"))
                .from(DSL.table("product").as("p"))
                .where(DSL.field("p.storage_reference_id").isNotNull())
                .and(DSL.field("p.storage_reference_id").notIn(
                    dsl.select(DSL.field("id")).from(DSL.table("storage_reference"))))
                .limit(limit)
                .fetch();

            for (var row : results) {
                Map<String, Object> issue = new LinkedHashMap<>();
                issue.put("issueType", "PRODUCT_STORAGE_REFERENCE_MISSING");
                issue.put("severity", "HIGH");
                issue.put("entityType", "Product");
                issue.put("entityId", row.get("id"));
                issue.put("status", row.get("status"));
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
            var results = dsl.select(DSL.field("id"))
                .from(DSL.table("render_job"))
                .where(DSL.field("status").eq("COMPLETED"))
                .and(DSL.field("output_product_id").isNull())
                .limit(limit)
                .fetch();

            for (var row : results) {
                Map<String, Object> issue = new LinkedHashMap<>();
                issue.put("issueType", "COMPLETED_RENDER_JOB_WITHOUT_OUTPUT_PRODUCT");
                issue.put("severity", "MEDIUM");
                issue.put("entityType", "RenderJob");
                issue.put("entityId", row.get("id"));
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
            var results = dsl.select(
                    DSL.field("rj.id"),
                    DSL.field("rj.output_product_id"))
                .from(DSL.table("render_job").as("rj"))
                .where(DSL.field("rj.output_product_id").isNotNull())
                .and(DSL.field("rj.output_product_id").notIn(
                    dsl.select(DSL.field("id")).from(DSL.table("product"))))
                .limit(limit)
                .fetch();

            for (var row : results) {
                Map<String, Object> issue = new LinkedHashMap<>();
                issue.put("issueType", "RENDER_JOB_OUTPUT_PRODUCT_MISSING");
                issue.put("severity", "HIGH");
                issue.put("entityType", "RenderJob");
                issue.put("entityId", row.get("id"));
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
            var refs = dsl.select(
                    DSL.field("sr.id"),
                    DSL.field("sr.storage_path"))
                .from(DSL.table("storage_reference").as("sr"))
                .join(DSL.table("product").as("p"))
                .on(DSL.field("p.storage_reference_id").eq(DSL.field("sr.id")))
                .limit(limit)
                .fetch();

            for (var row : refs) {
                checked++;
                String refId = row.get("id", String.class);
                String storagePath = row.get("storage_path", String.class);

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