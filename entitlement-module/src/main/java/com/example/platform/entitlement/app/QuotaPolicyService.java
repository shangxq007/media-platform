package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.QuotaPolicy;
import com.example.platform.entitlement.domain.QuotaProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QuotaPolicyService {

    private static final Logger log = LoggerFactory.getLogger(QuotaPolicyService.class);

    private final Map<String, QuotaPolicy> quotaPolicies = new ConcurrentHashMap<>();

    public QuotaPolicyService() {
        quotaPolicies.put("render.job.create", new QuotaPolicy("qp-render", "default", "render.job.create", 10000, "MONTHLY", 80));
        quotaPolicies.put("ai.model.standard", new QuotaPolicy("qp-ai-std", "default", "ai.model.standard", 1000, "MONTHLY", 80));
        quotaPolicies.put("ai.model.premium", new QuotaPolicy("qp-ai-prem", "default", "ai.model.premium", 100, "MONTHLY", 80));
        quotaPolicies.put("export.gpu", new QuotaPolicy("qp-gpu", "default", "export.gpu", 500, "MONTHLY", 80));
        quotaPolicies.put("extension.execute", new QuotaPolicy("qp-ext", "default", "extension.execute", 50, "MONTHLY", 80));
        quotaPolicies.put("prompt.execute", new QuotaPolicy("qp-prompt", "default", "prompt.execute", 10000, "MONTHLY", 80));
    }

    public QuotaPolicy getQuotaPolicy(String featureCode) {
        return quotaPolicies.getOrDefault(featureCode,
                new QuotaPolicy("qp-default", "default", featureCode, 100, "MONTHLY", 80));
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
        return profile.monthlyRenderMinutes();
    }

    public void registerPolicy(QuotaPolicy policy) {
        quotaPolicies.put(policy.featureCode(), policy);
    }
}
