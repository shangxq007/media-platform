package com.example.platform.render.infrastructure;

import com.example.platform.storage.api.StorageCatalogPort;
import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.PutObjectCommand;
import com.example.platform.storage.domain.StorageObjectRef;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Uploads rendered media files from the local artifact workspace into blob storage.
 */
@Service
public class RenderArtifactStorageService {

    private static final Logger log = LoggerFactory.getLogger(RenderArtifactStorageService.class);

    private final BlobStorage blobStorage;
    private final StorageCatalogPort storageCatalogPort;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    public RenderArtifactStorageService(BlobStorage blobStorage, StorageCatalogPort storageCatalogPort) {
        this.blobStorage = blobStorage;
        this.storageCatalogPort = storageCatalogPort;
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
        storageCatalogPort.registerArtifact(jobId, projectId, storageRef);
        log.info("Uploaded render artifact job={} artifact={} bytes={}", jobId, artifactId, bytes.length);
        return storageRef;
    }

    public Path jobArtifactPath(String jobId, String fileName) {
        return Path.of(storageRoot, "artifacts", jobId, fileName);
    }
}
