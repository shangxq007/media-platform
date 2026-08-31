package com.example.platform.render.app.clientexport;

import com.example.platform.render.api.port.ClientExportArtifactPort;
import com.example.platform.render.app.clientexport.ClientExportPresetCatalog.Preset;
import com.example.platform.render.domain.clientexport.ClientExportSession;
import com.example.platform.render.infrastructure.clientexport.ClientExportSessionRepository;
import com.example.platform.shared.Ids;
import com.example.platform.shared.commercial.CommercialAdmissionPort;
import com.example.platform.shared.commercial.CommercialAdmissionRequest;
import com.example.platform.shared.commercial.CommercialDecision;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.shared.authorization.FailClosedAuthorization;
import com.example.platform.storage.contract.ChecksummingInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
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
    private final ClientExportPresetCatalog presetCatalog;
    private final CommercialAdmissionPort commercialAdmission;
    private final Optional<ClientExportArtifactPort> artifactPort;

    public ClientExportService(
            @Value("${app.storage.local-root:/tmp/platform}") String storageRoot,
            ClientExportSessionRepository repository,
            ClientExportPresetCatalog presetCatalog,
            CommercialAdmissionPort commercialAdmission,
            @Autowired(required = false) ClientExportArtifactPort artifactPort) {
        this.storageRoot = Path.of(storageRoot);
        this.repository = repository;
        this.presetCatalog = presetCatalog;
        this.commercialAdmission = commercialAdmission;
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

        Preset preset;
        if (requestedPreset == null || requestedPreset.isBlank()) {
            preset = presetCatalog.findPreset("client_720p_watermarked")
                    .orElseThrow(() -> new IllegalStateException("Default client export preset is not catalogued"));
        } else {
            preset = presetCatalog.findPreset(requestedPreset)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown export preset: " + requestedPreset));
        }
        Instant now = Instant.now();
        YearMonth month = YearMonth.from(now.atZone(ZoneOffset.UTC));
        Instant periodStart = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant periodEnd = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        PrincipalRef principal = new PrincipalRef(tenantId, PrincipalType.USER, userId, workspaceId, null);
        CommercialDecision admission = decidePresetAdmission(
                principal, projectId, preset, periodStart, periodEnd, now);
        if (!admission.allowed()) {
            throw new IllegalArgumentException("Export entitlement denied: " + admission.reason());
        }

        boolean watermark = preset.watermark();
        String renderLocation = "client".equals(preset.providerKey()) ? "CLIENT" : "SERVER";

        String sessionId = Ids.newId("cex");
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

        List<Map<String, Object>> availablePresets = presetCatalog.listPresets().stream()
                .filter(candidate -> decidePresetAdmission(
                        principal, projectId, candidate, periodStart, periodEnd, now).allowed())
                .map(ClientExportService::toAvailablePreset)
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

    private CommercialDecision decidePresetAdmission(
            PrincipalRef principal,
            String projectId,
            Preset preset,
            Instant periodStart,
            Instant periodEnd,
            Instant now) {
        return commercialAdmission.decide(new CommercialAdmissionRequest(
                principal,
                "client-export.create",
                "export.preset." + preset.name(),
                "render.job.create",
                1,
                periodStart,
                periodEnd,
                "client-export:" + principal.tenantId() + ":" + projectId + ":" + preset.name(),
                now));
    }

    private static Map<String, Object> toAvailablePreset(Preset preset) {
        return Map.of(
                "name", preset.name(),
                "displayName", preset.displayName(),
                "resolution", preset.resolution(),
                "format", preset.format(),
                "watermark", preset.watermark(),
                "renderLocation", "client".equals(preset.providerKey()) ? "CLIENT" : "SERVER");
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
        throw FailClosedAuthorization.unavailable("client export progress mutation");
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
        throw FailClosedAuthorization.unavailable("client export upload completion");
    }

    public ClientExportSession failSession(String sessionId, String errorCode, String errorMessage) {
        throw FailClosedAuthorization.unavailable("client export failure mutation");
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

    private static int estimateVideoBitrate(Preset preset) {
        return switch (preset.height()) {
            case 2160 -> 20_000_000;
            case 1080 -> 8_000_000;
            case 720  -> 4_000_000;
            case 480  -> 2_000_000;
            default   -> 4_000_000;
        };
    }

    private static int estimateAudioBitrate(Preset preset) {
        return preset.audioCodec().equals("opus") ? 128_000 : 192_000;
    }
}
