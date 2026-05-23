package com.example.platform.workflow.temporal;

/**
 * Resolves Temporal namespace as {@code media-platform-{env}} unless explicitly overridden.
 */
public final class TemporalNamespaceResolver {

    private TemporalNamespaceResolver() {}

    public static String resolve(
            AppTemporalProperties properties, String temporalNamespaceEnv, String platformEnv) {
        if (properties != null && properties.getNamespace() != null && !properties.getNamespace().isBlank()) {
            return properties.getNamespace().trim();
        }
        if (temporalNamespaceEnv != null && !temporalNamespaceEnv.isBlank()) {
            return temporalNamespaceEnv.trim();
        }
        String env = resolveEnvironment(properties, platformEnv);
        String prefix = properties != null && properties.getNamespacePrefix() != null
                ? properties.getNamespacePrefix()
                : "media-platform";
        return prefix + "-" + env;
    }

    public static String resolveEnvironment(AppTemporalProperties properties, String platformEnv) {
        if (properties != null && properties.getEnvironment() != null && !properties.getEnvironment().isBlank()) {
            return properties.getEnvironment().trim();
        }
        if (platformEnv != null && !platformEnv.isBlank()) {
            return platformEnv.trim();
        }
        return "dev";
    }
}
