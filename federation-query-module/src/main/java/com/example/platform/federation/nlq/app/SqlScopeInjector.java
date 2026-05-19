package com.example.platform.federation.nlq.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SqlScopeInjector {

    private static final Logger log = LoggerFactory.getLogger(SqlScopeInjector.class);

    private static final Pattern WHERE_PATTERN = Pattern.compile("\\bWHERE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern GROUP_BY_PATTERN = Pattern.compile("\\bGROUP\\s+BY\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER_BY_PATTERN = Pattern.compile("\\bORDER\\s+BY\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIMIT_PATTERN = Pattern.compile("\\bLIMIT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HAVING_PATTERN = Pattern.compile("\\bHAVING\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TENANT_CONDITION = Pattern.compile("\\btenant_id\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORKSPACE_CONDITION = Pattern.compile("\\bworkspace_id\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern USER_CONDITION = Pattern.compile("\\b(user_id|created_by|actor_id)\\s*=", Pattern.CASE_INSENSITIVE);

    private static final String GLOBAL_QUERY_PERMISSION = "analytics.global.query";

    public String injectScope(String sql, String tenantId, String workspaceId, String userId,
            boolean isAdmin, boolean hasGlobalQueryPermission) {
        log.debug("SqlScopeInjector: isAdmin={}, hasGlobalPermission={}", isAdmin, hasGlobalQueryPermission);

        if (isAdmin && hasGlobalQueryPermission) {
            log.info("SqlScopeInjector: admin with global query permission, skipping scope injection");
            return sql;
        }

        StringBuilder scopedSql = new StringBuilder(sql);
        Map<String, String> scopeParams = new LinkedHashMap<>();

        boolean hasTenant = TENANT_CONDITION.matcher(sql).find();
        boolean hasWorkspace = WORKSPACE_CONDITION.matcher(sql).find();
        boolean hasUser = USER_CONDITION.matcher(sql).find();

        boolean needsScope = !hasTenant || !hasWorkspace;

        if (!needsScope && !isAdmin) {
            log.info("SqlScopeInjector: SQL already contains scope conditions");
            return sql;
        }

        if (tenantId != null && !hasTenant) {
            scopedSql.append(insertWhereOrAnd(scopedSql.toString()))
                .append(" tenant_id = :tenant_id");
            scopeParams.put("tenant_id", tenantId);
        }

        if (workspaceId != null && !hasWorkspace) {
            scopedSql.append(insertWhereOrAnd(scopedSql.toString()))
                .append(" workspace_id = :workspace_id");
            scopeParams.put("workspace_id", workspaceId);
        }

        if (userId != null && !hasUser && !isAdmin) {
            scopedSql.append(insertWhereOrAnd(scopedSql.toString()))
                .append(" created_by = :user_id");
            scopeParams.put("user_id", userId);
        }

        log.info("SqlScopeInjector: injected scope conditions, params={}", scopeParams.keySet());
        return scopedSql.toString();
    }

    public Map<String, Object> buildScopeParameters(String tenantId, String workspaceId, String userId,
            boolean isAdmin, boolean hasGlobalQueryPermission) {
        Map<String, Object> params = new LinkedHashMap<>();

        if (isAdmin && hasGlobalQueryPermission) {
            return params;
        }

        if (tenantId != null) params.put("tenant_id", tenantId);
        if (workspaceId != null) params.put("workspace_id", workspaceId);
        if (userId != null && !isAdmin) params.put("user_id", userId);

        return params;
    }

    private String insertWhereOrAnd(String sql) {
        if (WHERE_PATTERN.matcher(sql).find()) {
            return " AND";
        }
        return " WHERE";
    }
}
