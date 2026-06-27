package com.example.platform.render.app.governance;

import com.example.platform.render.domain.governance.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Access Governance Service — single platform entry for authorization.
 * Business code never evaluates permissions directly.
 */
@Service
public class AccessGovernanceService {

    private static final Logger log = LoggerFactory.getLogger(AccessGovernanceService.class);
    private final Map<String, Role> roles = new LinkedHashMap<>();

    public AccessGovernanceService() {
        roles.put("admin", Role.admin());
        roles.put("editor", Role.editor());
        roles.put("viewer", Role.viewer());
    }

    public AccessDecision evaluate(AccessRequest request) {
        // RBAC check
        if (!hasPermission(request.subject().subjectId(), request.action())) {
            return AccessDecision.deny("No permission: " + request.action());
        }

        // ABAC: budget state
        if (request.context().containsKey("budgetExceeded")
                && Boolean.TRUE.equals(request.context().get("budgetExceeded"))) {
            return AccessDecision.overage("Budget exceeded for " + request.resource().resourceType());
        }

        // ABAC: require approval
        if ("FULLY_TRUSTED".equals(request.resource().trustLevel())
                && request.context().containsKey("requireApproval")) {
            return AccessDecision.deny("Approval required for trusted resource");
        }

        log.debug("Access allowed: {} on {} for {}", request.action(), request.resource().resourceId(),
                request.subject().subjectId());
        return AccessDecision.allow();
    }

    private boolean hasPermission(String subjectId, String action) {
        Role role = resolveRole(subjectId);
        if (role == null) return false;
        return role.permissions().contains("*") || role.permissions().contains(action);
    }

    private Role resolveRole(String subjectId) {
        return switch (subjectId) {
            case "admin" -> roles.get("admin");
            case "editor" -> roles.get("editor");
            case "viewer" -> roles.get("viewer");
            default -> roles.get("viewer");
        };
    }
}
