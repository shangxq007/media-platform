package com.example.platform.extension.domain;

/**
 * Runtime and trust requirement declaration (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>ADAPTS the existing {@link ExtensionTrustLevel} concept as the trust
 * representation. P1 supports only {@code TRUSTED_IN_PROCESS} in a local
 * process; the trust level must be {@code FULLY_TRUSTED} (trusted internal) —
 * any other trust level is rejected at registration (TRUST-REJECTED /
 * PLG-016).</p>
 *
 * <p>No code path may start loading new plugin binaries: arbitrary external
 * JAR, PF4J dynamic loading, tenant code upload, untrusted provider,
 * separate-process protocol, container sandbox, remote plugin protocol, hot
 * reload and marketplace installation are all prohibited.</p>
 *
 * @param runtime              TRUSTED_IN_PROCESS for P1
 * @param executionEnvironment LOCAL_PROCESS (evidence-derived: ToolRegistry/DefaultProcessToolRunner)
 * @param trustLevel           ExtensionTrustLevel.FULLY_TRUSTED required
 */
public record PluginRuntimeRequirement(
        RuntimeMode runtime,
        ExecutionEnvironment executionEnvironment,
        ExtensionTrustLevel trustLevel) {

    /** Runtime modes supported by P1. */
    public enum RuntimeMode {
        TRUSTED_IN_PROCESS
    }

    /** Execution environments supported by P1. */
    public enum ExecutionEnvironment {
        LOCAL_PROCESS
    }

    /** Frozen default: trusted in-process, local process, fully trusted. */
    public static PluginRuntimeRequirement trustedInProcess() {
        return new PluginRuntimeRequirement(
                RuntimeMode.TRUSTED_IN_PROCESS,
                ExecutionEnvironment.LOCAL_PROCESS,
                ExtensionTrustLevel.FULLY_TRUSTED);
    }

    public PluginRuntimeRequirement {
        if (runtime == null) {
            throw new NullPointerException("runtime must not be null");
        }
        if (executionEnvironment == null) {
            throw new NullPointerException("executionEnvironment must not be null");
        }
        if (trustLevel == null) {
            throw new NullPointerException("trustLevel must not be null");
        }
    }
}
