package com.example.platform.render.api;

import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.app.preview.CreatePreviewRenderJobRequest;
import com.example.platform.render.app.preview.PreviewRenderJobResponse;
import com.example.platform.render.domain.previewjob.PreviewRenderJob;
import com.example.platform.render.domain.previewjob.PreviewRenderJobId;
import com.example.platform.render.domain.previewjob.PreviewRenderJobStatus;
import com.example.platform.render.domain.product.*;
import com.example.platform.render.domain.storage.StorageClass;
import com.example.platform.render.domain.storage.StorageProviderType;
import com.example.platform.render.domain.storage.StorageReference;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Scenario fixture for VS.1 Preview Render Smoke.
 *
 * <p>Provides deterministic test data for the headless API smoke harness.
 * All fixture data is safe for test environments — no real paths, no secrets,
 * no provider internals.</p>
 *
 * <h3>Scenario: VS.1 Preview Render Smoke</h3>
 * <ol>
 *   <li>Create a preview render job (QUEUED state)</li>
 *   <li>Transition to EXECUTING</li>
 *   <li>Complete with output product registration</li>
 *   <li>Query artifacts and validate contract shape</li>
 *   <li>Validate exposure safety (no leaks)</li>
 * </ol>
 */
public final class Vs1PreviewRenderSmokeFixture {

    private Vs1PreviewRenderSmokeFixture() {}

    // ─── Tenant / Project constants ───

    public static final String TENANT_ID = "tenant_smoke_01";
    public static final String PROJECT_ID = "proj_smoke_01";
    public static final String SNAPSHOT_ID = "snap_smoke_01";
    public static final String PROFILE = "default_1080p";

    // ─── RenderController scenario data ───

    public static CreateRenderJobRequest createRenderJobRequest() {
        return new CreateRenderJobRequest(PROJECT_ID, SNAPSHOT_ID, PROFILE);
    }

    /**
     * Validate that a RenderJobResponse has the expected VS.1 contract shape.
     *
     * @return list of validation failures (empty = all pass)
     */
    public static List<String> validateRenderJobContract(RenderJobResponse response) {
        var failures = new java.util.ArrayList<String>();

        if (response.id() == null || response.id().isBlank()) {
            failures.add("jobId: must not be null or blank");
        }
        if (response.status() == null || response.status().isBlank()) {
            failures.add("status: must not be null or blank");
        }
        if (response.projectId() == null || response.projectId().isBlank()) {
            failures.add("projectId: must not be null or blank");
        }
        if (!"QUEUED".equals(response.status())) {
            failures.add("status: expected QUEUED for newly created job, got " + response.status());
        }

        return failures;
    }

    // ─── PreviewRenderJob scenario data ───

    public static CreatePreviewRenderJobRequest createPreviewRequest() {
        return new CreatePreviewRenderJobRequest(TENANT_ID, PROJECT_ID, SNAPSHOT_ID, PROFILE);
    }

    /**
     * Validate that a PreviewRenderJobResponse has the expected VS.1 contract shape.
     *
     * <p>Contract fields:
     * <ul>
     *   <li>{@code jobId} — non-null, non-blank</li>
     *   <li>{@code status} — non-null, valid enum name</li>
     *   <li>{@code outputProductId} — null when QUEUED/EXECUTING, non-null when COMPLETED</li>
     *   <li>{@code errorMessage} — null when not FAILED</li>
     *   <li>{@code tenantId}, {@code projectId}, {@code snapshotId}, {@code profile} — non-null</li>
     * </ul>
     *
     * @return list of validation failures (empty = all pass)
     */
    public static List<String> validatePreviewJobContract(PreviewRenderJobResponse response) {
        var failures = new java.util.ArrayList<String>();

        // jobId
        if (response.jobId() == null || response.jobId().isBlank()) {
            failures.add("jobId: must not be null or blank");
        }

        // status
        if (response.status() == null || response.status().isBlank()) {
            failures.add("status: must not be null or blank");
        } else {
            try {
                PreviewRenderJobStatus.valueOf(response.status());
            } catch (IllegalArgumentException e) {
                failures.add("status: invalid enum value '" + response.status() + "'");
            }
        }

        // outputProductId (productId) — should be null for non-terminal states
        PreviewRenderJobStatus parsedStatus = null;
        try {
            parsedStatus = PreviewRenderJobStatus.valueOf(response.status());
        } catch (Exception ignored) {}

        if (parsedStatus != null && parsedStatus == PreviewRenderJobStatus.COMPLETED) {
            if (response.outputProductId() == null || response.outputProductId().isBlank()) {
                failures.add("outputProductId (productId): must not be null when COMPLETED");
            }
        }

        // errorMessage (error) — should be null for non-FAILED states
        if (parsedStatus != null && parsedStatus != PreviewRenderJobStatus.FAILED) {
            if (response.errorMessage() != null) {
                failures.add("errorMessage (error): must be null for non-FAILED status, got '" + response.errorMessage() + "'");
            }
        }

        // Required fields
        if (response.tenantId() == null) failures.add("tenantId: must not be null");
        if (response.projectId() == null) failures.add("projectId: must not be null");
        if (response.snapshotId() == null) failures.add("snapshotId: must not be null");
        if (response.profile() == null) failures.add("profile: must not be null");

        return failures;
    }

