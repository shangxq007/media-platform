package com.example.platform.workerfabric.reuse;

/** Bounded outcome produced by the real worker-local materialization path. */
public enum MaterializationDisposition {
    LOCAL_CACHE_HIT,
    STORAGE_MATERIALIZED,
    CORRUPTION_RECOVERED,
    FAILURE
}
