package com.example.platform.sandbox;

/** Independently advertised enforcement mechanics; capability is not execution authorization. */
@org.springframework.modulith.NamedInterface("API")
public enum SandboxCapability {
    PROCESS_TREE_CONTAINMENT,
    BEST_EFFORT_DESCENDANT_CLEANUP,
    WALL_CLOCK_TIMEOUT,
    FILESYSTEM_PATH_VALIDATION,
    FILESYSTEM_ACCESS_ISOLATION,
    NETWORK_NONE,
    NETWORK_ENDPOINT_ALLOWLIST,
    ENVIRONMENT_CLEARING,
    SECRET_INJECTION,
    BOUNDED_CAPTURE,
    CPU_COUNT_LIMIT,
    MEMORY_LIMIT,
    PROCESS_COUNT_LIMIT,
    OPEN_FILE_LIMIT,
    TEMPORARY_STORAGE_LIMIT,
    OUTPUT_STORAGE_LIMIT,
    UNPRIVILEGED_EXECUTION,
    HOST_EXPOSURE_DENIAL,
    DEVICE_NONE,
    DEVICE_GRANTS
}
