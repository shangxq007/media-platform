package com.example.platform.render.app.clientexport;

import com.example.platform.entitlement.domain.ExportCapabilityPolicy;
import com.example.platform.render.api.port.ClientExportArtifactPort;
import com.example.platform.render.domain.clientexport.ClientExportSession;
import com.example.platform.render.infrastructure.ExportPolicyService;
import com.example.platform.render.infrastructure.ExportPolicyService.ExportPreset;
import com.example.platform.render.infrastructure.clientexport.ClientExportSessionRepository;
import com.example.platform.shared.Ids;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final ClientExportSessionRepository repository;
    private final ExportPolicyService exportPolicy;
    private final Optional<ClientExportArtifactPort> artifactPort;

    public ClientExportService(
            @Value("${app.storage.local-root:/tmp/platform}") String storageRoot,
            ClientExportSessionRepository repository,
            ExportPolicyService exportPolicy,
            @Autowired(required = false) ClientExportArtifactPort artifactPort) {
        this.storageRoot = Path.of(storageRoot);
        this.repository = repository;
        this.exportPolicy = exportPolicy;
        this.artifactPort = Optional.ofNullable(artifactPort);
    }

    public record ExportConfig(
            String sessionId,
            String preset,
            String resolution,
            int fps,
            String format,
            String videoCodec,
            String audioCodec,
            boolean watermarkEnabled,
            int videoBitrate,
            int audioBitrate,
            int maxDurationSec,
            String renderLocation,
            List<Map<String, Object>> availablePresets) {}

    public ExportConfig createSessionWithConfig(
            String tenantId, String workspaceId, String projectId, String userId,
            String tier, String requestedPreset, String timelineSnapshotId) {

        ExportCapabilityPolicy capability = ExportCapabilityPolicy.forTier(tier);
        ExportPreset preset = exportPolicy.getPreset(requestedPreset);

        if (preset == null) {
            preset = exportPolicy.getDefaultPreset(tier);
        }

        if (!capability.isPresetAllowed(preset.name())) {
            throw new IllegalArgumentException(
                    "Preset '" + preset.name() + "' not available for tier " + tier);
        }

        boolean watermark = capability.watermarkRequired() || preset.watermark();
        String renderLocation = preset.providerKey().equals("client") ? "CLIENT" : "SERVER";

        String sessionId = Ids.newId("cex");
        Instant now = Instant.now();

        ClientExportSession session = new ClientExportSession(
                sessionId, tenantId, workspaceId, projectId, userId,
                timelineSnapshotId, "CLIENT_BROWSER", preset.name(),
                ClientExportSession.STATUS_CREATED, 0,
                preset.resolution(), preset.frameRate(), preset.format(),
                watermark,
                estimateVideoBitrate(preset), estimateAudioBitrate(preset),
                300,
                null, null, null, null, null,
                now, now, now.plus(24, ChronoUnit.HOURS));

        repository.insert(session);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> availablePresets = (List<Map<String, Object>>) (List<?>)
                exportPolicy.getAvailablePresets(tier).stream()
                .map(p -> Map.<String, Object>of(
                        "name", p.name(),
                        "displayName", p.displayName(),
                        "resolution", p.resolution(),
                        "format", p.format(),
                        "watermark", capability.watermarkRequired() || p.watermark(),
                        "renderLocation", p.providerKey().equals("client") ? "CLIENT" : "SERVER"))
                .toList();

        log.info("Client export session created id={} tenant={} tier={} preset={} location={}",
                sessionId, tenantId, tier, preset.name(), renderLocation);

        return new ExportConfig(
                sessionId, preset.name(), preset.resolution(), preset.frameRate(),
                preset.format(), preset.videoCodec(), preset.audioCodec(),
                watermark, estimateVideoBitrate(preset), estimateAudioBitrate(preset),
                300,
                renderLocation, availablePresets);
    }

    public Optional<ClientExportSession> findSession(String sessionId) {
        return repository.findById(sessionId);
    }

    public ClientExportSession findSessionOrThrow(String sessionId) {
        return repository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown client export session: " + sessionId));
    }

    public ClientExportSession findSessionForTenant(String sessionId, String tenantId) {
        ClientExportSession session = findSessionOrThrow(sessionId);
        if (!session.tenantId().equals(tenantId)) {
            throw new SecurityException("Access denied: session belongs to another tenant");
        }
        return session;
    }

    public List<ClientExportSession> listByTenant(String tenantId, int limit, int offset) {
        return repository.findByTenant(tenantId, limit, offset);
    }

    public List<ClientExportSession> listByTenantAndProject(String tenantId, String projectId, int limit, int offset) {
        return repository.findByTenantAndProject(tenantId, projectId, limit, offset);
    }

    public List<ClientExportSession> listActiveByTenant(String tenantId) {
        return repository.findActiveByTenant(tenantId);
    }

    public ClientExportSession updateProgress(String sessionId, String status, int progress) {
        ClientExportSession session = findSessionOrThrow(sessionId);
        if (!session.canTransitionTo(status)) {
            throw new IllegalStateException(
                    "Invalid status transition: " + session.status() + " -> " + status);
        }
        repository.updateProgress(sessionId, status, progress);
        return findSessionOrThrow(sessionId);
    }

    public Path resolveUploadPath(String sessionId) throws IOException {
        ClientExportSession session = findSessionOrThrow(sessionId);
        Path dir = buildTenantPath(session).resolve(sessionId);
        Files.createDirectories(dir);
        return dir.resolve("output." + session.format());
    }

    public ClientExportSession uploadAndComplete(
            String sessionId, MultipartFile file,
            Long durationSeconds, String checksum, boolean registerArtifact) throws IOException {

        ClientExportSession session = findSessionOrThrow(sessionId);

        if (session.isTerminal()) {
            throw new IllegalStateException("Session " + sessionId + " is already in terminal state: " + session.status());
        }

        Path output = resolveUploadPath(sessionId);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, output, StandardCopyOption.REPLACE_EXISTING);
        }

        String storageUri = buildTenantPath(session).resolve(sessionId).resolve("output." + session.format()).toString();
        String artifactId = null;
        String downloadPath = "/api/v1/render/client-exports/" + sessionId + "/download";

        if (registerArtifact && artifactPort.isPresent()) {
            var registered = artifactPort.get().register(
                    sessionId, session.projectId(), storageUri,
                    session.format(), session.resolution(),
                    durationSeconds != null ? durationSeconds : 0L);
            artifactId = registered.artifactId();
            if (registered.downloadPath() != null) {
                downloadPath = registered.downloadPath();
            }
        }

        repository.updateStatus(sessionId, ClientExportSession.STATUS_COMPLETED, 100,
                storageUri, artifactId, downloadPath, null, null);

        log.info("Client export session completed id={} size={}b", sessionId, file.getSize());
        return findSessionOrThrow(sessionId);
    }

    public ClientExportSession failSession(String sessionId, String errorCode, String errorMessage) {
        findSessionOrThrow(sessionId);
        repository.updateStatus(sessionId, ClientExportSession.STATUS_FAILED, 0,
                null, null, null, errorCode, errorMessage);
        log.warn("Client export session failed id={} error={}", sessionId, errorCode);
        return findSessionOrThrow(sessionId);
    }

    public ClientExportSession cancelSession(String sessionId) {
        ClientExportSession session = findSessionOrThrow(sessionId);
        if (session.isTerminal()) {
            throw new IllegalStateException("Cannot cancel session in terminal state: " + session.status());
        }
        repository.updateStatus(sessionId, ClientExportSession.STATUS_CANCELLED, session.progress(),
                null, null, null, null, null);
        log.info("Client export session cancelled id={}", sessionId);
        return findSessionOrThrow(sessionId);
    }

    public int cleanupExpired() {
        int deleted = repository.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired client export sessions", deleted);
        }
        return deleted;
    }

    private Path buildTenantPath(ClientExportSession session) {
        return storageRoot
                .resolve("tenant").resolve(session.tenantId())
                .resolve("workspace").resolve(session.workspaceId() != null ? session.workspaceId() : "default")
                .resolve("project").resolve(session.projectId())
                .resolve("exports");
    }

    private static int estimateVideoBitrate(ExportPreset preset) {
        return switch (preset.height()) {
            case 2160 -> 20_000_000;
            case 1080 -> 8_000_000;
            case 720  -> 4_000_000;
            case 480  -> 2_000_000;
            default   -> 4_000_000;
        };
    }

    private static int estimateAudioBitrate(ExportPreset preset) {
        return preset.audioCodec().equals("opus") ? 128_000 : 192_000;
    }
}
