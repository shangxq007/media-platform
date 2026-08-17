package com.example.platform.render.infrastructure;

import com.example.platform.artifact.domain.ArtifactCommitRequest;
import com.example.platform.artifact.domain.ArtifactCommitService;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.PutObjectCommand;
import com.example.platform.storage.domain.StorageObjectRef;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * GCR-2 (ARTIFACT_AUTHORITY_CONTRACT_V1): render is an Artifact PRODUCER.
 *
 * <p>Uploads rendered media bytes to blob storage (data-plane), computes the
 * content digest, and commits a canonical Artifact through the artifact-module
 * {@link ArtifactCommitService} (single write authority). Render never writes
 * the canonical artifact table directly and never decides Artifact identity
 * from a storage URI.</p>
 */
@Service
public class RenderArtifactStorageService {

    private static final Logger log = LoggerFactory.getLogger(RenderArtifactStorageService.class);

    private final BlobStorage blobStorage;
    private final ArtifactCommitService artifactCommitService;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    public RenderArtifactStorageService(BlobStorage blobStorage, ArtifactCommitService artifactCommitService) {
        this.blobStorage = blobStorage;
        this.artifactCommitService = artifactCommitService;
    }

    public StorageObjectRef uploadJobOutput(String jobId, String projectId, String artifactId,
            String localRelativePath, String contentType) throws IOException {
        Path localFile = Path.of(storageRoot, localRelativePath);
        if (!Files.isRegularFile(localFile)) {
            throw new IOException("Rendered file not found: " + localFile);
        }
        byte[] bytes = Files.readAllBytes(localFile);
        String objectKey = artifactId + "/" + localFile.getFileName();
        PutObjectCommand putCmd = new PutObjectCommand("artifacts", objectKey, bytes, contentType);
        StorageObjectRef storageRef = blobStorage.put(putCmd);

        ContentDigest digest = ContentDigest.sha256(sha256Hex(bytes));
        artifactCommitService.commit(new ArtifactCommitRequest(
                new ArtifactId(artifactId),
                "system",
                digest,
                bytes.length,
                ArtifactMediaType.VIDEO,
                ArtifactKind.RENDER_MASTER,
                1,
                new StorageObjectId(storageRef.bucket() + "/" + storageRef.objectKey()),
                new StorageReplicaId("replica-1"),
                new StorageProviderId(storageRef.provider()),
                ReplicaRole.PRIMARY,
                "default",
                "render:" + jobId,
                java.util.List.of(),
                Instant.now(),
                Instant.now(),
                jobId,
                projectId));

        log.info("Uploaded render artifact job={} artifact={} bytes={}", jobId, artifactId, bytes.length);
        return storageRef;
    }

    public Path jobArtifactPath(String jobId, String fileName) {
        return Path.of(storageRoot, "artifacts", jobId, fileName);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
