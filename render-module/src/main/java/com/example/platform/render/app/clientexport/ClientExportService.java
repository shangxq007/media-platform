package com.example.platform.render.app.clientexport;

import com.example.platform.render.api.port.ClientExportArtifactPort;
import com.example.platform.shared.Ids;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ClientExportService {

    private static final Logger log = LoggerFactory.getLogger(ClientExportService.class);

    private final Path storageRoot;
    private final Optional<ClientExportArtifactPort> artifactPort;
    private final Map<String, ClientExportSession> sessions = new ConcurrentHashMap<>();

    public ClientExportService(
            @Value("${app.storage.local-root:/tmp/platform}") String storageRoot,
            @Autowired(required = false) ClientExportArtifactPort artifactPort) {
        this.storageRoot = Path.of(storageRoot);
        this.artifactPort = Optional.ofNullable(artifactPort);
    }

    public ClientExportSession startSession(String tenantId, String projectId, String snapshotId, String preset) {
        String sessionId = Ids.newId("cex");
        ClientExportSession session = new ClientExportSession(
                sessionId,
                tenantId,
                projectId,
                snapshotId,
                preset,
                "PENDING",
                null,
                null,
                null,
                Instant.now());
        sessions.put(sessionId, session);
        log.info("Client export session started id={} project={}", sessionId, projectId);
        return session;
    }

    public Optional<ClientExportSession> findSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public Path resolveUploadPath(String sessionId) throws IOException {
        ClientExportSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Unknown client export session: " + sessionId);
        }
        Path dir = storageRoot.resolve("artifacts").resolve("client-exports").resolve(sessionId);
        Files.createDirectories(dir);
        return dir.resolve("output.mp4");
    }

    public ClientExportSession uploadAndComplete(
            String sessionId,
            MultipartFile file,
            Long durationSeconds,
            String checksum,
            boolean registerArtifact) throws IOException {

        ClientExportSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Unknown client export session: " + sessionId);
        }

        Path output = resolveUploadPath(sessionId);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, output, StandardCopyOption.REPLACE_EXISTING);
        }

        String storageUri = "localFsStorageProvider://artifacts/client-exports/" + sessionId + "/output.mp4";
        String artifactId = null;
        String downloadPath = "/api/v1/render/client-exports/" + sessionId + "/download";

        if (registerArtifact && artifactPort.isPresent()) {
            var registered = artifactPort.get().register(
                    sessionId,
                    session.projectId(),
                    storageUri,
                    "mp4",
                    "1280x720",
                    durationSeconds != null ? durationSeconds : 0L);
            artifactId = registered.artifactId();
            if (registered.downloadPath() != null) {
                downloadPath = registered.downloadPath();
            }
        }

        ClientExportSession completed = new ClientExportSession(
                sessionId,
                session.tenantId(),
                session.projectId(),
                session.snapshotId(),
                session.preset(),
                "COMPLETED",
                storageUri,
                artifactId,
                downloadPath,
                session.createdAt());
        sessions.put(sessionId, completed);
        return completed;
    }

    public record ClientExportSession(
            String sessionId,
            String tenantId,
            String projectId,
            String snapshotId,
            String preset,
            String status,
            String storageUri,
            String artifactId,
            String downloadPath,
            Instant createdAt) {}
}
