package com.example.platform.billing.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(JdbcTemplate.class)
public class BillingPersistenceBootstrap {

    private static final Logger log = LoggerFactory.getLogger(BillingPersistenceBootstrap.class);

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("BillingPersistenceBootstrap: all billing services use JDBC as primary storage — no hydration needed");
    }
}
