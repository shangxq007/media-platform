package com.example.platform.render.domain.governance;

import java.util.Set;

/**
 * Minimal RBAC role with permissions — in-memory only for validation.
 */
public record Role(String roleId, String name, Set<String> permissions) {
    public static Role admin() { return new Role("admin", "Administrator", Set.of("*")); }
    public static Role editor() { return new Role("editor", "Editor", Set.of("read", "write", "execute")); }
    public static Role viewer() { return new Role("viewer", "Viewer", Set.of("read")); }
}
