package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.*;
import com.example.platform.entitlement.infrastructure.EntitlementGrantRepository;
import com.example.platform.entitlement.infrastructure.InMemoryEntitlementCache;
import com.example.platform.shared.Ids;
import com.example.platform.shared.audit.AuditPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EntitlementService {

    private static final Logger log = LoggerFactory.getLogger(EntitlementService.class);

    private final Map<String, Set<String>> featureGrants = new ConcurrentHashMap<>();
    private final Map<String, String> quotaProfiles = new ConcurrentHashMap<>();
    private final Map<String, EntitlementSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<String, EntitlementChangedEvent> changeEvents = new ConcurrentHashMap<>();
    private final InMemoryEntitlementCache cache;
    private final EntitlementGrantRepository entitlementGrantRepository;
    private final AuditPort auditPort;

    public EntitlementService(InMemoryEntitlementCache cache,
                              @Autowired(required = false) EntitlementGrantRepository entitlementGrantRepository,
                              AuditPort auditPort) {
        this.cache = cache;
        this.entitlementGrantRepository = entitlementGrantRepository;
        this.auditPort = auditPort;
    }

    public AccessDecision checkFeature(FeatureCheckCommand command) {
        if (entitlementGrantRepository != null) {
            try {
                List<EntitlementGrantRepository.EntitlementGrantRecord> grants =
                        entitlementGrantRepository.findActiveBySubjectId(command.subjectId());
                Instant now = Instant.now();
                boolean expired = grants.stream().anyMatch(g ->
                        g.expiresAt() != null && g.expiresAt().isBefore(now));
                if (expired) {
                    return new AccessDecision(false, "DENY", "EXPIRED",
                            "Entitlement grant has expired", null,
                            List.of(), null, null, null, null,
                            null, List.of(), null, false);
                }
                if (!grants.isEmpty()) {
                    boolean granted = grants.stream()
                            .anyMatch(g -> g.bundleCode().equals(command.featureCode()));
                    String reason = granted ? "explicit-grant" : "no-grant";
                    return new AccessDecision(granted, granted ? "ALLOW" : "DENY", reason,
                            granted ? "Access granted" : "Access denied", null,
                            List.of(), null, null, null, null,
                            null, List.of(), null, false);
                }
            } catch (Exception e) {
                log.warn("Failed to check feature grants from DB for {}: {}", command.subjectId(), e.getMessage());
            }
        }

        Set<String> features = featureGrants.get(command.subjectId());
        boolean granted = features != null && features.contains(command.featureCode());
        String reason = granted ? "explicit-grant" : "no-grant";

        AccessDecision decision = new AccessDecision(granted, granted ? "ALLOW" : "DENY", reason,
                granted ? "Access granted" : "Access denied", null,
                List.of(), null, null, null, null,
                null, List.of(), null, false);

        EntitlementSnapshot snapshot = snapshots.get(command.subjectId());
        if (snapshot != null) {
            cache.put(snapshot);
        }

        return decision;
    }

    public AccessDecision checkFeatureAccess(FeatureCheckCommand command) {
        return checkFeature(command);
    }

    public String explainAccess(String subjectId, String featureCode) {
        Set<String> features = featureGrants.get(subjectId);
        boolean granted = features != null && features.contains(featureCode);

        if (granted) {
            return String.format("Access GRANTED to '%s' for subject '%s': feature is in the granted feature set.",
                    featureCode, subjectId);
        }

        String quotaProfile = quotaProfiles.get(subjectId);
        if (quotaProfile != null) {
            return String.format("Access DENIED to '%s' for subject '%s': feature not in granted set (quota profile: %s).",
                    featureCode, subjectId, quotaProfile);
        }

        return String.format("Access DENIED to '%s' for subject '%s': no entitlements found.",
                featureCode, subjectId);
    }

    public EntitlementChangedEvent grantEntitlement(EntitlementGrant grant) {
        if (entitlementGrantRepository != null) {
            try {
                String grantId = grant.grantId() != null ? grant.grantId() : Ids.newId("ent_grant");
                entitlementGrantRepository.save(
                        grantId,
                        grant.subjectType() != null ? grant.subjectType() : "TENANT",
                        grant.subjectId(),
                        grant.bundleKey(),
                        grant.quotaProfileKey(),
                        grant.source() != null ? grant.source() : "admin",
                        null,
                        grant.status() != null ? grant.status().name() : "ACTIVE",
                        grant.startsAt() != null ? grant.startsAt() : Instant.now(),
                        grant.expiresAt()
                );
                log.debug("Persisted entitlement grant: {} for subject: {}", grantId, grant.subjectId());
            } catch (Exception e) {
                log.warn("Failed to persist entitlement grant for {}: {}", grant.subjectId(), e.getMessage());
            }
        }

        featureGrants.computeIfAbsent(grant.subjectId(), k -> ConcurrentHashMap.newKeySet())
                .add(grant.bundleKey());

        if (grant.quotaProfileKey() != null && !grant.quotaProfileKey().isBlank()) {
            quotaProfiles.put(grant.subjectId(), grant.quotaProfileKey());
        }

        String eventId = Ids.newId("ent");
        EntitlementChangedEvent event = new EntitlementChangedEvent(
                grant.subjectId(),
                "entitlement.granted",
                "billing.contract.activated"
        );
        changeEvents.put(eventId, event);

        EntitlementSnapshot snapshot = new EntitlementSnapshot(
                grant.subjectId(),
                List.of(grant.bundleKey()),
                grant.quotaProfileKey(),
                grant.expiresAt()
        );
        snapshots.put(grant.subjectId(), snapshot);
        cache.put(snapshot);

        audit("entitlement.granted", grant.grantedBy(), Map.of(
                "subjectId", grant.subjectId(),
                "featureKey", grant.featureKey(),
                "bundleKey", grant.bundleKey()));

        return event;
    }

    public EntitlementChangedEvent revokeEntitlement(String grantId, String revokedBy, String reason) {
        if (entitlementGrantRepository != null) {
            try {
                List<EntitlementGrantRepository.EntitlementGrantRecord> all =
                        entitlementGrantRepository.findBySubjectId(grantId);
                Optional<EntitlementGrantRepository.EntitlementGrantRecord> target = all.stream()
                        .filter(g -> g.id().equals(grantId))
                        .findFirst();
                if (target.isPresent()) {
                    EntitlementGrantRepository.EntitlementGrantRecord rec = target.get();
                    entitlementGrantRepository.save(
                            rec.id(), rec.subjectType(), rec.subjectId(),
                            rec.bundleCode(), rec.quotaProfileCode(),
                            rec.sourceType(), rec.sourceRef(),
                            "REVOKED", rec.effectiveAt(), rec.expiresAt());
                }
            } catch (Exception e) {
                log.warn("Failed to persist grant revocation for {}: {}", grantId, e.getMessage());
            }
        }

        String eventId = Ids.newId("ent");
        EntitlementChangedEvent event = new EntitlementChangedEvent(
                grantId, "entitlement.revoked", "admin.revoked");
        changeEvents.put(eventId, event);

        audit("entitlement.revoked", revokedBy, Map.of(
                "grantId", grantId, "reason", reason != null ? reason : "unspecified"));

        log.info("Revoked entitlement grant: {} by {} reason: {}", grantId, revokedBy, reason);
        return event;
    }

    public EntitlementChangedEvent extendGrant(String grantId, Instant newExpiresAt) {
        if (entitlementGrantRepository != null) {
            try {
                List<EntitlementGrantRepository.EntitlementGrantRecord> all =
                        entitlementGrantRepository.findBySubjectId(grantId);
                Optional<EntitlementGrantRepository.EntitlementGrantRecord> target = all.stream()
                        .filter(g -> g.id().equals(grantId))
                        .findFirst();
                if (target.isPresent()) {
                    EntitlementGrantRepository.EntitlementGrantRecord rec = target.get();
                    entitlementGrantRepository.save(
                            rec.id(), rec.subjectType(), rec.subjectId(),
                            rec.bundleCode(), rec.quotaProfileCode(),
                            rec.sourceType(), rec.sourceRef(),
                            rec.grantStatus(), rec.effectiveAt(), newExpiresAt);
                }
            } catch (Exception e) {
                log.warn("Failed to persist grant extension for {}: {}", grantId, e.getMessage());
            }
        }

        String eventId = Ids.newId("ent");
        EntitlementChangedEvent event = new EntitlementChangedEvent(
                grantId, "entitlement.extended", "admin.extended");
        changeEvents.put(eventId, event);

        audit("entitlement.extended", "system", Map.of(
                "grantId", grantId, "newExpiresAt", newExpiresAt));

        log.info("Extended entitlement grant: {} to {}", grantId, newExpiresAt);
        return event;
    }

    public List<EntitlementGrantRepository.EntitlementGrantRecord> listGrants(String subjectId) {
        if (entitlementGrantRepository != null) {
            return entitlementGrantRepository.findBySubjectId(subjectId);
        }
        return List.of();
    }

    public Optional<EntitlementGrantRepository.EntitlementGrantRecord> getGrant(String grantId) {
        if (entitlementGrantRepository != null) {
            return entitlementGrantRepository.findBySubjectId(grantId).stream()
                    .filter(g -> g.id().equals(grantId))
                    .findFirst();
        }
        return Optional.empty();
    }

    public QuotaDecision getQuotaProfile(String subjectId) {
        String profileCode = quotaProfiles.get(subjectId);
        if (profileCode == null) {
            return new QuotaDecision(subjectId, "default", false, 0, 0);
        }

        long limitValue = switch (profileCode) {
            case "pro_quota" -> 10000L;
            case "basic_quota" -> 1000L;
            case "enterprise_quota" -> 100000L;
            default -> 100L;
        };

        return new QuotaDecision(subjectId, profileCode, true, limitValue, 0);
    }

    public QuotaDecision checkQuota(String subjectId, String quotaCode, double requestedQuantity) {
        QuotaDecision profile = getQuotaProfile(subjectId);
        boolean allowed = requestedQuantity <= profile.limitValue();
        return new QuotaDecision(subjectId, quotaCode, allowed, profile.limitValue(), profile.usedValue());
    }

    public EntitlementSnapshot getSnapshot(String subjectId) {
        EntitlementSnapshot cached = cache.get(subjectId);
        if (cached != null) {
            Instant now = Instant.now();
            if (cached.expiresAt() != null && cached.expiresAt().isBefore(now)) {
                log.debug("Cached snapshot expired for {}", subjectId);
            } else {
                return cached;
            }
        }

        if (entitlementGrantRepository != null) {
            try {
                Instant now = Instant.now();
                List<EntitlementGrantRepository.EntitlementGrantRecord> grants =
                        entitlementGrantRepository.findActiveBySubjectId(subjectId);
                if (!grants.isEmpty()) {
                    boolean anyExpired = grants.stream()
                            .anyMatch(g -> g.expiresAt() != null && g.expiresAt().isBefore(now));
                    if (anyExpired) {
                        log.debug("Some grants expired for {}", subjectId);
                    }
                    List<String> featureCodes = grants.stream()
                            .map(EntitlementGrantRepository.EntitlementGrantRecord::bundleCode)
                            .distinct()
                            .toList();
                    String quotaProfile = grants.stream()
                            .map(EntitlementGrantRepository.EntitlementGrantRecord::quotaProfileCode)
                            .filter(qp -> qp != null && !qp.isBlank())
                            .findFirst()
                            .orElse(null);
                    Instant expiresAt = grants.stream()
                            .map(EntitlementGrantRepository.EntitlementGrantRecord::expiresAt)
                            .filter(Objects::nonNull)
                            .min(Instant::compareTo)
                            .orElse(Instant.now().plusSeconds(86400 * 30L));

                    EntitlementSnapshot snapshot = new EntitlementSnapshot(
                            subjectId, featureCodes, quotaProfile, expiresAt);
                    snapshots.put(subjectId, snapshot);
                    cache.put(snapshot);
                    return snapshot;
                }
            } catch (Exception e) {
                log.warn("Failed to load entitlement grants from DB for {}: {}", subjectId, e.getMessage());
            }
        }

        EntitlementSnapshot stored = snapshots.get(subjectId);
        if (stored != null) {
            return stored;
        }

        return new EntitlementSnapshot(
                subjectId,
                List.of("render.job.create", "ai.model.standard"),
                "pro_quota",
                Instant.now().plusSeconds(86400 * 30L)
        );
    }

    public List<EntitlementChangedEvent> getChangeEvents() {
        return List.copyOf(changeEvents.values());
    }

    private void audit(String action, String actor, Map<String, Object> payload) {
        if (auditPort != null) {
            auditPort.record("ADMIN", action, "ENTITLEMENT",
                    "GRANT", payload.getOrDefault("grantId", payload.getOrDefault("subjectId", "unknown")).toString(), payload);
        }
    }
}
