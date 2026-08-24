package com.example.platform.workerfabric.domain;

/** The one placement authority active after an execution backend is selected. */
public enum PlacementAuthorityScope {
    PLATFORM_MANAGED,
    BACKEND_DELEGATED,
    REMOTE_PROVIDER_MANAGED
}
