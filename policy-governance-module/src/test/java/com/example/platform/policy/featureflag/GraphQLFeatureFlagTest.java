package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.*;
import com.example.platform.policy.featureflag.AppFeaturesProperties;
import com.example.platform.policy.featureflag.FeatureFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLFeatureFlagTest {

    private FeatureFlagService service;
    private LocalFeatureFlagProvider localProvider;

    @BeforeEach
    void setUp() {
        localProvider = new LocalFeatureFlagProvider();
        AppFeaturesProperties props = new AppFeaturesProperties();
        OpenFeatureFlagEvaluator openFeatureEvaluator = new OpenFeatureFlagEvaluator();
        service = new FeatureFlagService(localProvider, openFeatureEvaluator, props);
    }

    @Test
    void graphQLAggregationFlagCreated() {
        FeatureFlagDefinition def = new FeatureFlagDefinition(
                "graphql.queryAggregation.enabled", "GraphQL Aggregation",
                "Controls GraphQL query aggregation feature",
                FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "platform", List.of("graphql", "query"),
                null, null, false
        );
        FeatureFlagDefinition created = service.createFlag(def);
        assertEquals("graphql.queryAggregation.enabled", created.flagKey());
    }

    @Test
    void graphQLAggregationFlagEvaluatesForAdmin() {
        service.createFlag(new FeatureFlagDefinition(
                "graphql.aggregation.admin", "GraphQL Admin", null,
                FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "platform", List.of("graphql"),
                null, null, false
        ));
        localProvider.saveRule("graphql.aggregation.admin", new FeatureFlagTargetingRule(
                "r-gql-admin", "graphql.aggregation.admin", 10, true,
                null, null, null, "ADMIN", null, null,
                null, null, null, null, null, null
        ));

        FeatureFlagContext ctx = new FeatureFlagContext(
                "tenant-1", "ws-1", "user-1", List.of("ADMIN"), List.of(),
                null, "api", null, null, null, Map.of());
        FeatureFlagEvaluationResult result = service.evaluate(
                new FeatureFlagEvaluationRequest("graphql.aggregation.admin", ctx, false));

        assertTrue(result.decision().enabled());
        assertEquals("RULE_MATCHED", result.decision().reasonCode());
    }

    @Test
    void graphQLAggregationFlagDeniedForNonAdmin() {
        service.createFlag(new FeatureFlagDefinition(
                "graphql.aggregation.role", "GraphQL Role", null,
                FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "platform", List.of("graphql"),
                null, null, false
        ));
        localProvider.saveRule("graphql.aggregation.role", new FeatureFlagTargetingRule(
                "r-gql-role", "graphql.aggregation.role", 10, true,
                null, null, null, "ADMIN", null, null,
                null, null, null, null, null, null
        ));

        FeatureFlagContext ctx = new FeatureFlagContext(
                "tenant-1", "ws-1", "user-1", List.of("USER"), List.of(),
                null, "api", null, null, null, Map.of());
        FeatureFlagEvaluationResult result = service.evaluate(
                new FeatureFlagEvaluationRequest("graphql.aggregation.role", ctx, false));

        assertFalse(result.decision().enabled());
        assertEquals("NO_MATCHING_RULE", result.decision().reasonCode());
    }

    @Test
    void graphQLPlaygroundFlag() {
        service.createFlag(new FeatureFlagDefinition(
                "graphql.playground.enabled", "GraphQL Playground", null,
                FeatureFlagType.BOOLEAN, false,
                List.of(), List.of(), true, "platform", List.of("graphql"),
                null, null, false
        ));

        FeatureFlagContext ctx = new FeatureFlagContext(
                "tenant-1", null, null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagEvaluationResult result = service.evaluate(
                new FeatureFlagEvaluationRequest("graphql.playground.enabled", ctx, false));

        assertFalse(result.decision().enabled());
    }

    @Test
    void graphQLDepthLimitFlag() {
        FeatureFlagDefinition def = new FeatureFlagDefinition(
                "graphql.depth.limit.enabled", "GraphQL Depth Limit", null,
                FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "platform", List.of("graphql", "security"),
                null, null, false
        );
        service.createFlag(def);

        List<FeatureFlagDefinition> flags = service.listFlags();
        assertTrue(flags.stream()
                .anyMatch(f -> "graphql.depth.limit.enabled".equals(f.flagKey())));
    }

    @Test
    void graphQLComplexityCheckFlag() {
        service.createFlag(new FeatureFlagDefinition(
                "graphql.complexity.check.enabled", "GraphQL Complexity", null,
                FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "platform", List.of("graphql", "security"),
                null, null, false
        ));

        FeatureFlagEvaluationResult result = service.evaluate(
                new FeatureFlagEvaluationRequest("graphql.complexity.check.enabled", null, true));

        assertNotNull(result);
        assertNotNull(result.decision());
    }

    @Test
    void graphQLFlagWithTierRestriction() {
        service.createFlag(new FeatureFlagDefinition(
                "graphql.tier.flag", "GraphQL Tier", null,
                FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "platform", List.of("graphql"),
                null, null, false
        ));
        localProvider.saveRule("graphql.tier.flag", new FeatureFlagTargetingRule(
                "r-gql-tier", "graphql.tier.flag", 10, true,
                null, null, null, null, null, "enterprise",
                null, null, null, null, null, null
        ));

        FeatureFlagContext enterpriseCtx = new FeatureFlagContext(
                "tenant-1", null, null, List.of(), List.of(),
                "enterprise", null, null, null, null, Map.of());
        FeatureFlagEvaluationResult enterpriseResult = service.evaluate(
                new FeatureFlagEvaluationRequest("graphql.tier.flag", enterpriseCtx, false));
        assertTrue(enterpriseResult.decision().enabled());

        FeatureFlagContext freeCtx = new FeatureFlagContext(
                "tenant-1", null, null, List.of(), List.of(),
                "free", null, null, null, null, Map.of());
        FeatureFlagEvaluationResult freeResult = service.evaluate(
                new FeatureFlagEvaluationRequest("graphql.tier.flag", freeCtx, false));
        assertFalse(freeResult.decision().enabled());
    }

    @Test
    void graphQLFlagWithPercentageRollout() {
        service.createFlag(new FeatureFlagDefinition(
                "graphql.rollout.flag", "GraphQL Rollout", null,
                FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "platform", List.of("graphql"),
                null, null, false
        ));
        localProvider.saveRule("graphql.rollout.flag", new FeatureFlagTargetingRule(
                "r-gql-rollout", "graphql.rollout.flag", 10, true,
                null, null, null, null, null, null,
                100.0, null, null, null, null, null
        ));

        FeatureFlagContext ctx = new FeatureFlagContext(
                null, null, "user-1", List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagEvaluationResult result = service.evaluate(
                new FeatureFlagEvaluationRequest("graphql.rollout.flag", ctx, false));

        assertTrue(result.decision().enabled());
    }

    @Test
    void graphQLBatchEvaluation() {
        service.createFlag(new FeatureFlagDefinition(
                "graphql.batch.1", "GQL Batch 1", null,
                FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "platform", List.of(),
                null, null, false
        ));
        service.createFlag(new FeatureFlagDefinition(
                "graphql.batch.2", "GQL Batch 2", null,
                FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "platform", List.of(),
                null, null, false
        ));

        List<FeatureFlagEvaluationRequest> requests = List.of(
                new FeatureFlagEvaluationRequest("graphql.batch.1", null, true),
                new FeatureFlagEvaluationRequest("graphql.batch.2", null, false)
        );
        List<FeatureFlagEvaluationResult> results = service.evaluateBatch(requests);

        assertEquals(2, results.size());
    }

    @Test
    void graphQLFlagWithTimeBasedRule() {
        service.createFlag(new FeatureFlagDefinition(
                "graphql.time.flag", "GraphQL Time", null,
                FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "platform", List.of("graphql"),
                null, null, false
        ));
        localProvider.saveRule("graphql.time.flag", new FeatureFlagTargetingRule(
                "r-gql-time", "graphql.time.flag", 10, true,
                "tenant-1", null, null, null, null, null,
                null, null, null, null,
                Instant.now().minusSeconds(100),
                Instant.now().plusSeconds(100)
        ));

        FeatureFlagContext ctx = new FeatureFlagContext(
                "tenant-1", null, null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagEvaluationResult result = service.evaluate(
                new FeatureFlagEvaluationRequest("graphql.time.flag", ctx, false));

        assertTrue(result.decision().enabled());
    }
}
