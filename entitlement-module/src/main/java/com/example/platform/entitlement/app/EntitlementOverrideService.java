package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.EntitlementOverride;
import com.example.platform.entitlement.infrastructure.EntitlementOverrideRepository;
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
public class EntitlementOverrideService {

    private static final Logger log = LoggerFactory.getLogger(EntitlementOverrideService.class);

    private final EntitlementOverrideRepository overrideRepository;
    private final AuditPort auditPort;

    public EntitlementOverrideService(
            @Autowired(required = false) EntitlementOverrideRepository overrideRepository,
            AuditPort auditPort) {
        this.overrideRepository = overrideRepository;
        this.auditPort = auditPort;
    }

    public EntitlementOverride createOverride(String subjectType, String subjectId,
            String overrideKind, String overridePayload,
            Instant effectiveAt, Instant expiresAt, String actor) {
        String id = Ids.newId("ent_ovr");
        EntitlementOverride override = new EntitlementOverride(
                id, subjectType, subjectId, overrideKind, overridePayload,
                effectiveAt, expiresAt, "ACTIVE", null, null);
        if (overrideRepository != null) {
            overrideRepository.save(override);
        }
        audit("entitlement.override.created", actor, Map.of(
                "overrideId", id, "subjectType", subjectType,
                "subjectId", subjectId, "overrideKind", overrideKind));
        log.info("Created entitlement override: {} for subject: {} {}", id, subjectType, subjectId);
        return override;
    }

    public EntitlementOverride updateOverride(String id, String overrideKind,
            String overridePayload, Instant effectiveAt, Instant expiresAt, String actor) {
        EntitlementOverride existing = resolve(id);
        EntitlementOverride updated = new EntitlementOverride(
                id, existing.subjectType(), existing.subjectId(),
                overrideKind, overridePayload, effectiveAt, expiresAt,
                existing.status(), existing.createdAt(), null);
        if (overrideRepository != null) {
            overrideRepository.update(updated);
        }
        audit("entitlement.override.updated", actor, Map.of("overrideId", id));
        return updated;
    }

    public EntitlementOverride enableOverride(String id, String actor) {
        EntitlementOverride existing = resolve(id);
        EntitlementOverride enabled = new EntitlementOverride(
                id, existing.subjectType(), existing.subjectId(),
                existing.overrideKind(), existing.overridePayload(),
                existing.effectiveAt(), existing.expiresAt(),
                "ACTIVE", existing.createdAt(), null);
        if (overrideRepository != null) {
            overrideRepository.update(enabled);
        }
        audit("entitlement.override.enabled", actor, Map.of("overrideId", id));
        return enabled;
    }

    public EntitlementOverride disableOverride(String id, String actor) {
        EntitlementOverride existing = resolve(id);
        EntitlementOverride disabled = new EntitlementOverride(
                id, existing.subjectType(), existing.subjectId(),
                existing.overrideKind(), existing.overridePayload(),
                existing.effectiveAt(), existing.expiresAt(),
                "DISABLED", existing.createdAt(), null);
        if (overrideRepository != null) {
            overrideRepository.update(disabled);
        }
        audit("entitlement.override.disabled", actor, Map.of("overrideId", id));
        return disabled;
    }

    public List<EntitlementOverride> queryOverrides(String subjectId) {
        if (overrideRepository != null) {
            return overrideRepository.findBySubjectId(subjectId);
        }
        return List.of();
    }

    public Optional<EntitlementOverride> getOverride(String id) {
        if (overrideRepository != null) {
            return overrideRepository.findById(id);
        }
        return Optional.empty();
    }

    public EntitlementOverride archiveOverride(String id, String actor) {
        EntitlementOverride existing = resolve(id);
        EntitlementOverride archived = new EntitlementOverride(
                id, existing.subjectType(), existing.subjectId(),
                existing.overrideKind(), existing.overridePayload(),
                existing.effectiveAt(), existing.expiresAt(),
                "ARCHIVED", existing.createdAt(), null);
        if (overrideRepository != null) {
            overrideRepository.update(archived);
        }
        audit("entitlement.override.archived", actor, Map.of("overrideId", id));
        return archived;
    }

    public List<EntitlementOverride> getActiveOverrides(String subjectId) {
        if (overrideRepository != null) {
            return overrideRepository.findActiveBySubjectId(subjectId);
        }
        return List.of();
    }

    private EntitlementOverride resolve(String id) {
        if (overrideRepository != null) {
            return overrideRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Override not found: " + id));
        }
        throw new IllegalStateException("No override repository available");
    }

    private void audit(String action, String actor, Map<String, Object> payload) {
        if (auditPort != null) {
            auditPort.record("ADMIN", action, "ENTITLEMENT",
                    "OVERRIDE", payload.getOrDefault("overrideId", "unknown").toString(), payload);
        }
    }
}
