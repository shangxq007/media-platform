package com.example.platform.policy.app;

import com.example.platform.policy.domain.*;
import com.example.platform.policy.featureflag.FeatureFlagAuditService;
import com.example.platform.policy.featureflag.domain.*;
import com.example.platform.policy.featureflag.FeatureFlagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PolicyEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(PolicyEvaluationService.class);
    private static final String FEATURE_FLAG_PREFIX = "featureFlag.";

    private final Map<String, PolicyRule> rules = new ConcurrentHashMap<>();
    private final FeatureFlagService featureFlagService;
    private final FeatureFlagAuditService auditService;

    public PolicyEvaluationService() {
        this(null, null);
    }

    public PolicyEvaluationService(FeatureFlagService featureFlagService,
                                    FeatureFlagAuditService auditService) {
        this.featureFlagService = featureFlagService;
        this.auditService = auditService;

        PolicyRule defaultDeny = new PolicyRule(
                "rule-default-deny", "Default Deny",
                PolicyEffect.DENY, "{}", 999, "ACTIVE");
        rules.put(defaultDeny.id(), defaultDeny);
    }

    public PolicyDecision evaluate(PolicyContext context) {
        List<PolicyRule> activeRules = rules.values().stream()
                .filter(r -> "ACTIVE".equals(r.status()))
                .sorted(Comparator.comparingInt(PolicyRule::priority))
                .toList();

        Map<String, Boolean> evaluatedFlags = new HashMap<>();

        for (PolicyRule rule : activeRules) {
            if (matchesRule(rule, context, evaluatedFlags)) {
                PolicyDecision decision = new PolicyDecision(
                        rule.effect(), "Matched rule: " + rule.name(), rule.id(),
                        Map.copyOf(evaluatedFlags));
                log.debug("Policy evaluation matched rule: {} with effect: {}", rule.name(), rule.effect());
                return decision;
            }
        }

        return new PolicyDecision(PolicyEffect.DENY, "No matching policy rule found", "none",
                Map.copyOf(evaluatedFlags));
    }

    public void addRule(PolicyRule rule) {
        rules.put(rule.id(), rule);
    }

    public void removeRule(String ruleId) {
        rules.remove(ruleId);
    }

    public List<PolicyRule> listRules() {
        return rules.values().stream()
                .sorted(Comparator.comparingInt(PolicyRule::priority))
                .toList();
    }

    private boolean matchesRule(PolicyRule rule, PolicyContext context, Map<String, Boolean> evaluatedFlags) {
        if (rule.conditions() == null || rule.conditions().isBlank() || rule.conditions().equals("{}")) {
            return true;
        }
        try {
            String conditions = rule.conditions();

            if (conditions.contains("\"tenantId\"")) {
                String expected = extractJsonValue(conditions, "tenantId");
                if (expected != null && expected.equals(context.tenantId())) {
                    return true;
                }
            }
            if (conditions.contains("\"workspaceId\"")) {
                String expected = extractJsonValue(conditions, "workspaceId");
                if (expected != null && expected.equals(context.workspaceId())) {
                    return true;
                }
            }
            if (conditions.contains("\"userId\"")) {
                String expected = extractJsonValue(conditions, "userId");
                if (expected != null && expected.equals(context.userId())) {
                    return true;
                }
            }
            if (conditions.contains("\"role\"")) {
                String expected = extractJsonValue(conditions, "role");
                if (expected != null && expected.equals(context.role())) {
                    return true;
                }
            }
            if (conditions.contains("\"featureFlag\"") && featureFlagService != null) {
                return evaluateFeatureFlagConditions(conditions, context, evaluatedFlags);
            }
            return false;
        } catch (Exception e) {
            log.warn("Failed to evaluate rule {} conditions: {}", rule.id(), e.getMessage());
            return false;
        }
    }

    private boolean evaluateFeatureFlagConditions(String conditions, PolicyContext context,
                                                   Map<String, Boolean> evaluatedFlags) {
        List<PolicyFeatureFlagCondition> ffConditions = extractFeatureFlagConditions(conditions);
        if (ffConditions.isEmpty()) {
            return false;
        }

        FeatureFlagContext ffContext = new FeatureFlagContext(
                context.tenantId(), context.workspaceId(), context.userId(),
                context.role() != null ? List.of(context.role()) : List.of(),
                List.of(), null, context.requestSource(),
                null, null, null, context.attributes()
        );

        for (PolicyFeatureFlagCondition ffCond : ffConditions) {
            String flagKey = ffCond.flagKey();
            if (flagKey.startsWith(FEATURE_FLAG_PREFIX)) {
                flagKey = flagKey.substring(FEATURE_FLAG_PREFIX.length());
            }

            FeatureFlagEvaluationRequest evalRequest = new FeatureFlagEvaluationRequest(
                    flagKey, ffContext, false);
            FeatureFlagEvaluationResult result = featureFlagService.evaluate(evalRequest);
            FeatureFlagDecision decision = result.decision();

            evaluatedFlags.put(flagKey, decision.enabled());

            if (auditService != null) {
                auditService.auditEvaluated(decision, context.userId());
            }

            boolean conditionMet = evaluateFlagCondition(ffCond, decision.enabled());
            if (!conditionMet) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateFlagCondition(PolicyFeatureFlagCondition condition, boolean flagEnabled) {
        String operator = condition.operator() != null ? condition.operator() : "eq";
        Object expected = condition.expectedValue();

        boolean expectedBool;
        if (expected instanceof Boolean) {
            expectedBool = (Boolean) expected;
        } else if (expected instanceof String) {
            expectedBool = Boolean.parseBoolean((String) expected);
        } else {
            expectedBool = flagEnabled;
        }

        return switch (operator) {
            case "eq", "equals" -> flagEnabled == expectedBool;
            case "ne", "notEquals" -> flagEnabled != expectedBool;
            default -> flagEnabled == expectedBool;
        };
    }

    private List<PolicyFeatureFlagCondition> extractFeatureFlagConditions(String conditions) {
        List<PolicyFeatureFlagCondition> result = new ArrayList<>();
        String searchKey = "\"featureFlag\"";
        int searchFrom = 0;
        while (true) {
            int keyIndex = conditions.indexOf(searchKey, searchFrom);
            if (keyIndex < 0) break;

            int objStart = conditions.indexOf("{", keyIndex + searchKey.length());
            if (objStart < 0) break;
            int objEnd = findMatchingBrace(conditions, objStart);
            if (objEnd < 0) break;

            String objContent = conditions.substring(objStart + 1, objEnd);
            String flagKey = extractJsonValue("{" + objContent + "}", "flagKey");
            String operator = extractJsonValue("{" + objContent + "}", "operator");
            String expectedStr = extractJsonValue("{" + objContent + "}", "expectedValue");

            if (flagKey != null) {
                Object expectedValue = expectedStr;
                if (expectedStr != null) {
                    if ("true".equalsIgnoreCase(expectedStr) || "false".equalsIgnoreCase(expectedStr)) {
                        expectedValue = Boolean.parseBoolean(expectedStr);
                    }
                } else {
                    String rawValue = extractRawValue("{" + objContent + "}", "expectedValue");
                    if (rawValue != null) {
                        if ("true".equalsIgnoreCase(rawValue) || "false".equalsIgnoreCase(rawValue)) {
                            expectedValue = Boolean.parseBoolean(rawValue);
                        } else {
                            expectedValue = rawValue;
                        }
                    }
                }
                result.add(new PolicyFeatureFlagCondition(flagKey, operator, expectedValue));
            }
            searchFrom = objEnd + 1;
        }
        return result;
    }

    private int findMatchingBrace(String text, int openBracePos) {
        int depth = 1;
        for (int i = openBracePos + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex < 0) return null;
        int colonIndex = json.indexOf(":", keyIndex + searchKey.length());
        if (colonIndex < 0) return null;
        int startQuote = json.indexOf("\"", colonIndex + 1);
        if (startQuote < 0) return null;
        int endQuote = json.indexOf("\"", startQuote + 1);
        if (endQuote < 0) return null;
        return json.substring(startQuote + 1, endQuote);
    }

    private String extractRawValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex < 0) return null;
        int colonIndex = json.indexOf(":", keyIndex + searchKey.length());
        if (colonIndex < 0) return null;
        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= json.length()) return null;
        int valueEnd = valueStart;
        while (valueEnd < json.length() && json.charAt(valueEnd) != ',' && json.charAt(valueEnd) != '}') {
            valueEnd++;
        }
        return json.substring(valueStart, valueEnd).trim();
    }
}
