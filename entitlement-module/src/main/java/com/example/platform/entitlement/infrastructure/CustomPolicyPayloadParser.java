package com.example.platform.entitlement.infrastructure;

import com.example.platform.entitlement.domain.EntitlementPolicy;
import com.example.platform.shared.Jsons;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CustomPolicyPayloadParser {

    private static final Logger log = LoggerFactory.getLogger(CustomPolicyPayloadParser.class);

    private CustomPolicyPayloadParser() {}

    public static EntitlementPolicy parse(String tenantId, String overrideId, String payloadJson) {
        EntitlementPolicy base = EntitlementPolicy.enterpriseTier();
        if (payloadJson == null || payloadJson.isBlank()) {
            return withSource(base, tenantId, overrideId, "empty_payload_defaults");
        }
        try {
            CustomPolicyPayload payload = Jsons.fromJson(payloadJson, CustomPolicyPayload.class);
            if (payload.tier() != null && !"CUSTOM".equalsIgnoreCase(payload.tier())) {
                EntitlementPolicy tierBase = EntitlementPolicy.forTier(payload.tier());
                return merge(tierBase, payload, tenantId, overrideId);
            }
            return merge(base, payload, tenantId, overrideId);
        } catch (Exception e) {
            log.warn("Failed to parse custom policy payload for tenant {}: {}", tenantId, e.getMessage());
            return withSource(base, tenantId, overrideId, "parse_error_defaults");
        }
    }

    private static EntitlementPolicy merge(EntitlementPolicy base, CustomPolicyPayload p,
                                           String tenantId, String overrideId) {
        String policyId = overrideId != null ? "custom-" + overrideId : "custom-" + tenantId;
        String tier = p.tier() != null ? p.tier().toUpperCase() : "CUSTOM";
        return new EntitlementPolicy(
                policyId,
                tier,
                orDefault(p.maxResolutionWidth(), base.maxResolutionWidth()),
                orDefault(p.maxResolutionHeight(), base.maxResolutionHeight()),
                orDefault(p.monthlyRenderMinutes(), base.monthlyRenderMinutes()),
                orDefault(p.watermark(), base.watermark()),
                toSet(p.allowedProviders(), base.allowedProviders()),
                orDefault(p.gpuAllowed(), base.gpuAllowed()),
                orDefault(p.remoteWorkerAllowed(), base.remoteWorkerAllowed()),
                orDefault(p.maxSubtitleTracks(), base.maxSubtitleTracks()),
                orDefault(p.customFontsAllowed(), base.customFontsAllowed()),
                toSet(p.effectPacksAllowed(), base.effectPacksAllowed()),
                toSet(p.exportFormats(), base.exportFormats()),
                orDefault(p.maxConcurrentJobs(), base.maxConcurrentJobs()),
                mergeExtra(base.extra(), p.extra(), tenantId, overrideId));
    }

    private static EntitlementPolicy withSource(EntitlementPolicy base, String tenantId,
                                                String overrideId, String source) {
        Map<String, String> extra = new LinkedHashMap<>(base.extra() != null ? base.extra() : Map.of());
        extra.put("source", source);
        extra.put("tenantId", tenantId);
        if (overrideId != null) {
            extra.put("overrideId", overrideId);
        }
        return new EntitlementPolicy(
                "custom-" + tenantId,
                "CUSTOM",
                base.maxResolutionWidth(),
                base.maxResolutionHeight(),
                base.monthlyRenderMinutes(),
                base.watermark(),
                base.allowedProviders(),
                base.gpuAllowed(),
                base.remoteWorkerAllowed(),
                base.maxSubtitleTracks(),
                base.customFontsAllowed(),
                base.effectPacksAllowed(),
                base.exportFormats(),
                base.maxConcurrentJobs(),
                extra);
    }

    private static Map<String, String> mergeExtra(Map<String, String> baseExtra,
                                                  Map<String, String> payloadExtra,
                                                  String tenantId, String overrideId) {
        Map<String, String> extra = new LinkedHashMap<>(baseExtra != null ? baseExtra : Map.of());
        if (payloadExtra != null) {
            extra.putAll(payloadExtra);
        }
        extra.put("source", "custom_policy_db");
        extra.put("tenantId", tenantId);
        if (overrideId != null) {
            extra.put("overrideId", overrideId);
        }
        return extra;
    }

    private static Set<String> toSet(List<String> values, Set<String> fallback) {
        return values != null && !values.isEmpty() ? Set.copyOf(values) : fallback;
    }

    private static int orDefault(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    private static long orDefault(Long value, long fallback) {
        return value != null ? value : fallback;
    }

    private static boolean orDefault(Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }
}
