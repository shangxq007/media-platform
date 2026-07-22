package com.example.platform.render.app.output;

import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.RenderOutputStorageProperties;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.domain.product.*;
import com.example.platform.render.domain.storage.*;
import com.example.platform.shared.Ids;
import com.example.platform.storage.infrastructure.S3ObjectWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Coordinates render output files through StorageRuntime and ProductRuntime.
 *
 * <p>Converts a completed render output file into a StorageReference
 * and registers it as a READY Product. Does NOT own Product lifecycle,
 * does NOT own physical storage — delegates to existing services.
 *
 * <p>This service is the integration glue between render output and
 * the Storage/Product runtimes. It does NOT modify either runtime's
 * semantic contract.
 *
 * <p>Architecture boundaries:
 * <ul>
 *   <li>Uses StorageRuntimeService for storage registration and checksum</li>
 *   <li>Uses ProductRuntimeService for Product registration and lifecycle</li>
 *   <li>Never accesses repositories directly</li>
 *   <li>Never resolves storage paths outside the registered root</li>
 *   <li>Never persists signed URLs</li>
 * </ul>
 */
@Service
public class RenderOutputRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RenderOutputRegistrationService.class);

    private final StorageRuntimeService storageRuntime;
    private final ProductRuntimeService productRuntime;
    private final Path storageRoot;
    private final S3ObjectWriter s3Writer;
    private final RenderOutputStorageProperties outputStorageProperties;

    @Autowired
    public RenderOutputRegistrationService(StorageRuntimeService storageRuntime,
                                            ProductRuntimeService productRuntime,
                                            Path storageRoot,
                                            ObjectProvider<S3ObjectWriter> s3WriterProvider,
                                            ObjectProvider<RenderOutputStorageProperties> outputStoragePropertiesProvider) {
        this.storageRuntime = storageRuntime;
        this.productRuntime = productRuntime;
        this.storageRoot = storageRoot;
        this.s3Writer = s3WriterProvider.getIfAvailable();
        this.outputStorageProperties = outputStoragePropertiesProvider.getIfAvailable();
    }


    // Legacy constructor for backward compatibility
    // Legacy constructor removed - use ObjectProvider version instead
    private RenderOutputRegistrationService(StorageRuntimeService storageRuntime,
                                            ProductRuntimeService productRuntime,
                                            Path storageRoot,
                                            S3ObjectWriter s3Writer,
                                            RenderOutputStorageProperties outputStorageProperties) {
        this.storageRuntime = storageRuntime;
        this.productRuntime = productRuntime;
        this.storageRoot = storageRoot;
        this.s3Writer = s3Writer;
        this.outputStorageProperties = outputStorageProperties;
    }

    /**
     * Register a render output file as a StorageReference and READY Product.
     *
     * <p>Routes to LOCAL or S3-compatible registration based on configuration.</p>
     *
     * @param jobId           the render job identifier
     * @param tenantId        the tenant identifier
     * @param projectId       the project identifier
     * @param producerId      the producer that generated the output
     * @param relativePath    the relative path under storage root (e.g., "artifacts/job-1/output.mp4")
     * @return the registered and READY Product
     * @throws RenderOutputRegistrationException if any step fails
     */
    public Product registerOutput(String jobId, String tenantId, String projectId,
                                   String producerId, String relativePath) {
        return registerOutput(jobId, tenantId, projectId, producerId, relativePath, null);
    }

    /**
     * Check if S3-compatible output storage is configured and available.
     */
    public boolean isS3OutputEnabled() {
        return outputStorageProperties != null
                && outputStorageProperties.isS3Compatible()
                && s3Writer != null
                && s3Writer.isEnabled();
    }

    /**
     * Register a render output file as a StorageReference and READY Product with
     * full render provenance metadata.
     *
     * <p>All operations (StorageReference registration, Product registration,
     * markReady, dependency linking) execute within a single transaction.
     * If any step fails, all changes are rolled back.</p>
     *
     * <p>Routes to LOCAL or S3-compatible registration based on configuration.
     * When S3-compatible output is enabled, the local file is uploaded to the
     * configured internal S3 bucket before registration.</p>
     *
     * @param jobId           the render job identifier
     * @param tenantId        the tenant identifier
     * @param projectId       the project identifier
     * @param producerId      the producer that generated the output
     * @param relativePath    the relative path under storage root
     * @param provenance      optional render provenance metadata to enrich the Product
     * @return the registered and READY Product
     * @throws RenderOutputRegistrationException if any step fails
     */
    @Transactional
    public Product registerOutput(String jobId, String tenantId, String projectId,
                                   String producerId, String relativePath,
                                   RenderProductProvenance provenance) {
        if (isS3OutputEnabled()) {
            return registerOutputS3(jobId, tenantId, projectId, producerId, relativePath, provenance);
        }
        return registerOutputLocal(jobId, tenantId, projectId, producerId, relativePath, provenance);
    }

    /**
     * Register a render output file using LOCAL storage.
     */
    private Product registerOutputLocal(String jobId, String tenantId, String projectId,
                                         String producerId, String relativePath,
                                         RenderProductProvenance provenance) {
        Path outputFile = validatePath(relativePath);
        String checksum = computeSha256(outputFile);
        long fileSize = outputFile.toFile().length();
        String mimeType = detectMimeType(outputFile);

        StorageReference storageRef = new StorageReference(
                null,
                StorageProviderType.LOCAL.name(),
                StorageClass.STANDARD,
                storageRoot.toString(),
                relativePath,
                checksum,
                checksum,
                fileSize,
                mimeType,
                Instant.now(),
                Instant.now());

        StorageReference registeredRef;
        try {
            registeredRef = storageRuntime.register(storageRef);
        } catch (Exception e) {
            throw new RenderOutputRegistrationException(
                    jobId, "Storage registration failed: " + e.getMessage(), false);
        }
        log.info("Render output registered in local storage: job={} storageId={} path={} size={}",
                jobId, registeredRef.storageReferenceId(), outputFile, fileSize);

        if (!storageRuntime.verifyChecksum(registeredRef.storageReferenceId())) {
            throw new RenderOutputRegistrationException(
                    jobId, "Checksum verification failed for storage: " + registeredRef.storageReferenceId(), false);
        }

        return registerProductAndLink(jobId, tenantId, projectId, producerId,
                outputFile, fileSize, mimeType, checksum, registeredRef.storageReferenceId(), provenance);
    }

    /**
     * Register a render output file using S3-compatible internal storage.
     *
     * <p>Uploads the local render output to the configured S3-compatible bucket,
     * then registers a StorageReference with S3_COMPATIBLE provider type.</p>
     */
    private Product registerOutputS3(String jobId, String tenantId, String projectId,
                                       String producerId, String relativePath,
                                       RenderProductProvenance provenance) {
        Path outputFile = validatePath(relativePath);
        String checksum = computeSha256(outputFile);
        long fileSize = outputFile.toFile().length();
        String mimeType = detectMimeType(outputFile);

        // Build S3 object key: {prefix}/{projectId}/render-jobs/{renderJobId}/outputs/{filename}
        String bucket = resolveS3Bucket();
        String objectKey = buildS3ObjectKey(projectId, jobId, outputFile.getFileName().toString());

        // Upload to S3
        S3ObjectWriter.UploadResult uploadResult;
        try {
            uploadResult = s3Writer.upload(outputFile, bucket, objectKey, mimeType);
            log.info("Render output uploaded to S3: job={} bucket={} key={} size={} checksum={}",
                    jobId, uploadResult.bucket(), uploadResult.objectKey(),
                    uploadResult.sizeBytes(), uploadResult.checksum());
        } catch (Exception e) {
            log.error("S3 upload failed for job={}: {}", jobId, e.getMessage());
            throw new RenderOutputRegistrationException(
                    jobId, "S3 upload failed: " + e.getMessage(), false);
        }

        // Verify checksum matches local computation
        if (!checksum.equalsIgnoreCase(uploadResult.checksum())) {
            log.error("S3 checksum mismatch for job={}: local={} uploaded={}",
                    jobId, checksum, uploadResult.checksum());
            s3Writer.delete(bucket, objectKey);
            throw new RenderOutputRegistrationException(
                    jobId, "S3 checksum mismatch after upload", false);
        }

        // Register StorageReference with S3_COMPATIBLE provider type
        StorageReference storageRef = new StorageReference(
                null,
                StorageProviderType.S3_COMPATIBLE.name(),
                StorageClass.STANDARD,
                bucket,
                objectKey,
                checksum,
                checksum,
                fileSize,
                mimeType,
                Instant.now(),
                Instant.now());

        StorageReference registeredRef;
        try {
            registeredRef = storageRuntime.register(storageRef);
        } catch (Exception e) {
            log.error("S3 storage registration failed for job={}: {}", jobId, e.getMessage());
            s3Writer.delete(bucket, objectKey);
            throw new RenderOutputRegistrationException(
                    jobId, "Storage registration failed: " + e.getMessage(), false);
        }
        log.info("Render output registered in S3 storage: job={} storageId={} bucket={} key={}",
                jobId, registeredRef.storageReferenceId(), bucket, objectKey);

        return registerProductAndLink(jobId, tenantId, projectId, producerId,
                outputFile, fileSize, mimeType, checksum, registeredRef.storageReferenceId(), provenance);
    }

    /**
     * Register Product and link dependencies. Shared between LOCAL and S3 paths.
     */
    private Product registerProductAndLink(String jobId, String tenantId, String projectId,
                                             String producerId, Path outputFile,
                                             long fileSize, String mimeType, String checksum,
                                             String storageReferenceId,
                                             RenderProductProvenance provenance) {
        String metadataJson = buildMetadataJson(jobId, producerId, outputFile, fileSize, mimeType, checksum, provenance);
        String productId = Ids.newId("prod");

        // Resolve sourceTimelineRevisionId from provenance if available
        String sourceTimelineRevisionId = (provenance != null) ? provenance.timelineRevisionId() : null;

        Product product = new Product(
                productId, tenantId, projectId, null,
                ProductType.FINAL_RENDER, RepresentationKind.MEDIA_FILE,
                producerId.contains(":") ? producerId.split(":")[0] : producerId,
                producerId, sourceTimelineRevisionId,
                ProductStatus.REGISTERED, storageReferenceId,
                checksum, checksum, mimeType, 1,
                metadataJson, Instant.now(), Instant.now());

        Product registeredProduct;
        try {
            registeredProduct = productRuntime.register(product);
        } catch (Exception e) {
            log.error("Product registration failed for job={}: {}", jobId, e.getMessage());
            throw new RenderOutputRegistrationException(
                    jobId, "Product registration failed: " + e.getMessage(), false);
        }
        log.info("Product registered for render output: job={} productId={} type={}",
                jobId, registeredProduct.productId(), registeredProduct.productType());

        Product readyProduct = productRuntime.markReady(registeredProduct.productId());
        log.info("Render output fully registered: job={} productId={} storageId={}",
                jobId, readyProduct.productId(), storageReferenceId);

        // Link formal Product dependency edges from input Products to output Product
        if (provenance != null && provenance.inputProductIds() != null && !provenance.inputProductIds().isEmpty()) {
            linkInputDependencies(jobId, readyProduct.productId(), tenantId, projectId, provenance.inputProductIds());
        }

        return readyProduct;
    }

    /**
     * Resolve the S3 bucket for output storage.
     */
    private String resolveS3Bucket() {
        if (outputStorageProperties != null && outputStorageProperties.getS3Bucket() != null
                && !outputStorageProperties.getS3Bucket().isBlank()) {
            return outputStorageProperties.getS3Bucket();
        }
        return s3Writer.getDefaultBucket();
    }

    /**
     * Build the S3 object key for render output.
     * Format: {prefix}/{projectId}/render-jobs/{renderJobId}/outputs/{filename}
     */
    private String buildS3ObjectKey(String projectId, String renderJobId, String filename) {
        String prefix = (outputStorageProperties != null && outputStorageProperties.getS3KeyPrefix() != null)
                ? outputStorageProperties.getS3KeyPrefix()
                : "projects";
        return prefix + "/" + projectId + "/render-jobs/" + renderJobId + "/outputs/" + filename;
    }

    /**
     * Link formal Product dependency edges from input Products to the output Product.
     *
     * <p>Uses DERIVED_FROM dependency type to express that the output Product
     * is derived from its input Products. De-duplicates input IDs and rejects
     * self-dependencies. Fails closed — throws if any dependency link fails.</p>
     *
     * @param jobId          the render job identifier (for logging)
     * @param outputProductId the output Product identifier
     * @param tenantId       the tenant identifier
     * @param projectId      the project identifier
     * @param inputProductIds the input Product identifiers to link
     * @throws RenderOutputRegistrationException if dependency linking fails
     */
    private void linkInputDependencies(String jobId, String outputProductId,
                                        String tenantId, String projectId,
                                        List<String> inputProductIds) {
        // De-duplicate input IDs
        Set<String> uniqueInputIds = new LinkedHashSet<>(inputProductIds);

        for (String inputProductId : uniqueInputIds) {
            // Reject self-dependency
            if (outputProductId.equals(inputProductId)) {
                log.warn("Self-dependency rejected for job={} outputProduct={}", jobId, outputProductId);
                throw new RenderOutputRegistrationException(jobId,
                        "Self-dependency rejected: output Product " + outputProductId + " cannot depend on itself", true);
            }

            // Validate input Product exists
            if (productRuntime.find(inputProductId).isEmpty()) {
                log.warn("Input Product not found for dependency linking: job={} inputProduct={}", jobId, inputProductId);
                throw new RenderOutputRegistrationException(jobId,
                        "Input Product not found for dependency linking: " + inputProductId, true);
            }

            try {
                productRuntime.linkDependency(outputProductId, inputProductId,
                        DependencyType.DERIVED_FROM, tenantId, projectId);
                log.info("Dependency linked: job={} output={} → input={} type=DERIVED_FROM",
                        jobId, outputProductId, inputProductId);
            } catch (IllegalArgumentException e) {
                // Cycle detection or other validation failure
                log.error("Dependency linking failed for job={}: {}", jobId, e.getMessage());
                throw new RenderOutputRegistrationException(jobId,
                        "Dependency linking failed: " + e.getMessage(), true);
            }
        }
    }

    /**
     * Register a failed render output — marks the Product as FAILED.
     * Does NOT create a StorageReference for failed outputs.
     *
     * <p>Both Product registration and markFailed execute within a single transaction.</p>
     */
    @Transactional
    public Product registerFailedOutput(String jobId, String tenantId, String projectId,
                                         String producerId, String errorMessage) {
        String productId = Ids.newId("prod");
        String metadataJson = "{\"jobId\":\"" + jobId + "\",\"error\":\""
                + escapeJson(errorMessage) + "\",\"status\":\"failed\"}";
        Product product = new Product(
                productId, tenantId, projectId, null,
                ProductType.FINAL_RENDER, RepresentationKind.MEDIA_FILE,
                producerId.contains(":") ? producerId.split(":")[0] : producerId,
                producerId, null,
                ProductStatus.REGISTERED, null, null, null, null, 1,
                metadataJson, Instant.now(), Instant.now());

        return productRuntime.markFailed(productRuntime.register(product).productId());
    }

    private Path validatePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new RenderOutputRegistrationException(null, "relativePath must not be null or blank", false);
        }
        Path resolved = storageRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(storageRoot.normalize())) {
            throw new RenderOutputRegistrationException(null,
                    "Path traversal detected: " + relativePath, false);
        }
        if (!Files.exists(resolved)) {
            throw new RenderOutputRegistrationException(null,
                    "Output file not found: " + resolved, false);
        }
        if (!Files.isRegularFile(resolved)) {
            throw new RenderOutputRegistrationException(null,
                    "Output path is not a regular file: " + resolved, false);
        }
        if (resolved.toFile().length() == 0) {
            throw new RenderOutputRegistrationException(null,
                    "Output file is zero bytes: " + resolved, false);
        }
        return resolved;
    }

    private String computeSha256(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RenderOutputRegistrationException(null,
                    "Failed to compute checksum: " + e.getMessage(), false);
        }
    }

    private String detectMimeType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".mp4")) return "video/mp4";
        if (name.endsWith(".webm")) return "video/webm";
        if (name.endsWith(".mov")) return "video/quicktime";
        if (name.endsWith(".mkv")) return "video/x-matroska";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".srt")) return "text/plain";
        if (name.endsWith(".vtt")) return "text/vtt";
        if (name.endsWith(".ass") || name.endsWith(".ssa")) return "text/plain";
        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".wav")) return "audio/wav";
        return "application/octet-stream";
    }

    private String buildMetadataJson(String jobId, String producerId, Path file,
                                      long fileSize, String mimeType, String checksum,
                                      RenderProductProvenance provenance) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"jobId\":\"").append(escapeJson(jobId)).append("\",");
        sb.append("\"producerId\":\"").append(escapeJson(producerId)).append("\",");
        sb.append("\"outputFile\":\"").append(escapeJson(file.getFileName().toString())).append("\",");
        sb.append("\"fileSize\":").append(fileSize).append(",");
        sb.append("\"mimeType\":\"").append(mimeType).append("\",");
        sb.append("\"checksum\":\"").append(checksum).append("\"");

        if (provenance != null) {
            appendProvenanceFields(sb, provenance);
        }

        sb.append("}");
        return sb.toString();
    }

    private void appendProvenanceFields(StringBuilder sb, RenderProductProvenance p) {
        if (p.tenantId() != null) {
            sb.append(",\"tenantId\":\"").append(escapeJson(p.tenantId())).append("\"");
        }
        if (p.projectId() != null) {
            sb.append(",\"projectId\":\"").append(escapeJson(p.projectId())).append("\"");
        }
        if (p.timelineId() != null) {
            sb.append(",\"timelineId\":\"").append(escapeJson(p.timelineId())).append("\"");
        }
        if (p.timelineRevisionId() != null) {
            sb.append(",\"timelineRevisionId\":\"").append(escapeJson(p.timelineRevisionId())).append("\"");
        }
        if (p.snapshotId() != null) {
            sb.append(",\"snapshotId\":\"").append(escapeJson(p.snapshotId())).append("\"");
        }
        if (p.renderJobId() != null) {
            sb.append(",\"renderJobId\":\"").append(escapeJson(p.renderJobId())).append("\"");
        }
        if (p.executionJobId() != null) {
            sb.append(",\"executionJobId\":\"").append(escapeJson(p.executionJobId())).append("\"");
        }
        if (p.outputProfile() != null) {
            sb.append(",\"outputProfile\":\"").append(escapeJson(p.outputProfile())).append("\"");
        }
        if (p.outputFormat() != null) {
            sb.append(",\"outputFormat\":\"").append(escapeJson(p.outputFormat())).append("\"");
        }
        if (p.durationSeconds() != null) {
            sb.append(",\"durationSeconds\":").append(p.durationSeconds());
        }
        if (p.fps() != null) {
            sb.append(",\"fps\":").append(p.fps());
        }
        if (p.width() != null) {
            sb.append(",\"width\":").append(p.width());
        }
        if (p.height() != null) {
            sb.append(",\"height\":").append(p.height());
        }
        if (p.hasSubtitles() != null) {
            sb.append(",\"hasSubtitles\":").append(p.hasSubtitles());
        }
        if (p.subtitleFormat() != null) {
            sb.append(",\"subtitleFormat\":\"").append(escapeJson(p.subtitleFormat())).append("\"");
        }
        if (p.baselineRenderer() != null) {
            sb.append(",\"baselineRenderer\":\"").append(escapeJson(p.baselineRenderer())).append("\"");
        }
        if (p.renderMode() != null) {
            sb.append(",\"renderMode\":\"").append(escapeJson(p.renderMode())).append("\"");
        }
        if (p.inputProductIds() != null && !p.inputProductIds().isEmpty()) {
            sb.append(",\"inputProductIds\":[");
            for (int i = 0; i < p.inputProductIds().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(p.inputProductIds().get(i))).append("\"");
            }
            sb.append("]");
        }
        if (p.sourceAssetIds() != null && !p.sourceAssetIds().isEmpty()) {
            sb.append(",\"sourceAssetIds\":[");
            for (int i = 0; i < p.sourceAssetIds().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(p.sourceAssetIds().get(i))).append("\"");
            }
            sb.append("]");
        }
        // storageReferenceId is already on the Product record itself, not in metadataJson
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
