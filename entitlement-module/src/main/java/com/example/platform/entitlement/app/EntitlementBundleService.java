package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.EntitlementBundle;
import com.example.platform.entitlement.infrastructure.EntitlementBundleRepository;
import com.example.platform.shared.Ids;
import com.example.platform.shared.audit.AuditPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EntitlementBundleService {

    private static final Logger log = LoggerFactory.getLogger(EntitlementBundleService.class);

    private final EntitlementBundleRepository bundleRepository;
    private final AuditPort auditPort;

    public EntitlementBundleService(
            @Autowired(required = false) EntitlementBundleRepository bundleRepository,
            AuditPort auditPort) {
        this.bundleRepository = bundleRepository;
        this.auditPort = auditPort;
    }

    public EntitlementBundle createBundle(String bundleKey, String name, String description,
            boolean gpuAllowed, boolean remoteWorkerAllowed, boolean customFontsAllowed,
            int maxSubtitleTracks, int maxConcurrentJobs, long monthlyRenderMinutes,
            long storageLimitBytes, boolean watermarkRequired, boolean priorityQueueAllowed,
            boolean betaEffectsAllowed, long promptExecutionLimit, boolean extensionExecutionAllowed,
            boolean apiAccessAllowed, boolean mcpAccessAllowed, String actor) {
        Instant now = Instant.now();
        EntitlementBundle bundle = new EntitlementBundle(
                Ids.newId("ent_bndl"), bundleKey, name, description, "ACTIVE",
                null, null, gpuAllowed, remoteWorkerAllowed, customFontsAllowed,
                maxSubtitleTracks, maxConcurrentJobs, monthlyRenderMinutes,
                storageLimitBytes, watermarkRequired, priorityQueueAllowed,
                betaEffectsAllowed, promptExecutionLimit, extensionExecutionAllowed,
                apiAccessAllowed, mcpAccessAllowed, now, now);
        if (bundleRepository != null) {
            bundleRepository.save(bundle);
        }
        audit("entitlement.bundle.created", actor, Map.of("bundleKey", bundleKey));
        log.info("Created entitlement bundle: {}", bundleKey);
        return bundle;
    }

    public Optional<EntitlementBundle> getBundle(String bundleKey) {
        if (bundleRepository != null) {
            return bundleRepository.findByKey(bundleKey);
        }
        return Optional.empty();
    }

    public List<EntitlementBundle> listBundles() {
        if (bundleRepository != null) {
            return bundleRepository.findAllActive();
        }
        return List.of();
    }

    public EntitlementBundle updateBundle(String bundleKey, String name, String description,
            boolean gpuAllowed, boolean remoteWorkerAllowed, boolean customFontsAllowed,
            int maxSubtitleTracks, int maxConcurrentJobs, long monthlyRenderMinutes,
            long storageLimitBytes, boolean watermarkRequired, boolean priorityQueueAllowed,
            boolean betaEffectsAllowed, long promptExecutionLimit, boolean extensionExecutionAllowed,
            boolean apiAccessAllowed, boolean mcpAccessAllowed, String actor) {
        EntitlementBundle existing = resolve(bundleKey);
        EntitlementBundle updated = new EntitlementBundle(
                existing.id(), bundleKey, name, description, existing.status(),
                existing.allowedProviders(), existing.allowedPresets(),
                gpuAllowed, remoteWorkerAllowed, customFontsAllowed,
                maxSubtitleTracks, maxConcurrentJobs, monthlyRenderMinutes,
                storageLimitBytes, watermarkRequired, priorityQueueAllowed,
                betaEffectsAllowed, promptExecutionLimit, extensionExecutionAllowed,
                apiAccessAllowed, mcpAccessAllowed, existing.createdAt(), Instant.now());
        if (bundleRepository != null) {
            bundleRepository.update(updated);
        }
        audit("entitlement.bundle.updated", actor, Map.of("bundleKey", bundleKey));
        return updated;
    }

    public EntitlementBundle archiveBundle(String bundleKey, String actor) {
        EntitlementBundle existing = resolve(bundleKey);
        EntitlementBundle archived = new EntitlementBundle(
                existing.id(), bundleKey, existing.name(), existing.description(),
                "ARCHIVED", existing.allowedProviders(), existing.allowedPresets(),
                existing.gpuAllowed(), existing.remoteWorkerAllowed(), existing.customFontsAllowed(),
                existing.maxSubtitleTracks(), existing.maxConcurrentJobs(),
                existing.monthlyRenderMinutes(), existing.storageLimitBytes(),
                existing.watermarkRequired(), existing.priorityQueueAllowed(),
                existing.betaEffectsAllowed(), existing.promptExecutionLimit(),
                existing.extensionExecutionAllowed(), existing.apiAccessAllowed(),
                existing.mcpAccessAllowed(), existing.createdAt(), Instant.now());
        if (bundleRepository != null) {
            bundleRepository.update(archived);
        }
        audit("entitlement.bundle.archived", actor, Map.of("bundleKey", bundleKey));
        return archived;
    }

    private EntitlementBundle resolve(String bundleKey) {
        if (bundleRepository != null) {
            return bundleRepository.findByKey(bundleKey)
                    .orElseThrow(() -> new IllegalArgumentException("Bundle not found: " + bundleKey));
        }
        throw new IllegalStateException("No bundle repository available");
    }

    private void audit(String action, String actor, Map<String, Object> payload) {
        if (auditPort != null) {
            auditPort.record("ADMIN", action, "ENTITLEMENT",
                    "BUNDLE", payload.getOrDefault("bundleKey", "unknown").toString(), payload);
        }
    }
}
