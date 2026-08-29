package com.example.platform.shared.commercial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PrincipalRefTest {

    @Test
    void requiresTenantTypeAndSubjectIdentity() {
        assertThrows(IllegalArgumentException.class,
                () -> new PrincipalRef(null, PrincipalType.USER, "user-1", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PrincipalRef(" ", PrincipalType.USER, "user-1", null, null));
        assertThrows(NullPointerException.class,
                () -> new PrincipalRef("tenant-1", null, "user-1", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PrincipalRef("tenant-1", PrincipalType.USER, " ", null, null));
    }

    @Test
    void carriesOptionalWorkspaceAndOrganizationScope() {
        PrincipalRef scoped = new PrincipalRef(
                "tenant-1", PrincipalType.USER, "user-1", "workspace-1", "org-1");

        assertEquals("tenant-1", scoped.tenantId());
        assertEquals(PrincipalType.USER, scoped.principalType());
        assertEquals("user-1", scoped.principalId());
        assertEquals("workspace-1", scoped.workspaceId());
        assertEquals("org-1", scoped.organizationId());

        PrincipalRef tenantScoped = PrincipalRef.tenantScoped(
                "tenant-1", PrincipalType.SERVICE_ACCOUNT, "service-1");
        assertNull(tenantScoped.workspaceId());
        assertNull(tenantScoped.organizationId());
    }

    @Test
    void rejectsBlankOptionalScopeIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> new PrincipalRef("tenant-1", PrincipalType.USER, "user-1", " ", null));
        assertThrows(IllegalArgumentException.class,
                () -> new PrincipalRef("tenant-1", PrincipalType.USER, "user-1", null, " "));
    }
}
