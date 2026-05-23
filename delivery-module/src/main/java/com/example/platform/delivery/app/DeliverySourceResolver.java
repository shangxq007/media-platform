package com.example.platform.delivery.app;

import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.StorageObjectRef;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DeliverySourceResolver {

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    private final BlobStorage blobStorage;

    public DeliverySourceResolver(BlobStorage blobStorage) {
        this.blobStorage = blobStorage;
    }

    public record SourceFile(String uri, String fileName, String contentType, long length, InputStream stream)
            implements AutoCloseable {
        @Override
        public void close() throws java.io.IOException {
            stream.close();
        }
    }

    public Optional<SourceFile> open(String sourceUri) {
        if (sourceUri == null || sourceUri.isBlank()) {
            return Optional.empty();
        }
        Optional<StorageObjectRef> ref = BlobStorage.parseUri(sourceUri);
        if (ref.isPresent()) {
            var r = ref.get();
            Optional<byte[]> bytes = blobStorage.get(r.bucket(), r.objectKey());
            if (bytes.isEmpty()) {
                return Optional.empty();
            }
            byte[] data = bytes.get();
            String name = Path.of(r.objectKey()).getFileName().toString();
            return Optional.of(new SourceFile(
                    sourceUri, name, "video/mp4", data.length, new java.io.ByteArrayInputStream(data)));
        }
        if (sourceUri.startsWith("file:")) {
            try {
                Path p = Path.of(java.net.URI.create(sourceUri));
                if (!Files.isRegularFile(p)) {
                    return Optional.empty();
                }
                byte[] data = Files.readAllBytes(p);
                return Optional.of(new SourceFile(
                        sourceUri, p.getFileName().toString(), "video/mp4", data.length,
                        new java.io.ByteArrayInputStream(data)));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        Path local = Path.of(storageRoot).resolve(sourceUri.replace("localFsStorageProvider://", ""));
        if (Files.isRegularFile(local)) {
            try {
                byte[] data = Files.readAllBytes(local);
                return Optional.of(new SourceFile(
                        sourceUri, local.getFileName().toString(), "video/mp4", data.length,
                        new java.io.ByteArrayInputStream(data)));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
