package com.example.platform.render.infrastructure.media;

import com.example.platform.storage.domain.BlobStorage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MediaAssetResolver {

    private static final Logger log = LoggerFactory.getLogger(MediaAssetResolver.class);

    private final Path storageRoot;
    private final Optional<BlobStorage> blobStorage;
    private final HttpClient httpClient;

    public MediaAssetResolver(
            @Value("${app.storage.local-root:/tmp/platform}") String storageRoot,
            Optional<BlobStorage> blobStorage) {
        this.storageRoot = Path.of(storageRoot);
        this.blobStorage = blobStorage;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String resolveToLocalPath(String storageUri) {
        if (storageUri == null || storageUri.isBlank()) {
            return null;
        }

        if (storageUri.startsWith("file://") || storageUri.startsWith("/")) {
            String path = storageUri.startsWith("file://")
                    ? storageUri.substring("file://".length()) : storageUri;
            if (Files.isRegularFile(Path.of(path))) {
                return path;
            }
            return null;
        }

        if (storageUri.startsWith("localFsStorageProvider://")) {
            String relative = storageUri.substring("localFsStorageProvider://".length());
            Path localPath = storageRoot.resolve(relative);
            if (Files.isRegularFile(localPath)) {
                return localPath.toString();
            }
            return null;
        }

        if (storageUri.startsWith("storage://") || storageUri.startsWith("s3://")) {
            return downloadFromBlobStorage(storageUri);
        }

        if (storageUri.startsWith("http://") || storageUri.startsWith("https://")) {
            return downloadFromUrl(storageUri);
        }

        Path localPath = storageRoot.resolve(storageUri);
        if (Files.isRegularFile(localPath)) {
            return localPath.toString();
        }
        return null;
    }

    private String downloadFromBlobStorage(String storageUri) {
        if (blobStorage.isEmpty()) {
            log.warn("No BlobStorage available to resolve: {}", storageUri);
            return null;
        }
        try {
            Optional<byte[]> data = Optional.empty();

            var ref = BlobStorage.parseUri(storageUri);
            if (ref.isPresent() && blobStorage.isPresent()) {
                data = blobStorage.get().get(ref.get().bucket(), ref.get().objectKey());
            }

            if (data.isEmpty()) {
                Optional<String> presigned = blobStorage.get().presignStorageUri(storageUri);
                if (presigned.isPresent()) {
                    byte[] downloaded = downloadBytes(presigned.get());
                    if (downloaded != null) {
                        data = Optional.of(downloaded);
                    }
                }
            }

            if (data.isPresent()) {
                Path tempDir = storageRoot.resolve("tmp").resolve("asset-cache");
                Files.createDirectories(tempDir);
                String filename = storageUri.replaceAll("[^a-zA-Z0-9._-]", "_");
                Path tempFile = tempDir.resolve(filename);
                Files.write(tempFile, data.get());
                log.info("Downloaded remote asset to: {}", tempFile);
                return tempFile.toString();
            }
        } catch (Exception e) {
            log.error("Failed to download asset {}: {}", storageUri, e.getMessage());
        }
        return null;
    }

    private String downloadFromUrl(String url) {
        try {
            Path tempDir = storageRoot.resolve("tmp").resolve("asset-cache");
            Files.createDirectories(tempDir);
            String filename = URI.create(url).getPath()
                    .replaceAll(".*/", "")
                    .replaceAll("[^a-zA-Z0-9._-]", "_");
            if (filename.isBlank()) filename = "download";
            Path tempFile = tempDir.resolve(filename);

            if (Files.isRegularFile(tempFile)) {
                return tempFile.toString();
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();

            HttpResponse<Path> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofFile(tempFile));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Downloaded URL asset to: {}", tempFile);
                return tempFile.toString();
            }
            Files.deleteIfExists(tempFile);
            log.warn("Failed to download URL {}: HTTP {}", url, response.statusCode());
        } catch (Exception e) {
            log.error("Failed to download URL {}: {}", url, e.getMessage());
        }
        return null;
    }

    private byte[] downloadBytes(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
        } catch (Exception e) {
            log.error("Failed to download bytes from {}: {}", url, e.getMessage());
            return null;
        }
    }
}
