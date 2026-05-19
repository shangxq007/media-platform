package com.example.platform.extension.domain;

public record RoutingRule(
        String id,
        String ruleName,
        String extensionCode,
        String sourceVersion,
        String targetVersion,
        String tenantId,
        String userId,
        String scene,
        int priority,
        int trafficPercent,
        boolean enabled
) {
    public RoutingRule {
        if (extensionCode == null || extensionCode.isBlank()) {
            throw new IllegalArgumentException("extensionCode must not be blank");
        }
        if (targetVersion == null || targetVersion.isBlank()) {
            throw new IllegalArgumentException("targetVersion must not be blank");
        }
        if (trafficPercent < 0 || trafficPercent > 100) {
            throw new IllegalArgumentException("trafficPercent must be between 0 and 100");
        }
    }

    public boolean matches(String tenantId, String userId, String scene) {
        if (this.tenantId != null && !this.tenantId.equals(tenantId)) return false;
        if (this.userId != null && !this.userId.equals(userId)) return false;
        if (this.scene != null && !this.scene.equals(scene)) return false;
        return enabled;
    }
}
