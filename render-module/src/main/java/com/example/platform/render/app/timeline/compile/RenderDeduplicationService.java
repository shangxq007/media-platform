package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.timeline.TimelineRevisionRenderService;
import com.example.platform.render.domain.product.Product;
import com.example.platform.render.domain.product.ProductStatus;
import com.example.platform.render.domain.product.ProductType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Deduplication service for render requests.
 *
 * <p>Internal only — checks for existing READY Products before starting
 * a new render. Prevents duplicate renders for the same logical request.</p>
 *
 * <p>v0 behavior:
 * <ul>
 *   <li>If exact READY Product exists for same revision + project + profile, reuse it</li>
 *   <li>If previous failed render exists, allow retry</li>
 *   <li>If no existing render found, proceed with new render</li>
 *   <li>In-progress detection is reserved for future (no render job status table in v0)</li>
 * </ul>
 */
@Service
public class RenderDeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(RenderDeduplicationService.class);

    private final ProductRuntimeService productRuntime;

    public RenderDeduplicationService(ProductRuntimeService productRuntime) {
        this.productRuntime = productRuntime;
    }

    /**
     * Check for existing render result before starting a new render.
     *
     * @param projectId          the project identifier
     * @param timelineRevisionId the timeline revision identifier
     * @param outputProfile      the normalized output profile
     * @param executionMode      the execution mode
     * @return the dedup decision
     */
    public RenderDeduplicationDecision check(
            String projectId, String timelineRevisionId,
            String outputProfile, String executionMode) {

        RenderRequestFingerprint fingerprint = RenderRequestFingerprint.generate(
                projectId, timelineRevisionId, outputProfile, executionMode);

        try {
            // Query products by sourceTimelineRevisionId
            List<Product> candidates = productRuntime.findBySourceTimelineRevisionId(timelineRevisionId);

            // Filter: same project, FINAL_RENDER type, READY status
            List<Product> readyMatches = candidates.stream()
                    .filter(p -> projectId.equals(p.projectId()))
                    .filter(p -> p.productType() == ProductType.FINAL_RENDER)
                    .filter(p -> p.status() == ProductStatus.READY)
                    .filter(p -> matchesOutputProfile(p, outputProfile))
                    .toList();

            if (!readyMatches.isEmpty()) {
                Product existing = readyMatches.get(0);
                log.info("Dedup: reusing READY product={} for revision={} project={} profile={}",
                        existing.productId(), timelineRevisionId, projectId, outputProfile);

                TimelineRevisionRenderService.RevisionRenderResult reusedResult =
                        buildReusedResult(existing, timelineRevisionId, outputProfile);

                return RenderDeduplicationDecision.reuse(fingerprint, reusedResult,
                        "READY product exists: " + existing.productId());
            }

            // Check for failed products — allow retry
            List<Product> failedMatches = candidates.stream()
                    .filter(p -> projectId.equals(p.projectId()))
                    .filter(p -> p.productType() == ProductType.FINAL_RENDER)
                    .filter(p -> p.status() == ProductStatus.FAILED)
                    .toList();

            if (!failedMatches.isEmpty()) {
                log.info("Dedup: previous failed render found for revision={} project={}, allowing retry",
                        timelineRevisionId, projectId);
                return RenderDeduplicationDecision.retry(fingerprint,
                        "Previous failed render found, allowing retry");
            }

            // No existing render — proceed
            log.info("Dedup: no existing render for revision={} project={} profile={}, proceeding",
                    timelineRevisionId, projectId, outputProfile);
            return RenderDeduplicationDecision.proceed(fingerprint,
                    RenderDeduplicationReason.NO_EXISTING_RENDER,
                    "No existing render found");

        } catch (Exception e) {
            log.warn("Dedup lookup failed for revision={} project={}: {}",
                    timelineRevisionId, projectId, e.getMessage());
            return RenderDeduplicationDecision.failedClosed(fingerprint,
                    "Lookup error: " + e.getMessage());
        }
    }

    /**
     * Check if a product matches the expected output profile.
     * v0: checks metadataJson for outputProfile field.
     */
    private boolean matchesOutputProfile(Product product, String expectedProfile) {
        String normalized = RenderRequestFingerprint.normalizeProfile(expectedProfile);
        String metadata = product.metadataJson();
        if (metadata == null || metadata.isBlank()) {
            // No metadata — assume default profile
            return "default_1080p".equals(normalized);
        }
        // Extract outputProfile from metadataJson
        String profileFromMeta = extractJsonString(metadata, "outputProfile");
        if (profileFromMeta == null) {
            return "default_1080p".equals(normalized);
        }
        return RenderRequestFingerprint.normalizeProfile(profileFromMeta).equals(normalized);
    }

    /**
     * Simple JSON string value extraction (no external dependency).
     */
    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    /**
     * Build a RevisionRenderResult from an existing READY Product for reuse.
     */
    private TimelineRevisionRenderService.RevisionRenderResult buildReusedResult(
            Product existing, String timelineRevisionId, String outputProfile) {
        String profileFromMeta = extractJsonString(
                existing.metadataJson() != null ? existing.metadataJson() : "", "outputProfile");
        String outputFormat = extractJsonString(
                existing.metadataJson() != null ? existing.metadataJson() : "", "outputFormat");

        return new TimelineRevisionRenderService.RevisionRenderResult(
                "dedup-reuse-" + existing.productId(),  // synthetic job ID
                timelineRevisionId,
                null,  // snapshotId not available from product
                existing.productId(),
                existing.status().name(),
                existing.storageReferenceId(),
                existing.mimeType(),
                outputFormat != null ? outputFormat : "mp4",
                0, 0, 0, 0.0, false,  // dimensions/duration not in product record
                "ffmpeg-libass",
                "dedup-reuse",
                List.of(),  // inputProductIds not reconstructable from product alone
                0);
    }
}
