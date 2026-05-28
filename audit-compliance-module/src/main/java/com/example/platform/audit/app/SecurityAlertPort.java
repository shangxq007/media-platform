package com.example.platform.audit.app;

/**
 * Port interface for publishing security alerts to external systems.
 *
 * <p>Implementations must:
 * <ul>
 *   <li>Never throw exceptions that block the business flow</li>
 *   <li>Never log or transmit payload or sensitive fields</li>
 *   <li>Handle network/IO errors gracefully</li>
 * </ul>
 */
public interface SecurityAlertPort {

    /**
     * Publish a security alert.
     *
     * @param alert the security alert to publish
     */
    void publish(SecurityAlert alert);
}
