package com.example.platform.sandbox;

/** Explicit privilege projection; defaults deny privileged and host exposure. */
@org.springframework.modulith.NamedInterface("API")
public record PrivilegePolicy(
        boolean privileged, boolean rootUser, boolean hostNamespaces, boolean hostSockets) {
    public static PrivilegePolicy unprivileged() { return new PrivilegePolicy(false, false, false, false); }
}