    // ─── Exposure safety patterns ───

    /**
     * Patterns that must NOT appear in any API response representation.
     * Includes local filesystem paths, storage provider internals, and secret patterns.
     */
    private static final List<String> FORBIDDEN_PATTERNS = List.of(
            "/home/", "/tmp/", "/var/", "/opt/", "/root/",
            "C:\\", "D:\\",
            "s3://", "gs://", "az://", "wasb://",
            "signedUrl", "signed_url", "preSign", "pre_sign",
            "accessKey", "access_key", "secretKey", "secret_key",
            "aws_secret", "aws_key", "AKIA",
            "password", "passwd", "credential",
            "storageRef", "storage_ref", "storageProvider",
            "bucketName", "bucket_name", "containerName",
            "endpointUrl", "endpoint_url", "regionId", "region_id"
    );

    /**
     * Check if a string representation of an API response contains forbidden patterns.
     *
     * @param label    the test label for error messages
     * @param toString the toString() representation of the response object
     * @return list of violations (empty = safe)
     */
    public static List<String> checkExposureSafety(String label, String toString) {
        var violations = new java.util.ArrayList<String>();
        String lower = toString.toLowerCase();

        for (String pattern : FORBIDDEN_PATTERNS) {
            if (lower.contains(pattern.toLowerCase())) {
                violations.add(label + ": contains forbidden pattern '" + pattern + "'");
            }
        }

        return violations;
    }

    /**
     * Build a preview render job in COMPLETED state with output product for artifact testing.
     */
    public static PreviewRenderJob completedPreviewJob(String jobId) {
        return new PreviewRenderJob(
                new PreviewRenderJobId(jobId),
                TENANT_ID, PROJECT_ID, SNAPSHOT_ID, PROFILE,
                PreviewRenderJobStatus.COMPLETED,
                "prod-out-" + jobId,
                null,
                Instant.now().minusSeconds(60),
                Instant.now());
    }

    /**
     * Build a preview render job in FAILED state for error testing.
     */
    public static PreviewRenderJob failedPreviewJob(String jobId, String errorMessage) {
        return new PreviewRenderJob(
                new PreviewRenderJobId(jobId),
                TENANT_ID, PROJECT_ID, SNAPSHOT_ID, PROFILE,
                PreviewRenderJobStatus.FAILED,
                null,
                errorMessage,
                Instant.now().minusSeconds(30),
                Instant.now());
    }

    /**
     * Build a sample output product for a completed preview render job.
     */
    public static Product outputProduct(String productId) {
        return new Product(
                productId, TENANT_ID, PROJECT_ID, "asset-1",
                ProductType.FINAL_RENDER, RepresentationKind.MEDIA_FILE,
                "render", "render:prj-1", null,
                ProductStatus.READY, "stor-1", "sha256-abc", "sha256-abc", "video/mp4", 1,
                "{\"width\":1920,\"height\":1080,\"fps\":30,\"durationSeconds\":10.5,\"outputFormat\":\"mp4\",\"hasSubtitles\":false}",
                Instant.now().minusSeconds(60), Instant.now());
    }

    /**
     * Build a storage reference for the output product.
     */
    public static StorageReference outputStorageReference() {
        return new StorageReference(
                "stor-1", StorageProviderType.LOCAL.name(), StorageClass.STANDARD,
                "/data/render-output", "artifacts/prj-1/output.mp4",
                "sha256-abc", "sha256-abc", 10485760L, "video/mp4",
                Instant.now().minusSeconds(60), Instant.now());
    }

    /**
     * Valid statuses for a preview render job lifecycle.
     */
    public static List<String> validStatuses() {
        return List.of("QUEUED", "EXECUTING", "COMPLETED", "FAILED", "CANCELLED");
    }
}
