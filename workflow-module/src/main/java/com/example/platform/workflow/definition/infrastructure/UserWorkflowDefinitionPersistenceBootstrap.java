package com.example.platform.workflow.definition.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Persistence bootstrap following the platform convention
 * (BillingPersistenceBootstrap / EntitlementPersistenceBootstrap): a
 * log-only readiness marker. All W2 persistence flows through the JDBC
 * repository; no hydration is needed.
 */
@Component
public class UserWorkflowDefinitionPersistenceBootstrap {

    private static final Logger log = LoggerFactory.getLogger(UserWorkflowDefinitionPersistenceBootstrap.class);

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("UserWorkflowDefinitionPersistenceBootstrap: W2 workflow-definition persistence is JDBC-backed "
                + "(user_workflow_definition* tables) — no hydration needed");
    }
}
