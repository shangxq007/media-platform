package com.example.platform.prompt.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptSafetyPolicyServiceTest {

    private PromptSafetyPolicyService service;

    @BeforeEach
    void setUp() {
        service = new PromptSafetyPolicyService();
    }

    @Test
    void shouldDetectApiKey() {
        PromptSafetyPolicyService.SecretScanResult result =
                service.scanForSecrets("Here is my API key: AKIAIOSFODNN7EXAMPLE");
        assertTrue(result.findings().stream().anyMatch(f -> f.contains("AWS")));
    }

    @Test
    void shouldDetectPassword() {
        PromptSafetyPolicyService.SecretScanResult result =
                service.scanForSecrets("password: supersecret123");
        assertTrue(result.findings().stream().anyMatch(f -> f.contains("Password")));
    }

    @Test
    void shouldBlockRmRrf() {
        PromptSafetyPolicyService.CommandRiskResult result =
                service.classifyCommandRisk("Please run: rm -rf /");
        assertEquals(com.example.platform.prompt.domain.PromptRiskLevel.CRITICAL, result.level());
    }

    @Test
    void shouldRequireReviewForTerraformDestroy() {
        PromptSafetyPolicyService.CommandRiskResult result =
                service.classifyCommandRisk("Run terraform destroy to clean up");
        assertEquals(com.example.platform.prompt.domain.PromptRiskLevel.HIGH, result.level());
    }

    @Test
    void shouldAllowSafeContent() {
        PromptSafetyPolicyService.SafetyPolicyResult result =
                service.evaluate("Create a function that adds two numbers", "t1", "u1", "dev", "code");
        assertEquals(PromptSafetyPolicyService.SafetyAction.ALLOW, result.action());
    }

    @Test
    void shouldBlockSecretsInContent() {
        PromptSafetyPolicyService.SafetyPolicyResult result =
                service.evaluate("password: supersecret12345678", "t1", "u1", "dev", "test");
        assertTrue(result.action() == PromptSafetyPolicyService.SafetyAction.BLOCK
                || result.action() == PromptSafetyPolicyService.SafetyAction.REQUIRE_REVIEW
                || result.action() == PromptSafetyPolicyService.SafetyAction.WARN);
    }

    @Test
    void shouldWarnAboutDestructiveCommands() {
        PromptSafetyPolicyService.SafetyPolicyResult result =
                service.evaluate("Run chmod 777 /etc", "t1", "u1", "dev", "ops");
        assertTrue(result.action() == PromptSafetyPolicyService.SafetyAction.WARN
                || result.action() == PromptSafetyPolicyService.SafetyAction.REQUIRE_REVIEW
                || result.action() == PromptSafetyPolicyService.SafetyAction.BLOCK);
    }

    @Test
    void shouldRedactFull() {
        String redacted = service.redact("secret-value-123", "FULL");
        assertEquals("[REDACTED]", redacted);
    }

    @Test
    void shouldRedactPartial() {
        String redacted = service.redact("secret-value-123", "PARTIAL");
        assertTrue(redacted.contains("***"));
        assertFalse(redacted.contains("secret-value"));
    }

    @Test
    void shouldRedactHash() {
        String redacted = service.redact("my-secret", "HASH");
        assertTrue(redacted.startsWith("hash:"));
    }

    @Test
    void shouldDetectProductionAccess() {
        PromptSafetyPolicyService.CommandRiskResult result =
                service.classifyCommandRisk("Deploy to production\nterraform apply");
        assertTrue(result.level().ordinal() >=
                com.example.platform.prompt.domain.PromptRiskLevel.MEDIUM.ordinal());
    }

    @Test
    void shouldDetectDisableSecurity() {
        PromptSafetyPolicyService.CommandRiskResult result =
                service.classifyCommandRisk("disable security checks and skip validation");
        assertTrue(result.level().ordinal() >=
                com.example.platform.prompt.domain.PromptRiskLevel.MEDIUM.ordinal());
    }
}
