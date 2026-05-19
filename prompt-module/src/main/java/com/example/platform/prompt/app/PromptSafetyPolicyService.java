package com.example.platform.prompt.app;

import com.example.platform.prompt.domain.PromptRiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Analyzes prompt content for safety risks and enforces safety policies.
 */
@Service
public class PromptSafetyPolicyService {

    private static final Logger log = LoggerFactory.getLogger(PromptSafetyPolicyService.class);

    private static final List<SecretPattern> SECRET_PATTERNS = List.of(
            new SecretPattern("API Key", java.util.regex.Pattern.compile("(?i)(api[_-]?key|apikey)\\s*[:=]\\s*['\"]?([a-zA-Z0-9_\\-]{16,})"), true),
            new SecretPattern("Password", java.util.regex.Pattern.compile("(?i)(password|passwd|pwd)\\s*[:=]\\s*['\"]?([^\\s'\"]{8,})"), true),
            new SecretPattern("Private Key", java.util.regex.Pattern.compile("(?i)(private[_-]?key|secret[_-]?key)\\s*[:=]\\s*['\"]?([^\\s'\"]{8,})"), true),
            new SecretPattern("AWS Key", java.util.regex.Pattern.compile("(?i)(AKIA[0-9A-Z]{16})"), true),
            new SecretPattern("GitHub Token", java.util.regex.Pattern.compile("(?i)(ghp_[a-zA-Z0-9]{36})"), true),
            new SecretPattern("Generic Secret", java.util.regex.Pattern.compile("(?i)(secret|token)\\s*[:=]\\s*['\"]?([a-zA-Z0-9_\\-]{20,})"), false)
    );

    private static final List<DestructivePattern> DESTRUCTIVE_PATTERNS = List.of(
            new DestructivePattern("rm -rf", java.util.regex.Pattern.compile("(?i)rm\\s+-rf\\s+/"), PromptRiskLevel.CRITICAL),
            new DestructivePattern("terraform destroy", java.util.regex.Pattern.compile("(?i)terraform\\s+destroy"), PromptRiskLevel.HIGH),
            new DestructivePattern("tofu destroy", java.util.regex.Pattern.compile("(?i)tofu\\s+destroy"), PromptRiskLevel.HIGH),
            new DestructivePattern("chmod wide", java.util.regex.Pattern.compile("(?i)chmod\\s+(777|666)\\s+/"), PromptRiskLevel.HIGH),
            new DestructivePattern("chown recursive", java.util.regex.Pattern.compile("(?i)chown\\s+-R\\s+root"), PromptRiskLevel.MEDIUM),
            new DestructivePattern("production apply", java.util.regex.Pattern.compile("(?i)(production|prod)\\s*\\n.*(terraform|tofu)\\s+apply"), PromptRiskLevel.HIGH),
            new DestructivePattern("delete tests", java.util.regex.Pattern.compile("(?i)delete.*test|remove.*test|skip.*test"), PromptRiskLevel.MEDIUM),
            new DestructivePattern("disable security", java.util.regex.Pattern.compile("(?i)disable.*security|skip.*check|bypass.*validation"), PromptRiskLevel.HIGH),
            new DestructivePattern("upload private key", java.util.regex.Pattern.compile("(?i)upload.*private[_-]?key|send.*secret"), PromptRiskLevel.CRITICAL)
    );

    /**
     * Scan content for secrets.
     */
    public SecretScanResult scanForSecrets(String content) {
        if (content == null) return new SecretScanResult(false, List.of());

        List<String> findings = new ArrayList<>();
        boolean blocked = false;

        for (SecretPattern pattern : SECRET_PATTERNS) {
            java.util.regex.Matcher m = pattern.regex().matcher(content);
            while (m.find()) {
                String match = m.group();
                findings.add("Potential " + pattern.name + " detected: " + redact(match, "PARTIAL"));
                if (pattern.block) blocked = true;
            }
        }

        return new SecretScanResult(blocked, findings);
    }

