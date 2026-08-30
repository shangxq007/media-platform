package com.example.platform.audit.app;

/** Test-only sink for focused alert-service tests. */
final class NoopSecurityAlertAdapter implements SecurityAlertPort {
    @Override public void publish(SecurityAlert alert) {}
}
