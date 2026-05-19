package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.*;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class OpenFeatureFlagEvaluator {

    private static final Logger log = LoggerFactory.getLogger(OpenFeatureFlagEvaluator.class);

    private final Client client;

    public OpenFeatureFlagEvaluator() {
        this.client = OpenFeatureAPI.getInstance().getClient();
    }

    public FeatureFlagDecision evaluate(FeatureFlagEvaluationRequest request) {
        try {
            FeatureFlagContext ctx = request.context();
            EvaluationContext evalCtx = ctx != null ? OpenFeatureContextMapper.map(ctx) : new ImmutableContext();

            String flagKey = request.flagKey();
            Object defaultValue = request.defaultValue();

            boolean enabled = false;
            String variant = null;

            if (defaultValue instanceof Boolean) {
                enabled = client.getBooleanValue(flagKey, (Boolean) defaultValue, evalCtx);
                variant = enabled ? "enabled" : "disabled";
            } else if (defaultValue instanceof String) {
                String strVal = client.getStringValue(flagKey, (String) defaultValue, evalCtx);
                enabled = strVal != null && !strVal.isBlank();
                variant = strVal;
            } else if (defaultValue instanceof Number) {
                double numVal = client.getDoubleValue(flagKey, ((Number) defaultValue).doubleValue(), evalCtx);
                enabled = numVal != 0;
                variant = String.valueOf(numVal);
            } else {
                var jsonVal = client.getObjectValue(flagKey, defaultValue != null ? new Value(defaultValue) : new Value(""), evalCtx);
                enabled = jsonVal != null;
                variant = jsonVal != null ? jsonVal.toString() : null;
            }

            Map<String, Object> details = new HashMap<>();
            details.put("flagKey", flagKey);
            details.put("provider", "openfeature");
            if (ctx != null && ctx.tenantId() != null) {
                details.put("tenantId", ctx.tenantId());
            }

            return new FeatureFlagDecision(
                    flagKey, enabled, variant, "EVALUATED",
                    FeatureFlagProviderType.OPENFEATURE, null,
                    ctx != null ? ctx.tenantId() : null,
                    ctx != null ? ctx.workspaceId() : null,
                    ctx != null ? ctx.userId() : null,
                    Instant.now(), details
            );
        } catch (Exception e) {
            log.error("FF-EVAL-OPENFEATURE-001: OpenFeature evaluation failed for flag '{}': {}",
                    request.flagKey(), e.getMessage(), e);
            boolean fallbackEnabled = request.defaultValue() instanceof Boolean ? (Boolean) request.defaultValue() : false;
            Map<String, Object> errorDetails = Map.of(
                    "errorCode", "FF-EVAL-OPENFEATURE-001",
                    "errorMessage", e.getMessage(),
                    "fallback", true
            );
            return new FeatureFlagDecision(
                    request.flagKey(), fallbackEnabled, null, "ERROR",
                    FeatureFlagProviderType.OPENFEATURE, null,
                    request.context() != null ? request.context().tenantId() : null,
                    request.context() != null ? request.context().workspaceId() : null,
                    request.context() != null ? request.context().userId() : null,
                    Instant.now(), errorDetails
            );
        }
    }

    public FeatureFlagDecision evaluateBoolean(String flagKey, FeatureFlagContext context, boolean defaultValue) {
        return evaluate(new FeatureFlagEvaluationRequest(flagKey, context, defaultValue));
    }

    public FeatureFlagDecision evaluateString(String flagKey, FeatureFlagContext context, String defaultValue) {
        return evaluate(new FeatureFlagEvaluationRequest(flagKey, context, defaultValue));
    }

    public FeatureFlagDecision evaluateNumber(String flagKey, FeatureFlagContext context, double defaultValue) {
        return evaluate(new FeatureFlagEvaluationRequest(flagKey, context, defaultValue));
    }
}
