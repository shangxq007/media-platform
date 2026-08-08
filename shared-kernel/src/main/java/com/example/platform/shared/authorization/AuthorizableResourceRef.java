package com.example.platform.shared.authorization;

import java.util.Objects;
import java.util.Optional;

/**
 * A bounded reference to the resource being accessed.
 *
 * <p>Not a JSON bag — only the fields the authorization model needs. The
 * {@code tenantId} is mandatory (it drives the tenant-boundary default-deny). The
 * {@code projectId}/{@code ownerId} are optional and only populated when the
 * decision requires a bounded resource relation beyond tenant scope.</p>
 *
 * @param resourceType the resource type (frozen vocabulary)
 * @param resourceId   the specific resource id (null for collection/list operations)
 * @param tenantId     the tenant the resource belongs to (never null)
 * @param projectId    optional project scoping
 * @param ownerId      optional owner scoping
 */
public record AuthorizableResourceRef(
        AuthorizationResourceType resourceType,
        String resourceId,
        String tenantId,
        String projectId,
        String ownerId) {

    public AuthorizableResourceRef {
        Objects.requireNonNull(resourceType, "resourceType must not be null");
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null/blank");
        }
    }

    public AuthorizableResourceRef(AuthorizationResourceType resourceType, String resourceId, String tenantId) {
        this(resourceType, resourceId, tenantId, null, null);
    }
}
