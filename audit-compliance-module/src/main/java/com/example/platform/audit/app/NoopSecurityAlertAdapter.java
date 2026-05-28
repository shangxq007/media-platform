package com.example.platform.audit.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-op adapter that silently discards all security alerts.
 *
 * <p>Used when security alert publishing is disabled or for testing.
 * Does not log or transmit any data.
 */
public class NoopSecurityAlertAdapter implements SecurityAlertPort {

    private static final Logger log = LoggerFactory.getLogger(NoopSecurityAlertAdapter.class);

    @Override
    public void publish(SecurityAlert alert) {
        // Intentionally empty — alerts are discarded
        log.debug("Security alert discarded (noop): rule={} actorId={}", alert.rule(), alert.actorId());
    }
}
