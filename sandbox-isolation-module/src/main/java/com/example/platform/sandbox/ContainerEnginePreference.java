package com.example.platform.sandbox;

/** Explicit technology selection; AUTO prefers rootless Podman and then equivalent rootless Docker. */
@org.springframework.modulith.NamedInterface("API")
public enum ContainerEnginePreference {
    AUTO,
    PODMAN,
    DOCKER
}
