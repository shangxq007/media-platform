package com.example.platform.security;

import com.example.platform.identity.domain.User;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Maps OIDC groups/roles claims to platform RBAC role keys and {@link User.UserRole}. */
public final class OidcRoleMapping {

    private static final List<String> ROLE_PRIORITY = List.of("ADMIN", "EDITOR", "VIEWER");

    private OidcRoleMapping() {}

    public static List<String> toPlatformRoleKeys(List<String> claimRoles) {
        if (claimRoles == null || claimRoles.isEmpty()) {
            return List.of("VIEWER");
        }
        Set<String> mapped = new LinkedHashSet<>();
        for (String raw : claimRoles) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            if (normalized.startsWith("ROLE_")) {
                normalized = normalized.substring("ROLE_".length());
            }
            mapped.add(mapSingle(normalized));
        }
        if (mapped.isEmpty()) {
            mapped.add("VIEWER");
        }
        List<String> ordered = new ArrayList<>();
        for (String key : ROLE_PRIORITY) {
            if (mapped.contains(key)) {
                ordered.add(key);
            }
        }
        return ordered.isEmpty() ? List.of("VIEWER") : ordered;
    }

    public static User.UserRole toUserRole(List<String> platformRoleKeys) {
        if (platformRoleKeys.contains("ADMIN")) {
            return User.UserRole.ADMIN;
        }
        if (platformRoleKeys.contains("EDITOR")) {
            return User.UserRole.MEMBER;
        }
        return User.UserRole.VIEWER;
    }

    private static String mapSingle(String normalized) {
        return switch (normalized) {
            case "ADMIN", "ADMINISTRATOR", "OWNER" -> "ADMIN";
            case "EDITOR", "MEMBER", "USER", "WRITE" -> "EDITOR";
            case "VIEWER", "READ", "GUEST" -> "VIEWER";
            default -> normalized;
        };
    }
}
