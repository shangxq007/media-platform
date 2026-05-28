package com.example.platform;

import com.example.platform.identity.app.BuiltinDataInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Initializes built-in RBAC data (roles, permissions, role-permission links)
 * after the application context is fully started.
 *
 * <p>This runner is ordered to run early (before other runners that may depend on
 * RBAC data). It is controlled by the {@code identity.builtin-data.enabled} property
 * (default: true).
 *
 * <p>The initialization is idempotent — roles and permissions are only created if
 * they do not already exist. Running this multiple times will not duplicate data
 * or overwrite user customizations.
 */
@Component
@ConditionalOnProperty(prefix = "identity.builtin-data", name = "enabled", havingValue = "true", matchIfMissing = true)
@Order(100)
public class BuiltinDataBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BuiltinDataBootstrapRunner.class);

    private final BuiltinDataInitializer builtinDataInitializer;

    public BuiltinDataBootstrapRunner(BuiltinDataInitializer builtinDataInitializer) {
        this.builtinDataInitializer = builtinDataInitializer;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting built-in RBAC data initialization");
        try {
            builtinDataInitializer.init();
            log.info("Built-in RBAC data initialization completed successfully");
        } catch (Exception e) {
            // Fail-fast: RBAC data is required for the system to function correctly.
            // If initialization fails, the application should not start with incomplete permissions.
            log.error("Built-in RBAC data initialization failed — application will not start", e);
            throw new IllegalStateException("Built-in RBAC data initialization failed", e);
        }
    }
}
