package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.FeatureFlagContext;
import dev.openfeature.sdk.EvaluationContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpenFeatureContextMapperTest {

    @Test
    void mapNullContextReturnsEmptyContext() {
        EvaluationContext ctx = OpenFeatureContextMapper.map(null);
        assertNotNull(ctx);
        assertNull(ctx.getTargetingKey());
    }

    @Test
    void mapUsesUserIdAsTargetingKey() {
        FeatureFlagContext context = new FeatureFlagContext(
                "tenant-1", "ws-1", "user-1", List.of(), List.of(),
                null, null, null, null, null, Map.of());
        EvaluationContext ctx = OpenFeatureContextMapper.map(context);
        assertEquals("user-1", ctx.getTargetingKey());
    }

    @Test
    void mapUsesTenantIdAsTargetingKeyWhenNoUserId() {
        FeatureFlagContext context = new FeatureFlagContext(
                "tenant-1", "ws-1", null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        EvaluationContext ctx = OpenFeatureContextMapper.map(context);
        assertEquals("tenant-1", ctx.getTargetingKey());
    }

    @Test
    void mapUsesEmptyStringWhenNoUserOrTenant() {
        FeatureFlagContext context = new FeatureFlagContext(
                null, null, null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        EvaluationContext ctx = OpenFeatureContextMapper.map(context);
        assertEquals("", ctx.getTargetingKey());
    }

    @Test
    void mapSetsScalarAttributes() {
        FeatureFlagContext context = new FeatureFlagContext(
                "tenant-1", "ws-1", "user-1",
                List.of("ADMIN"), List.of("group-1"),
                "enterprise", "api", "prod", "us-east", null, Map.of());
        EvaluationContext ctx = OpenFeatureContextMapper.map(context);
        assertEquals("tenant-1", ctx.getValue("tenantId").asString());
        assertEquals("ws-1", ctx.getValue("workspaceId").asString());
        assertEquals("user-1", ctx.getValue("userId").asString());
        assertEquals("enterprise", ctx.getValue("tier").asString());
        assertEquals("api", ctx.getValue("requestSource").asString());
        assertEquals("prod", ctx.getValue("environment").asString());
        assertEquals("us-east", ctx.getValue("region").asString());
    }

    @Test
    void mapSetsListAttributes() {
        FeatureFlagContext context = new FeatureFlagContext(
                null, null, null,
                List.of("ADMIN", "USER"), List.of("g1", "g2"),
                null, null, null, null, null, Map.of());
        EvaluationContext ctx = OpenFeatureContextMapper.map(context);
        assertNotNull(ctx.getValue("roles"));
        assertNotNull(ctx.getValue("groups"));
    }

    @Test
    void mapSetsCustomAttributes() {
        FeatureFlagContext context = new FeatureFlagContext(
                null, null, null, List.of(), List.of(),
                null, null, null, null, null,
                Map.of("customKey", "customValue", "numValue", 42));
        EvaluationContext ctx = OpenFeatureContextMapper.map(context);
        assertEquals("customValue", ctx.getValue("customKey").asString());
        assertEquals(42, ctx.getValue("numValue").asInteger());
    }

    @Test
    void mapIgnoresNullFields() {
        FeatureFlagContext context = new FeatureFlagContext(
                "tenant-1", null, null, null, null,
                null, null, null, null, null, Map.of());
        EvaluationContext ctx = OpenFeatureContextMapper.map(context);
        assertEquals("tenant-1", ctx.getValue("tenantId").asString());
        assertNull(ctx.getValue("workspaceId"));
        assertNull(ctx.getValue("userId"));
    }
}
