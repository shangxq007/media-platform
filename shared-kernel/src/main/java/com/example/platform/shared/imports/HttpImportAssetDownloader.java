package com.example.platform.shared.imports;

import com.example.platform.shared.io.ChecksummingInputStream;
import com.example.platform.shared.security.SafeDownloadUrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * HTTP-based implementation of {@link ImportAssetDownloader}.
 *
 * <p>Downloads assets via HTTP/HTTPS with SSRF protection, streaming checksum
 * computation, timeout enforcement, and max file size limits.
 */
public class HttpImportAssetDownloader implements ImportAssetDownloader {

    private static final Logger log = LoggerFactory.getLogger(HttpImportAssetDownloader.class);
    private static final long DEFAULT_MAX_BYTES = 2L * 1024 * 1024 * 1024; // 2GB
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private static final int READ_TIMEOUT_SECONDS = 300; // 5 minutes

    private final HttpClient httpClient;
    private final Path tempDir;
    private final long maxBytes;

    public HttpImportAssetDownloader(Path tempDir) {
        this(tempDir, DEFAULT_MAX_BYTES);
    }

    public HttpImportAssetDownloader(Path tempDir, long maxBytes) {
        this.tempDir = tempDir;
        this.maxBytes = maxBytes;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public DownloadedAsset download(String downloadUrl) {
        // 1. SSRF validation before any network request
        String validationError = SafeDownloadUrlValidator.validate(downloadUrl);
        if (validationError != null) {
            throw new AssetDownloadException("UNSAFE_URL",
                    "Download URL rejected: " + validationError);
        }

        Path tempFile = null;
        try {
            // 2. Create temp file
            Files.createDirectories(tempDir);
            tempFile = Files.createTempFile(tempDir, "import-", ".tmp");

            // 3. Send HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            // 4. Check HTTP status
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new AssetDownloadException("HTTP_ERROR",
                        "Download failed with HTTP status: " + statusCode);
            }

            // 5. Check Content-Length if available
            long contentLength = response.headers()
                    .firstValueAsLong("Content-Length")
                    .orElse(-1);
            if (contentLength > maxBytes) {
                throw new AssetDownloadException("FILE_TOO_LARGE",
                        "Content-Length " + contentLength + " exceeds max " + maxBytes);
            }

            // 6. Stream download to temp file with checksum computation
            long sizeBytes;
            String checksum;
            try (InputStream bodyStream = response.body();
                 ChecksummingInputStream cis = new ChecksummingInputStream(bodyStream)) {
                Files.copy(cis, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                sizeBytes = cis.sizeBytes();
                checksum = cis.checksum();
            }

            // 7. Post-download size check
            if (sizeBytes > maxBytes) {
                throw new AssetDownloadException("FILE_TOO_LARGE",
                        "Downloaded " + sizeBytes + " bytes, exceeds max " + maxBytes);
            }

            log.info("Downloaded asset: size={} checksum={}", sizeBytes, checksum);
            return new DownloadedAsset(tempFile, sizeBytes, checksum);

        } catch (AssetDownloadException e) {
            cleanupTempFile(tempFile);
            throw e;
        } catch (java.net.ConnectException e) {
            cleanupTempFile(tempFile);
            throw new AssetDownloadException("CONNECTION_FAILED",
                    "Connection failed: " + e.getMessage(), e);
        } catch (java.net.http.HttpTimeoutException e) {
            cleanupTempFile(tempFile);
            throw new AssetDownloadException("TIMEOUT",
                    "Download timed out: " + e.getMessage(), e);
        } catch (IOException e) {
            cleanupTempFile(tempFile);
            throw new AssetDownloadException("IO_ERROR",
                    "Download I/O error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            cleanupTempFile(tempFile);
            Thread.currentThread().interrupt();
            throw new AssetDownloadException("INTERRUPTED",
                    "Download interrupted", e);
        }
    }

    @Override
    public void cleanup(DownloadedAsset asset) {
        if (asset != null && asset.tempFile() != null) {
            cleanupTempFile(asset.tempFile());
        }
    }

    private void cleanupTempFile(Path tempFile) {
        if (tempFile != null) {
            try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
        }
    }
}
