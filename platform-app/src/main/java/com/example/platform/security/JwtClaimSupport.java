package com.example.platform.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Normalizes tenant/role claims from OIDC or legacy HMAC JWT payloads.
 */
public final class JwtClaimSupport {

    private JwtClaimSupport() {}

    public static String tenantId(Jwt jwt, String tenantClaim) {
        if (jwt == null) {
            return null;
        }
        String direct = jwt.getClaimAsString(tenantClaim);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        return firstStringClaim(jwt, "tenant_id", "tenant");
    }

    public static String userId(Jwt jwt, String userIdClaim) {
        if (jwt == null) {
            return null;
        }
        if (userIdClaim != null && !userIdClaim.isBlank()) {
            String explicit = jwt.getClaimAsString(userIdClaim);
            if (explicit != null && !explicit.isBlank()) {
                return explicit;
            }
        }
        return firstStringClaim(jwt, "platform_user_id", "platformUserId", "userId");
    }

    public static List<String> roles(Jwt jwt, String rolesClaim) {
        if (jwt == null) {
            return List.of();
        }
        Object raw = jwt.getClaim(rolesClaim);
        if (raw == null) {
            raw = jwt.getClaim("groups");
        }
        return normalizeRoles(raw);
    }

    @SuppressWarnings("unchecked")
    static List<String> normalizeRoles(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof String s) {
            if (s.isBlank()) {
                return List.of();
            }
            return List.of(s.split("\\s*,\\s*"));
        }
        if (raw instanceof Collection<?> collection) {
            List<String> out = new ArrayList<>();
            for (Object item : collection) {
                if (item != null) {
                    String v = item.toString().trim();
                    if (!v.isEmpty()) {
                        out.add(v);
                    }
                }
            }
            return List.copyOf(out);
        }
        return List.of(raw.toString());
    }

    private static String firstStringClaim(Jwt jwt, String... names) {
        for (String name : names) {
            String v = jwt.getClaimAsString(name);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
