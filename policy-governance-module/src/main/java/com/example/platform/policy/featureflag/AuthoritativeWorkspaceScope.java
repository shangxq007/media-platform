package com.example.platform.policy.featureflag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Resolves a workspace only from persisted tenant membership, never from client claims. */
@Component
final class AuthoritativeWorkspaceScope {
    private final JdbcTemplate jdbc;

    AuthoritativeWorkspaceScope(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    boolean isActiveMember(String tenantId, String workspaceId, String userId) {
        if (tenantId == null || workspaceId == null || userId == null) return false;
        Integer count = jdbc.queryForObject("""
                select count(*) from workspace w
                join workspace_member m on m.workspace_id = w.id
                join \"user\" u on u.id = m.user_id and u.tenant_id = w.tenant_id
                where w.id = ? and w.tenant_id = ?
                  and m.user_id = ? and m.status = 'ACTIVE'
                  and u.status = 'ACTIVE'
                """, Integer.class, workspaceId, tenantId, userId);
        return count != null && count == 1;
    }
}
