package com.example.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OIDC Resource Server settings (Authentik or any OIDC issuer).
 */
@ConfigurationProperties(prefix = "app.security.oauth2")
public record OAuth2SecurityProperties(
        boolean enabled,
        String issuerUri,
        String audience,
        String tenantClaim,
        String rolesClaim,
        String userIdClaim,
        boolean legacyHmacJwtEnabled,
        boolean jitProvisioningEnabled,
        boolean trustJwtTenantOnly,
        String defaultTenantId) {

    public OAuth2SecurityProperties {
        if (tenantClaim == null || tenantClaim.isBlank()) {
            tenantClaim = "tenantId";
        }
        if (rolesClaim == null || rolesClaim.isBlank()) {
            rolesClaim = "roles";
        }
    }

    public boolean hasIssuer() {
        return issuerUri != null && !issuerUri.isBlank();
    }
}
