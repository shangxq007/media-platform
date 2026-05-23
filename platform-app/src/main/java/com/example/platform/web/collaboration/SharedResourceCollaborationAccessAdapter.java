package com.example.platform.web.collaboration;

import com.example.platform.shared.collaboration.CollaborationAccessPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SharedResourceCollaborationAccessAdapter implements CollaborationAccessPort {

    private final Optional<SharedResourceJdbcRepository> repository;

    public SharedResourceCollaborationAccessAdapter(Optional<SharedResourceJdbcRepository> repository) {
        this.repository = repository;
    }

    @Override
    public boolean hasSharedAccess(String tenantId, String userId, String resourceType,
                                   String resourceId, String action) {
        if (tenantId == null || userId == null || resourceType == null || resourceId == null) {
            return false;
        }
        return repository
                .map(r -> r.hasActiveGrant(tenantId, userId, resourceType, resourceId, action))
                .orElse(false);
    }
}
