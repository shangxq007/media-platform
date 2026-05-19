package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.QuotaProfile;
import com.example.platform.entitlement.infrastructure.QuotaProfileRepository;
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
public class QuotaProfileService {

    private static final Logger log = LoggerFactory.getLogger(QuotaProfileService.class);

    private final QuotaProfileRepository quotaProfileRepository;
    private final AuditPort auditPort;

    public QuotaProfileService(
            @Autowired(required = false) QuotaProfileRepository quotaProfileRepository,
            AuditPort auditPort) {
        this.quotaProfileRepository = quotaProfileRepository;
        this.auditPort = auditPort;
    }

    public QuotaProfile createProfile(String profileKey, String name, String description,
            long monthlyRenderMinutes, int dailyRenderJobs, int concurrentRenderJobs,
            long storageBytes, long gpuMinutes, long remoteWorkerJobs,
            long promptExecutions, long extensionExecutions,
            int apiCallsPerMinute, int mcpCallsPerMinute, String actor) {
        Instant now = Instant.now();
        QuotaProfile profile = new QuotaProfile(
                Ids.newId("quota_prof"), profileKey, name, description,
                monthlyRenderMinutes, dailyRenderJobs, concurrentRenderJobs,
                storageBytes, gpuMinutes, remoteWorkerJobs,
                promptExecutions, extensionExecutions,
                apiCallsPerMinute, mcpCallsPerMinute, now, now);
        if (quotaProfileRepository != null) {
            quotaProfileRepository.save(profile);
        }
        audit("quota.profile.created", actor, Map.of("profileKey", profileKey));
        log.info("Created quota profile: {}", profileKey);
        return profile;
    }

    public Optional<QuotaProfile> getProfile(String profileKey) {
        if (quotaProfileRepository != null) {
            return quotaProfileRepository.findByKey(profileKey);
        }
        return Optional.empty();
    }

    public List<QuotaProfile> listProfiles() {
        if (quotaProfileRepository != null) {
            return quotaProfileRepository.findAll();
        }
        return List.of();
    }

    public QuotaProfile updateProfile(String profileKey, String name, String description,
            long monthlyRenderMinutes, int dailyRenderJobs, int concurrentRenderJobs,
            long storageBytes, long gpuMinutes, long remoteWorkerJobs,
            long promptExecutions, long extensionExecutions,
            int apiCallsPerMinute, int mcpCallsPerMinute, String actor) {
        QuotaProfile existing = resolve(profileKey);
        QuotaProfile updated = new QuotaProfile(
                existing.id(), profileKey, name, description,
                monthlyRenderMinutes, dailyRenderJobs, concurrentRenderJobs,
                storageBytes, gpuMinutes, remoteWorkerJobs,
                promptExecutions, extensionExecutions,
                apiCallsPerMinute, mcpCallsPerMinute, existing.createdAt(), Instant.now());
        if (quotaProfileRepository != null) {
            quotaProfileRepository.update(updated);
        }
        audit("quota.profile.updated", actor, Map.of("profileKey", profileKey));
        return updated;
    }

    private QuotaProfile resolve(String profileKey) {
        if (quotaProfileRepository != null) {
            return quotaProfileRepository.findByKey(profileKey)
                    .orElseThrow(() -> new IllegalArgumentException("Quota profile not found: " + profileKey));
        }
        throw new IllegalStateException("No quota profile repository available");
    }

    private void audit(String action, String actor, Map<String, Object> payload) {
        if (auditPort != null) {
            auditPort.record("ADMIN", action, "ENTITLEMENT",
                    "QUOTA_PROFILE", payload.getOrDefault("profileKey", "unknown").toString(), payload);
        }
    }
}
