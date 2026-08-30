package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.QuotaPolicy;
import com.example.platform.entitlement.domain.QuotaProfile;
import org.springframework.stereotype.Service;

@Service
public class QuotaPolicyService {

    public QuotaPolicy getQuotaPolicy(String featureCode) {
        if (featureCode == null || featureCode.isBlank()) {
            throw new IllegalArgumentException("quota key is required");
        }
        return switch (featureCode) {
            case "render.job.create" -> policy("qp-render", featureCode, 10000);
            case "ai.model.standard" -> policy("qp-ai-std", featureCode, 1000);
            case "ai.model.premium" -> policy("qp-ai-prem", featureCode, 100);
            case "export.gpu" -> policy("qp-gpu", featureCode, 500);
            case "extension.execute" -> policy("qp-ext", featureCode, 50);
            case "prompt.execute" -> policy("qp-prompt", featureCode, 10000);
            default -> throw new IllegalArgumentException("Unknown canonical quota key: " + featureCode);
        };
    }

    public boolean isExceeded(String featureCode, long currentUsage) {
        QuotaPolicy policy = getQuotaPolicy(featureCode);
        return policy.isExceeded(currentUsage);
    }

    public boolean isWarning(String featureCode, long currentUsage) {
        QuotaPolicy policy = getQuotaPolicy(featureCode);
        return policy.isWarning(currentUsage);
    }

    public long remaining(String featureCode, long currentUsage) {
        QuotaPolicy policy = getQuotaPolicy(featureCode);
        return policy.remaining(currentUsage);
    }

    public long resolveLimitFromProfile(QuotaProfile profile, String featureCode) {
        if (featureCode.startsWith("render")) return profile.monthlyRenderMinutes();
        if (featureCode.startsWith("gpu")) return profile.gpuMinutes();
        if (featureCode.startsWith("prompt")) return profile.promptExecutions();
        if (featureCode.startsWith("extension")) return profile.extensionExecutions();
        if (featureCode.startsWith("api")) return profile.apiCallsPerMinute();
        if (featureCode.startsWith("mcp")) return profile.mcpCallsPerMinute();
        throw new IllegalArgumentException("Unknown quota profile dimension: " + featureCode);
    }

    private static QuotaPolicy policy(String id, String key, long limit) {
        return new QuotaPolicy(id, "default", key, limit, "MONTHLY", 80);
    }
}
