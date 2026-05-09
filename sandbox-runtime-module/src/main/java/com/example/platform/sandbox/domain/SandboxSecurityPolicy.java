package com.example.platform.sandbox.domain;

/**
 * SPI interface for sandbox security policy enforcement.
 *
 * <p>Implementations should validate commands, restrict filesystem access,
 * and enforce resource limits before execution.</p>
 */
public interface SandboxSecurityPolicy {

    /**
     * Checks whether a command is allowed to execute under this policy.
     *
     * @param command the command or operation to validate
     * @return true if the command is permitted, false otherwise
     */
    boolean isAllowed(String command);
}
