package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.FeatureFlagDefinition;
import com.example.platform.policy.featureflag.domain.FeatureFlagDecision;
import com.example.platform.policy.featureflag.domain.FeatureFlagTargetingRule;
import com.example.platform.shared.Ids;
import com.example.platform.shared.audit.AuditPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FeatureFlagAuditService {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagAuditService.class);
    private static final int MAX_IN_MEMORY_EVENTS = 10000;

    private final AuditPort auditPort;
    private final List<FeatureFlagAuditEvent> recentEvents = new ArrayList<>();
    private final ConcurrentHashMap<String, List<FeatureFlagAuditEvent>> eventsByFlag = new ConcurrentHashMap<>();

    public FeatureFlagAuditService(AuditPort auditPort) {
        this.auditPort = auditPort;
    }

    public void auditFlagCreated(FeatureFlagDefinition definition, String actor) {
        Map<String, Object> details = Map.of(
                "flagKey", definition.flagKey(),
                "name", definition.name(),
                "flagType", definition.flagType().name(),
                "enabled", definition.enabled()
        );
        recordEvent("FLAG_CREATED", definition.flagKey(), actor, details);
    }

    public void auditFlagUpdated(String flagKey, FeatureFlagDefinition before, FeatureFlagDefinition after, String actor) {
        Map<String, Object> details = Map.of(
                "flagKey", flagKey,
                "nameChanged", !java.util.Objects.equals(before.name(), after.name()),
                "enabledChanged", before.enabled() != after.enabled(),
                "typeChanged", before.flagType() != after.flagType()
        );
        recordEvent("FLAG_UPDATED", flagKey, actor, details);
    }

    public void auditFlagEnabled(String flagKey, String actor) {
        recordEvent("FLAG_ENABLED", flagKey, actor, Map.of("flagKey", flagKey));
    }

    public void auditFlagDisabled(String flagKey, String actor) {
        recordEvent("FLAG_DISABLED", flagKey, actor, Map.of("flagKey", flagKey));
    }

    public void auditFlagArchived(String flagKey, String actor) {
        recordEvent("FLAG_ARCHIVED", flagKey, actor, Map.of("flagKey", flagKey));
    }

    public void auditRuleCreated(String flagKey, FeatureFlagTargetingRule rule, String actor) {
        Map<String, Object> details = Map.of(
                "flagKey", flagKey,
                "ruleId", rule.ruleId(),
                "priority", rule.priority(),
                "enabled", rule.enabled()
        );
        recordEvent("RULE_CREATED", flagKey, actor, details);
    }

    public void auditRuleUpdated(String flagKey, FeatureFlagTargetingRule rule, String actor) {
        Map<String, Object> details = Map.of(
                "flagKey", flagKey,
                "ruleId", rule.ruleId(),
                "priority", rule.priority()
        );
        recordEvent("RULE_UPDATED", flagKey, actor, details);
    }

    public void auditRuleDeleted(String flagKey, String ruleId, String actor) {
        Map<String, Object> details = Map.of("flagKey", flagKey, "ruleId", ruleId);
        recordEvent("RULE_DELETED", flagKey, actor, details);
    }

    public void auditEvaluated(FeatureFlagDecision decision, String actor) {
        Map<String, Object> details = Map.of(
                "flagKey", decision.flagKey(),
                "enabled", decision.enabled(),
                "providerType", decision.providerType().name(),
                "reasonCode", decision.reasonCode()
        );
        recordEvent("FLAG_EVALUATED", decision.flagKey(), actor, details);
    }

    public void auditEvaluationFailed(String flagKey, String errorCode, String errorMessage, String actor) {
        Map<String, Object> details = Map.of(
                "flagKey", flagKey,
                "errorCode", errorCode,
                "errorMessage", errorMessage
        );
        recordEvent("FLAG_EVALUATION_FAILED", flagKey, actor, details);
    }

    public void auditRolloutChanged(String flagKey, Double oldPercentage, Double newPercentage, String actor) {
        Map<String, Object> details = Map.of(
                "flagKey", flagKey,
                "oldPercentage", oldPercentage != null ? oldPercentage : 0.0,
                "newPercentage", newPercentage != null ? newPercentage : 0.0
        );
        recordEvent("ROLLOUT_CHANGED", flagKey, actor, details);
    }

    public void auditVariantChanged(String flagKey, String oldVariant, String newVariant, String actor) {
        Map<String, Object> details = Map.of(
                "flagKey", flagKey,
                "oldVariant", oldVariant != null ? oldVariant : "none",
                "newVariant", newVariant != null ? newVariant : "none"
        );
        recordEvent("VARIANT_CHANGED", flagKey, actor, details);
    }

    public void auditPolicyEvaluatedWithFeatureFlag(String flagKey, boolean flagEnabled,
                                                      String actor, String tenantId,
                                                      String workspaceId, String userId,
                                                      String matchedRule, String variant,
                                                      String reason, String traceId,
                                                      String requestSource) {
        Map<String, Object> details = buildRichDetails(flagKey, actor, tenantId, workspaceId,
                userId, null, null, matchedRule, variant, reason, traceId, requestSource);
        details.put("flagEnabled", flagEnabled);
        recordEvent("POLICY_EVALUATED_WITH_FEATURE_FLAG", flagKey, actor, details);
    }

    public void auditAccessDeniedByFeatureFlag(String flagKey, String actor, String tenantId,
                                                String workspaceId, String userId,
                                                String matchedRule, String variant,
                                                String reason, String traceId,
                                                String requestSource) {
        Map<String, Object> details = buildRichDetails(flagKey, actor, tenantId, workspaceId,
                userId, null, null, matchedRule, variant, reason, traceId, requestSource);
        recordEvent("ACCESS_DENIED_BY_FEATURE_FLAG", flagKey, actor, details);
    }

    public void auditNavigationDisabledByFeatureFlag(String flagKey, String actor, String tenantId,
                                                       String workspaceId, String userId,
                                                       String matchedRule, String variant,
                                                       String reason, String traceId,
                                                       String requestSource) {
        Map<String, Object> details = buildRichDetails(flagKey, actor, tenantId, workspaceId,
                userId, null, null, matchedRule, variant, reason, traceId, requestSource);
        recordEvent("NAVIGATION_DISABLED_BY_FEATURE_FLAG", flagKey, actor, details);
    }

    private Map<String, Object> buildRichDetails(String flagKey, String actorId, String tenantId,
                                                   String workspaceId, String userId,
                                                   Object before, Object after,
                                                   String matchedRule, String variant,
                                                   String reason, String traceId,
                                                   String requestSource) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("flagKey", flagKey);
        details.put("actorId", actorId);
        details.put("tenantId", tenantId);
        details.put("workspaceId", workspaceId);
        details.put("userId", userId);
        if (before != null) details.put("before", before);
        if (after != null) details.put("after", after);
        if (matchedRule != null) details.put("matchedRule", matchedRule);
        if (variant != null) details.put("variant", variant);
        if (reason != null) details.put("reason", reason);
        if (traceId != null) details.put("traceId", traceId);
        if (requestSource != null) details.put("requestSource", requestSource);
        return details;
    }

    public List<FeatureFlagAuditEvent> getRecentEvents(int limit) {
        synchronized (recentEvents) {
            int fromIndex = Math.max(0, recentEvents.size() - limit);
            return List.copyOf(recentEvents.subList(fromIndex, recentEvents.size()));
        }
    }

    public List<FeatureFlagAuditEvent> getEventsByFlag(String flagKey) {
        return List.copyOf(eventsByFlag.getOrDefault(flagKey, List.of()));
    }

    private void recordEvent(String eventType, String flagKey, String actor, Map<String, Object> details) {
        String id = Ids.newId("ffaud");
        FeatureFlagAuditEvent event = new FeatureFlagAuditEvent(
                id, eventType, flagKey, actor, details, OffsetDateTime.now()
        );
        synchronized (recentEvents) {
            recentEvents.add(event);
            if (recentEvents.size() > MAX_IN_MEMORY_EVENTS) {
                recentEvents.remove(0);
            }
        }
        eventsByFlag.computeIfAbsent(flagKey, k -> new ArrayList<>()).add(event);
        if (auditPort != null) {
            auditPort.record(actor != null ? actor : "system", eventType, "FEATURE_FLAG",
                    "feature_flag", flagKey, details);
        }
        log.debug("FeatureFlagAudit event: {} {} by {}", eventType, flagKey, actor);
    }

    public record FeatureFlagAuditEvent(
            String id,
            String eventType,
            String flagKey,
            String actor,
            Map<String, Object> details,
            OffsetDateTime occurredAt
    ) {}
}
