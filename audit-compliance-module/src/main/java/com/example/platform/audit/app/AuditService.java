package com.example.platform.audit.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.shared.Ids;
import com.example.platform.shared.Jsons;
import com.example.platform.shared.web.TenantContext;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuditService {
    private final DSLContext dsl;

    public AuditService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Map<String, Object> overview() {
        String currentTenant = TenantContext.get();
        Integer total;
        if (currentTenant != null) {
            total = dsl.selectCount()
                    .from(table("audit_records"))
                    .where(field("actor_id").eq(currentTenant))
                    .fetchOne(0, Integer.class);
        } else {
            total = dsl.fetchCount(table("audit_records"));
        }
        return Map.of(
                "module", "audit-compliance-module",
                "status", "active",
                "description", "审计与合规模块，负责关键操作审计、配置变更审计与查询。",
                "totalRecords", total
        );
    }

    public String record(String actorType, String actorId, String action,
            String resourceType, String resourceId, Object payload) {
        return record(actorType, actorId, action, resourceType, resourceId, payload, null);
    }

    public String record(String actorType, String actorId, String action,
            String resourceType, String resourceId, Object payload, AuditCategory category) {
        String id = Ids.newId("aud");
        dsl.insertInto(table("audit_records"))
                .columns(
                        field("id"),
                        field("actor_type"),
                        field("actor_id"),
                        field("action"),
                        field("resource_type"),
                        field("resource_id"),
                        field("payload"),
                        field("category"),
                        field("created_at")
                )
                .values(
                        id,
                        actorType,
                        actorId,
                        action,
                        resourceType,
                        resourceId,
                        payload == null ? null : Jsons.toJson(payload),
                        category == null ? null : category.name(),
                        OffsetDateTime.now()
                )
                .execute();
        return id;
    }

    public List<Map<String, Object>> recent(int limit) {
        String currentTenant = TenantContext.get();
        if (currentTenant != null) {
            return dsl.select().from(table("audit_records"))
                    .where(field("actor_id").eq(currentTenant))
                    .orderBy(field("created_at").desc())
                    .limit(limit)
                    .fetchMaps();
        }
        return dsl.select().from(table("audit_records"))
                .orderBy(field("created_at").desc())
                .limit(limit)
                .fetchMaps();
    }

    public List<Map<String, Object>> findByCategory(AuditCategory category, int limit) {
        String currentTenant = TenantContext.get();
        if (currentTenant != null) {
            return dsl.select().from(table("audit_records"))
                    .where(field("category").eq(category.name()))
                    .and(field("actor_id").eq(currentTenant))
                    .orderBy(field("created_at").desc())
                    .limit(limit)
                    .fetchMaps();
        }
        return dsl.select().from(table("audit_records"))
                .where(field("category").eq(category.name()))
                .orderBy(field("created_at").desc())
                .limit(limit)
                .fetchMaps();
    }

    public List<Map<String, Object>> findByResource(String resourceType, String resourceId) {
        String currentTenant = TenantContext.get();
        if (currentTenant != null) {
            return dsl.select().from(table("audit_records"))
                    .where(field("resource_type").eq(resourceType))
                    .and(field("resource_id").eq(resourceId))
                    .and(field("actor_id").eq(currentTenant))
                    .orderBy(field("created_at").desc())
                    .fetchMaps();
        }
        return dsl.select().from(table("audit_records"))
                .where(field("resource_type").eq(resourceType))
                .and(field("resource_id").eq(resourceId))
                .orderBy(field("created_at").desc())
                .fetchMaps();
    }
}
