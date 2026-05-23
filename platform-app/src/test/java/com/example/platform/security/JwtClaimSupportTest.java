package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtClaimSupportTest {

    @Test
    void normalizeRoles_fromCommaSeparatedString() {
        assertEquals(List.of("ADMIN", "EDITOR"), JwtClaimSupport.normalizeRoles("ADMIN, EDITOR"));
    }

    @Test
    void normalizeRoles_fromCollection() {
        assertEquals(List.of("ADMIN"), JwtClaimSupport.normalizeRoles(List.of("ADMIN")));
    }

    @Test
    void tenantId_prefersConfiguredClaim() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("u1")
                .claim("tenantId", "tenant-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        assertEquals("tenant-1", JwtClaimSupport.tenantId(jwt, "tenantId"));
    }

    @Test
    void userId_prefersPlatformUserIdClaim() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("authentik-uuid")
                .claim("platform_user_id", "user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        assertEquals("user-1", JwtClaimSupport.userId(jwt, "platform_user_id"));
    }

    @Test
    void roles_fallsBackToGroups() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("u1")
                .claim("groups", List.of("EDITOR"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        assertTrue(JwtClaimSupport.roles(jwt, "roles").contains("EDITOR"));
    }
}
