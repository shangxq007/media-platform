package com.example.platform.render.domain.governance;

/**
 * Access decision — platform-wide authorization outcome.
 */
public record AccessDecision(
        Decision decision,
        String reason,
        String overageDetail) {

    public enum Decision {
        ALLOW,
        DENY,
        ALLOW_WITH_OVERAGE,
        REQUIRE_APPROVAL,
        QUEUE,
        DEGRADE
    }

    public static AccessDecision allow() { return new AccessDecision(Decision.ALLOW, null, null); }
    public static AccessDecision deny(String reason) { return new AccessDecision(Decision.DENY, reason, null); }
    public static AccessDecision overage(String detail) { return new AccessDecision(Decision.ALLOW_WITH_OVERAGE, null, detail); }
}
