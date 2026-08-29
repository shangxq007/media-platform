package com.example.platform.shared.commercial;

import java.util.Objects;

/**
 * Tenant-scoped canonical commercial principal identity.
 *
 * @param tenantId mandatory tenant boundary
 * @param principalType canonical principal kind
 * @param principalId principal identifier scoped beneath the tenant
 * @param workspaceId optional workspace scope
 * @param organizationId optional organization scope
 */
public record PrincipalRef(
        String tenantId,
        PrincipalType principalType,
        String principalId,
        String workspaceId,
        String organizationId) {

    public PrincipalRef {
        tenantId = requireNonBlank(tenantId, "tenantId");
        Objects.requireNonNull(principalType, "principalType must not be null");
        principalId = requireNonBlank(principalId, "principalId");
        workspaceId = requireOptionalNonBlank(workspaceId, "workspaceId");
        organizationId = requireOptionalNonBlank(organizationId, "organizationId");
    }

    public static PrincipalRef tenantScoped(
            String tenantId, PrincipalType principalType, String principalId) {
        return new PrincipalRef(tenantId, principalType, principalId, null, null);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null/blank");
        }
        return value;
    }

    private static String requireOptionalNonBlank(String value, String field) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank when present");
        }
        return value;
    }
}
