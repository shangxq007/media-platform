package com.example.platform.render.infrastructure.tenant;

import com.example.platform.shared.web.TenantContext;
import java.util.Set;
import org.jooq.ExecuteContext;
import org.jooq.Param;
import org.jooq.Query;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultExecuteListener;
import org.jooq.tools.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TenantIsolationExecuteListener extends DefaultExecuteListener {

    private static final Logger log = LoggerFactory.getLogger(TenantIsolationExecuteListener.class);

    private static final Set<String> TENANT_EXEMPT_TABLES = Set.of(
            "flyway_schema_history",
            "outbox_events",
            "error_code_catalog",
            "feature_flag_audit",
            "render_job_status_history",
            "schema_version",
            "databasechangelog",
            "databasechangeloglock"
    );

    @Override
    public void renderStart(ExecuteContext ctx) {
        String tenantId = TenantContext.get();
        if (StringUtils.isBlank(tenantId)) {
            return;
        }

        Query query = ctx.query();
        if (query == null) {
            return;
        }

        String sql = ctx.sql();
        if (sql == null) {
            return;
        }

        String normalizedSql = sql.trim().toUpperCase();
        if (!normalizedSql.startsWith("SELECT") &&
            !normalizedSql.startsWith("UPDATE") &&
            !normalizedSql.startsWith("DELETE")) {
            return;
        }

        String tableName = extractTableName(sql);
        if (tableName == null || TENANT_EXEMPT_TABLES.contains(tableName.toLowerCase())) {
            return;
        }

        if (sql.toLowerCase().contains("tenant_id") &&
            (sql.toLowerCase().contains("= ?") || sql.toLowerCase().contains("= ?"))) {
            return;
        }
    }

    private String extractTableName(String sql) {
        String trimmed = sql.trim();
        String upper = trimmed.toUpperCase();

        if (upper.startsWith("SELECT")) {
            int fromIdx = upper.indexOf(" FROM ");
            if (fromIdx < 0) return null;
            String afterFrom = trimmed.substring(fromIdx + 6).trim();
            return firstToken(afterFrom);
        }

        if (upper.startsWith("UPDATE")) {
            String afterUpdate = trimmed.substring(6).trim();
            return firstToken(afterUpdate);
        }

        if (upper.startsWith("DELETE")) {
            int fromIdx = upper.indexOf(" FROM ");
            if (fromIdx < 0) {
                int deleteIdx = upper.indexOf("DELETE ");
                if (deleteIdx >= 0) {
                    String afterDelete = trimmed.substring(deleteIdx + 7).trim();
                    if (afterDelete.toUpperCase().startsWith("FROM ")) {
                        afterDelete = afterDelete.substring(5).trim();
                    }
                    return firstToken(afterDelete);
                }
                return null;
            }
            String afterFrom = trimmed.substring(fromIdx + 6).trim();
            return firstToken(afterFrom);
        }

        return null;
    }

    private String firstToken(String s) {
        String trimmed = s.trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isWhitespace(c) || c == ',' || c == '(' || c == ';') {
                break;
            }
            sb.append(c);
        }
        String token = sb.toString();
        if (token.startsWith("\"") && token.endsWith("\"")) {
            token = token.substring(1, token.length() - 1);
        }
        return token.isEmpty() ? null : token;
    }
}
