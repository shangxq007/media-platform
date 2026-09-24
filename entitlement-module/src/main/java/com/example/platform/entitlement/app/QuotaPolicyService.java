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

    public boolean isExceeded(String featureCode, java.math.BigDecimal currentUsage) {
        QuotaPolicy policy = getQuotaPolicy(featureCode);
        return policy.isExceeded(currentUsage);
    }
    public boolean isExceeded(String featureCode, long currentUsage) { return isExceeded(featureCode, java.math.BigDecimal.valueOf(currentUsage)); }

    public boolean isWarning(String featureCode, java.math.BigDecimal currentUsage) {
        QuotaPolicy policy = getQuotaPolicy(featureCode);
        return policy.isWarning(currentUsage);
    }
    public boolean isWarning(String featureCode, long currentUsage) { return isWarning(featureCode, java.math.BigDecimal.valueOf(currentUsage)); }

    public java.math.BigDecimal remaining(String featureCode, java.math.BigDecimal currentUsage) {
        QuotaPolicy policy = getQuotaPolicy(featureCode);
        return policy.remaining(currentUsage);
    }

    public java.math.BigDecimal resolveLimitFromProfile(QuotaProfile profile, String featureCode) {
        if (featureCode.startsWith("render")) return java.math.BigDecimal.valueOf(profile.monthlyRenderMinutes());
        if (featureCode.startsWith("gpu")) return java.math.BigDecimal.valueOf(profile.gpuMinutes());
        if (featureCode.startsWith("prompt")) return java.math.BigDecimal.valueOf(profile.promptExecutions());
        if (featureCode.startsWith("extension")) return java.math.BigDecimal.valueOf(profile.extensionExecutions());
        if (featureCode.startsWith("api")) return java.math.BigDecimal.valueOf(profile.apiCallsPerMinute());
        if (featureCode.startsWith("mcp")) return java.math.BigDecimal.valueOf(profile.mcpCallsPerMinute());
        throw new IllegalArgumentException("Unknown quota profile dimension: " + featureCode);
    }

    private static QuotaPolicy policy(String id, String key, long limit) {
        return new QuotaPolicy(id, "default", key, limit, "MONTHLY", 80);
    }
}
