package com.example.platform.workerfabric.domain;

/**
 * Canonical cross-domain execution-mechanics authority.
 *
 * <p>The placement mapping is frozen here rather than supplied by callers. Backend selection is
 * downstream of provider-bound executable semantics and therefore cannot affect an executable
 * task or ETG digest.
 */
public enum ExecutionBackend {
    NATIVE_PULL_WORKER(PlacementAuthorityScope.PLATFORM_MANAGED),
    OPEN_CUE_FARM(PlacementAuthorityScope.BACKEND_DELEGATED),
    REMOTE_PROVIDER(PlacementAuthorityScope.REMOTE_PROVIDER_MANAGED);

    private final PlacementAuthorityScope placementAuthorityScope;

    ExecutionBackend(PlacementAuthorityScope placementAuthorityScope) {
        this.placementAuthorityScope = placementAuthorityScope;
    }

    public PlacementAuthorityScope placementAuthorityScope() {
        return placementAuthorityScope;
    }
}
