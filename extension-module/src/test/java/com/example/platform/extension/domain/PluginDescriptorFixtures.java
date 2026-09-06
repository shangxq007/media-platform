package com.example.platform.extension.domain;

/** Neutral descriptor values shared by extension-module tests. */
public final class PluginDescriptorFixtures {

    private PluginDescriptorFixtures() {}

    public static ResourceRequirement resourceRequirements() {
        return new ResourceRequirement(
                1, 256, 50, 0,
                64L * 1024 * 1024, 64L * 1024 * 1024, 60_000L,
                false, 4096, false, 60_000L);
    }

    public static PluginGuarantee guarantees() {
        return PluginGuarantee.noneDeclared();
    }
}