    /**
     * Classify command risk.
     */
    public CommandRiskResult classifyCommandRisk(String content) {
        if (content == null) return new CommandRiskResult(PromptRiskLevel.LOW, List.of());

        PromptRiskLevel maxLevel = PromptRiskLevel.LOW;
        List<String> findings = new ArrayList<>();

        for (DestructivePattern pattern : DESTRUCTIVE_PATTERNS) {
            if (pattern.regex().matcher(content).find()) {
                findings.add("Destructive command detected: " + pattern.name);
                if (pattern.level.ordinal() > maxLevel.ordinal()) {
                    maxLevel = pattern.level;
                }
            }
        }

        return new CommandRiskResult(maxLevel, findings);
    }

    /**
     * Evaluate safety policy for a prompt.
     */
    public SafetyPolicyResult evaluate(String content, String tenantId, String userId,
            String environment, String category) {
        SecretScanResult secretResult = scanForSecrets(content);
        CommandRiskResult commandResult = classifyCommandRisk(content);

        // Determine action
        SafetyAction action;
        if (secretResult.blocked() || commandResult.level() == PromptRiskLevel.CRITICAL) {
            action = SafetyAction.BLOCK;
        } else if (commandResult.level() == PromptRiskLevel.HIGH) {
            action = SafetyAction.REQUIRE_REVIEW;
        } else if (!secretResult.findings().isEmpty() || commandResult.level() == PromptRiskLevel.MEDIUM) {
            action = SafetyAction.WARN;
        } else {
            action = SafetyAction.ALLOW;
        }

        StringBuilder explanation = new StringBuilder();
        if (action == SafetyAction.BLOCK) {
            explanation.append("Prompt blocked: ");
            if (secretResult.blocked()) explanation.append("contains secrets. ");
            if (commandResult.level() == PromptRiskLevel.CRITICAL) explanation.append("contains critical destructive commands.");
        } else if (action == SafetyAction.REQUIRE_REVIEW) {
            explanation.append("Prompt requires manual review: ");
            explanation.append(String.join("; ", commandResult.findings()));
        } else if (action == SafetyAction.WARN) {
            explanation.append("Warnings: ");
            explanation.append(String.join("; ", secretResult.findings()));
        } else {
            explanation.append("Prompt passed safety checks.");
        }

        log.info("PromptSafetyPolicyService: {} for tenant={}", action, tenantId);
        return new SafetyPolicyResult(action, explanation.toString(),
                secretResult.findings(), commandResult.findings());
    }

    /**
     * Get redaction for a value.
     */
    public String redact(String value, String redactionPolicy) {
        if (value == null) return null;
        return switch (redactionPolicy != null ? redactionPolicy.toUpperCase() : "FULL") {
            case "FULL" -> "[REDACTED]";
            case "PARTIAL" -> value.length() > 4
                    ? value.substring(0, 2) + "***" + value.substring(value.length() - 2)
                    : "***";
            case "HASH" -> "hash:" + Integer.toHexString(value.hashCode());
            case "MASK_LAST_FOUR" -> value.length() > 4
                    ? "****" + value.substring(value.length() - 4)
                    : "****";
            default -> "[REDACTED]";
        };
    }

    // -------------------------------------------------------------------------
    // Inner records
    // -------------------------------------------------------------------------

    public record SecretScanResult(boolean blocked, List<String> findings) {}
    public record CommandRiskResult(PromptRiskLevel level, List<String> findings) {}
    public record SafetyPolicyResult(SafetyAction action, String explanation,
            List<String> secretFindings, List<String> commandFindings) {}

    public enum SafetyAction {
        ALLOW, WARN, REQUIRE_REVIEW, BLOCK
    }

    private record SecretPattern(String name, java.util.regex.Pattern regex, boolean block) {}
    private record DestructivePattern(String name, java.util.regex.Pattern regex, PromptRiskLevel level) {}
}
