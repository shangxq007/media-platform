package com.example.platform.extension.domain;

/**
 * #16 (R4/C13): capability registration / runtime availability axis.
 *
 * <p>Describes whether a capability implementation registration can currently
 * actually provide the capability. This axis is INDEPENDENT of the capability
 * contract lifecycle (ACTIVE/RETIRED) and of plugin lifecycle (INSTALLED).
 * A plugin can be installed and healthy while its capability registration is
 * UNAVAILABLE (e.g. GPU missing, external binary absent, license absent).
 * Manifest declaration never auto-promotes a registration to AVAILABLE.
 */
public enum RegistrationAvailability {
    /** Declared in a plugin manifest/descriptor; not yet validated. */
    DISCOVERED,

    /** Declaration passed structural/namespace/contract validation. */
    VALIDATED,

    /** Registered and runtime conditions currently allow actual use. */
    AVAILABLE,

    /** Registered but degraded (partial functionality). */
    DEGRADED,

    /** Registered but currently cannot provide the capability. */
    UNAVAILABLE
}
