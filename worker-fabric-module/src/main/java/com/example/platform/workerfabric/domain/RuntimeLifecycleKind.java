package com.example.platform.workerfabric.domain;

/** Bounded lifecycle kinds supported by the worker-fabric foundation. */
public enum RuntimeLifecycleKind {
    EPHEMERAL_TASK,
    RESIDENT_RUNTIME,
    REMOTE_RUNTIME
}
