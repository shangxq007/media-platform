package com.example.platform.audit.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.shared.Ids;
import com.example.platform.shared.Jsons;
import com.example.platform.shared.web.TenantContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DSLContext dsl;
    private final Optional<AuditAlertService> alertService;

    public AuditService(DSLContext dsl, @Autowired(required = false) AuditAlertService alertService) {
        this.dsl = dsl;
        this.alertService = Optional.ofNullable(alertService);
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
                "totalRecords", total == null ? 0 : total
        );
    }

    public String record(String actorType, String actorId, String action,
            String resourceType, String resourceId, Object payload) {
        return record(actorType, actorId, action, resourceType, resourceId, payload, null);
    }

    public String record(String actorType, String actorId, String action,
            String resourceType, String resourceId, Object payload, AuditCategory category) {
        String id = Ids.newId("aud");
        String categoryName = category == null ? AuditCategory.UNKNOWN.name() : category.name();
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
                        categoryName,
                        OffsetDateTime.now()
                )
                .execute();

        if (alertService.isPresent()) {
            try {
                String result = extractResultFromPayload(payload);
                alertService.get().evaluate(categoryName, action, actorType, actorId,
                        resourceType, resourceId, extractTenantFromPayload(payload),
                        result, "", "");
            } catch (Exception e) {
                log.warn("Alert evaluation failed for audit {}: {}", id, e.getMessage());
            }
        }

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

    @SuppressWarnings("unchecked")
    private static String extractResultFromPayload(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Object result = map.get("result");
            return result != null ? result.toString() : null;
        }
        if (payload instanceof String str && !str.isBlank()) {
            try {
                Map<?, ?> map = OBJECT_MAPPER.readValue(str, Map.class);
                Object result = map.get("result");
                return result != null ? result.toString() : null;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static String extractTenantFromPayload(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Object tenant = map.get("targetTenantId");
            return tenant != null ? tenant.toString() : null;
        }
        if (payload instanceof String str && !str.isBlank()) {
            try {
                Map<?, ?> map = OBJECT_MAPPER.readValue(str, Map.class);
                Object tenant = map.get("targetTenantId");
                return tenant != null ? tenant.toString() : null;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
