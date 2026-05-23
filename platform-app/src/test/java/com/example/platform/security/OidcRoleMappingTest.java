package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.platform.identity.domain.User;
import java.util.List;
import org.junit.jupiter.api.Test;

class OidcRoleMappingTest {

    @Test
    void mapsGroupsToPlatformRoles() {
        assertEquals(List.of("ADMIN", "EDITOR"), OidcRoleMapping.toPlatformRoleKeys(List.of("ADMIN", "EDITOR")));
    }

    @Test
    void defaultsToViewer() {
        assertEquals(List.of("VIEWER"), OidcRoleMapping.toPlatformRoleKeys(List.of()));
    }

    @Test
    void mapsToUserRole() {
        assertEquals(User.UserRole.ADMIN, OidcRoleMapping.toUserRole(List.of("ADMIN")));
        assertEquals(User.UserRole.MEMBER, OidcRoleMapping.toUserRole(List.of("EDITOR")));
    }
}
