package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.FeatureFlagContext;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.Structure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class OpenFeatureContextMapper {

    private OpenFeatureContextMapper() {}

    public static EvaluationContext map(FeatureFlagContext context) {
        if (context == null) {
            return new ImmutableContext();
        }
        String targetingKey = context.userId() != null ? context.userId()
                : context.tenantId() != null ? context.tenantId() : "";
        Map<String, Value> attributes = new HashMap<>();
        if (context.tenantId() != null) {
            attributes.put("tenantId", new Value(context.tenantId()));
        }
        if (context.workspaceId() != null) {
            attributes.put("workspaceId", new Value(context.workspaceId()));
        }
        if (context.userId() != null) {
            attributes.put("userId", new Value(context.userId()));
        }
        if (context.roles() != null && !context.roles().isEmpty()) {
            attributes.put("roles", new Value(context.roles().stream()
                    .map(Value::new).collect(Collectors.toList())));
        }
        if (context.groups() != null && !context.groups().isEmpty()) {
            attributes.put("groups", new Value(context.groups().stream()
                    .map(Value::new).collect(Collectors.toList())));
        }
        if (context.tier() != null) {
            attributes.put("tier", new Value(context.tier()));
        }
        if (context.requestSource() != null) {
            attributes.put("requestSource", new Value(context.requestSource()));
        }
        if (context.environment() != null) {
            attributes.put("environment", new Value(context.environment()));
        }
        if (context.region() != null) {
            attributes.put("region", new Value(context.region()));
        }
        if (context.riskLevel() != null) {
            attributes.put("riskLevel", new Value(context.riskLevel()));
        }
        if (context.attributes() != null) {
            context.attributes().forEach((k, v) -> attributes.put(k, toValue(v)));
        }
        return new ImmutableContext(targetingKey, attributes);
    }

    private static Value toValue(Object v) {
        if (v == null) return new Value("");
        if (v instanceof Value val) return val;
        if (v instanceof String s) return new Value(s);
        if (v instanceof Boolean b) return new Value(b);
        if (v instanceof List<?> list) {
            return new Value(list.stream().map(OpenFeatureContextMapper::toValue).collect(Collectors.toList()));
        }
        if (v instanceof Map<?, ?> map) {
            Map<String, Object> mapped = new HashMap<>();
            map.forEach((k, val) -> mapped.put(String.valueOf(k), val));
            return new Value(Structure.mapToStructure(mapped));
        }
        try {
            if (v instanceof Number n) return new Value(n);
        } catch (Exception e) {
            return new Value(v.toString());
        }
        return new Value(v.toString());
    }
}
