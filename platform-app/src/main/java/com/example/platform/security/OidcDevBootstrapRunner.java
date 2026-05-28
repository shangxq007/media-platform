package com.example.platform.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Ensures default tenant + legacy {@code user-1} for local OIDC dev/test bootstrap.
 *
 * <p>DEV-ONLY: This runner is strictly limited to dev/local/test profiles.
 * It will NOT run in production OIDC environments.
 */
@Component
@Profile({"dev", "local", "test"})
@ConditionalOnProperty(name = "app.security.oidc-dev-bootstrap.enabled", havingValue = "true", matchIfMissing = false)
public class OidcDevBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OidcDevBootstrapRunner.class);

    private final OAuth2SecurityProperties oauth2Properties;
    private final DevWorkspaceBootstrapService devWorkspaceBootstrapService;

    public OidcDevBootstrapRunner(
            OAuth2SecurityProperties oauth2Properties,
            DevWorkspaceBootstrapService devWorkspaceBootstrapService) {
        this.oauth2Properties = oauth2Properties;
        this.devWorkspaceBootstrapService = devWorkspaceBootstrapService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String tenantId = oauth2Properties.defaultTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = DevWorkspaceBootstrapService.LEGACY_DEV_TENANT_ID;
        }
        devWorkspaceBootstrapService.ensureDefaultWorkspace(tenantId, "PRO");
        devWorkspaceBootstrapService.ensureLegacyDevUser(tenantId);
        log.info("OIDC dev bootstrap complete for tenant {}", tenantId);
    }
}
