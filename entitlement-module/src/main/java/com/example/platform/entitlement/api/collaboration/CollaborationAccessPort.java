package com.example.platform.entitlement.api.collaboration;

import com.example.platform.shared.authorization.*;

/**
 * Port for shared-resource (collaboration) access checks used in the ABAC decision chain.
 */
public interface CollaborationAccessPort {

    /**
     * @return true when the user has an active grant for the resource with sufficient permission
     */
    boolean hasSharedAccess(String tenantId, String userId, String resourceType, String resourceId, String action);
}
