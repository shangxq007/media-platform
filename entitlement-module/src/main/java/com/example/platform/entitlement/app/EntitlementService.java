package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.*;
import com.example.platform.entitlement.infrastructure.EntitlementGrantRepository;
import com.example.platform.entitlement.infrastructure.InMemoryEntitlementCache;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    public EntitlementService(InMemoryEntitlementCache cache,
                              @Autowired(required = false) EntitlementGrantRepository entitlementGrantRepository) {
        this.cache = cache;
        this.entitlementGrantRepository = entitlementGrantRepository;
    }

    public AccessDecision checkFeature(FeatureCheckCommand command) {
        if (entitlementGrantRepository != null) {
            try {
                List<EntitlementGrantRepository.EntitlementGrantRecord> grants =
                        entitlementGrantRepository.findActiveBySubjectId(command.subjectId());
                if (!grants.isEmpty()) {
                    boolean granted = grants.stream()
                            .anyMatch(g -> g.bundleCode().equals(command.featureCode()));
                    String reason = granted ? "explicit-grant" : "no-grant";
                    return new AccessDecision(command.subjectId(), command.featureCode(), granted, reason);
                }
            } catch (Exception e) {
                log.warn("Failed to check feature grants from DB for {}: {}", command.subjectId(), e.getMessage());
            }
        }

        Set<String> features = featureGrants.get(command.subjectId());
        boolean granted = features != null && features.contains(command.featureCode());
        String reason = granted ? "explicit-grant" : "no-grant";

        AccessDecision decision = new AccessDecision(command.subjectId(), command.featureCode(), granted, reason);

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
                String grantId = Ids.newId("ent_grant");
                entitlementGrantRepository.save(
                        grantId,
                        "tenant",
                        grant.subjectId(),
                        grant.featureBundleCode(),
                        grant.quotaProfileCode(),
                        "billing",
                        null,
                        "ACTIVE",
                        Instant.now(),
                        grant.effectiveUntil()
                );
                log.debug("Persisted entitlement grant: {} for subject: {}", grantId, grant.subjectId());
            } catch (Exception e) {
                log.warn("Failed to persist entitlement grant for {}: {}", grant.subjectId(), e.getMessage());
            }
        }

        featureGrants.computeIfAbsent(grant.subjectId(), k -> ConcurrentHashMap.newKeySet())
                .add(grant.featureBundleCode());

        if (grant.quotaProfileCode() != null && !grant.quotaProfileCode().isBlank()) {
            quotaProfiles.put(grant.subjectId(), grant.quotaProfileCode());
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
                List.of(grant.featureBundleCode()),
                grant.quotaProfileCode(),
                grant.effectiveUntil()
        );
        snapshots.put(grant.subjectId(), snapshot);
        cache.put(snapshot);

        return event;
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
            return cached;
        }

        if (entitlementGrantRepository != null) {
            try {
                List<EntitlementGrantRepository.EntitlementGrantRecord> grants =
                        entitlementGrantRepository.findActiveBySubjectId(subjectId);
                if (!grants.isEmpty()) {
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
                            .max(Instant::compareTo)
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
}
