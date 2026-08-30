package com.example.platform.entitlement.app;

import com.example.platform.entitlement.infrastructure.TenantTierJdbcRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Commercial tier metadata projection.
 *
 * <p>This service never answers capability existence, runtime/provider availability,
 * preset support, entitlement grants, or quota decisions. Those decisions belong to
 * their typed canonical authorities.</p>
 */
@Service
public class EntitlementPolicyService {
    private static final Logger log = LoggerFactory.getLogger(EntitlementPolicyService.class);

    private final ConcurrentHashMap<String, String> tierMetadata = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> decisionSources = new ConcurrentHashMap<>();
    private final TenantTierJdbcRepository tierRepository;

    public EntitlementPolicyService(Optional<TenantTierJdbcRepository> tierRepository) {
        this.tierRepository = tierRepository.orElse(null);
    }

    /** Restore a read projection from durable commercial metadata at startup. */
    public void hydrateTier(String tenantId, String tier) {
        if (tenantId != null && !tenantId.isBlank() && tier != null && !tier.isBlank()) {
            tierMetadata.put(tenantId, normalize(tier));
            decisionSources.put(tenantId, "durable-tier-metadata");
        }
    }

    /** Missing metadata is reported as FREE for presentation only; it grants nothing. */
    public String getTier(String tenantId) {
        return tierMetadata.getOrDefault(tenantId, "FREE");
    }

    /** Persist first; a failed write cannot change the read projection. */
    public void setTier(String tenantId, String tier) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        String normalized = normalize(tier);
        if (tierRepository != null) tierRepository.upsert(tenantId, normalized);
        tierMetadata.put(tenantId, normalized);
        decisionSources.put(tenantId, "durable-tier-metadata");
        log.info("Commercial tier metadata updated: tier={} tenant={}", normalized, tenantId);
    }

    public String getDecisionSource(String tenantId) {
        return decisionSources.get(tenantId);
    }

    public void refreshPolicies() {
        decisionSources.clear();
    }

    private static String normalize(String tier) {
        return tier == null || tier.isBlank() ? "FREE" : tier.toUpperCase(Locale.ROOT);
    }
}
