package com.example.platform.render.app.output;

import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.domain.product.*;
import com.example.platform.render.domain.storage.*;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

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
public class RenderOutputRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RenderOutputRegistrationService.class);

    private final StorageRuntimeService storageRuntime;
    private final ProductRuntimeService productRuntime;
    private final Path storageRoot;

    public RenderOutputRegistrationService(StorageRuntimeService storageRuntime,
                                            ProductRuntimeService productRuntime,
                                            Path storageRoot) {
        this.storageRuntime = storageRuntime;
        this.productRuntime = productRuntime;
        this.storageRoot = storageRoot;
    }

    public RenderOutputRegistrationService(StorageRuntimeService storageRuntime,
                                            ProductRuntimeService productRuntime,
                                            String storageRoot) {
        this(storageRuntime, productRuntime, Path.of(storageRoot));
    }

    /**
     * Register a render output file as a StorageReference and READY Product.
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
        log.info("Render output registered in storage: job={} storageId={} path={} size={}",
                jobId, registeredRef.storageReferenceId(), outputFile, fileSize);

        if (!storageRuntime.verifyChecksum(registeredRef.storageReferenceId())) {
            throw new RenderOutputRegistrationException(
                    jobId, "Checksum verification failed for storage: " + registeredRef.storageReferenceId(), false);
        }

        String metadataJson = buildMetadataJson(jobId, producerId, outputFile, fileSize, mimeType, checksum);
        String productId = Ids.newId("prod");
        Product product = new Product(
                productId, tenantId, projectId, null,
                ProductType.FINAL_RENDER, RepresentationKind.MEDIA_FILE,
                producerId.contains(":") ? producerId.split(":")[0] : producerId,
                producerId, null,
                ProductStatus.REGISTERED, registeredRef.storageReferenceId(),
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
                jobId, readyProduct.productId(), registeredRef.storageReferenceId());

        return readyProduct;
    }

    /**
     * Register a failed render output — marks the Product as FAILED.
     * Does NOT create a StorageReference for failed outputs.
     */
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
                                      long fileSize, String mimeType, String checksum) {
        return "{\"jobId\":\"" + jobId + "\","
                + "\"producerId\":\"" + escapeJson(producerId) + "\","
                + "\"outputFile\":\"" + escapeJson(file.getFileName().toString()) + "\","
                + "\"fileSize\":" + fileSize + ","
                + "\"mimeType\":\"" + mimeType + "\","
                + "\"checksum\":\"" + checksum + "\"}";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
