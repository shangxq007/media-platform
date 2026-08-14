package com.example.platform.extension.domain;

/**
 * #16 (R4/C13): capability CONTRACT lifecycle axis.
 *
 * <p>Describes whether the capability contract itself is still supported by the
 * platform. This axis is INDEPENDENT of registration/runtime availability
 * (whether a specific implementation can currently be provided) and of plugin
 * lifecycle (install/package state). A retired contract with an installed
 * plugin whose registration is UNAVAILABLE is a legal state.
 */
public enum CapabilityContractLifecycle {
    /** Contract is supported by the platform; new consumers may depend on it. */
    ACTIVE,

    /** Contract is no longer supported; consumers must migrate away. */
    RETIRED
}
