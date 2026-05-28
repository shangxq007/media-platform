package com.example.platform.delivery.api;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.table;

import com.example.platform.delivery.api.dto.AdminDeliveryJobResponse;
import com.example.platform.delivery.api.dto.DeliveryDestinationResponse;
import com.example.platform.delivery.app.DeliveryCredentialMigrationService;
import com.example.platform.delivery.app.DeliveryDestinationUriIndexService;
import com.example.platform.delivery.app.DeliveryJobService;
import com.example.platform.delivery.app.DeliveryRemoteUriIndexService;
import com.example.platform.secrets.api.port.CredentialBundlePort;
import com.example.platform.shared.audit.AdminAuditPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/delivery")
@Tag(name = "Delivery Admin", description = "平台运维：渲染成品出站交付（需 ADMIN 角色）")
public class DeliveryAdminController {

    private final DSLContext dsl;
    private final DeliveryJobService deliveryJobService;
    private final CredentialBundlePort credentialBundlePort;
    private final DeliveryCredentialMigrationService credentialMigrationService;
    private final DeliveryRemoteUriIndexService remoteUriIndexService;
    private final DeliveryDestinationUriIndexService destinationUriIndexService;
    private final AdminAuditPublisher auditPublisher;

    public DeliveryAdminController(
            DSLContext dsl,
            DeliveryJobService deliveryJobService,
            CredentialBundlePort credentialBundlePort,
            DeliveryCredentialMigrationService credentialMigrationService,
            DeliveryRemoteUriIndexService remoteUriIndexService,
            DeliveryDestinationUriIndexService destinationUriIndexService,
            AdminAuditPublisher auditPublisher) {
        this.dsl = dsl;
        this.deliveryJobService = deliveryJobService;
        this.credentialBundlePort = credentialBundlePort;
        this.credentialMigrationService = credentialMigrationService;
        this.remoteUriIndexService = remoteUriIndexService;
        this.destinationUriIndexService = destinationUriIndexService;
        this.auditPublisher = auditPublisher;
    }

    @GetMapping("/destination-uri-prefixes")
    @Operation(summary = "交付目的地 URI 前缀索引", description = "来自 delivery_destination.config_json，供孤儿扫描对账")
    public java.util.Set<String> listDestinationUriPrefixes(HttpServletRequest request) {
        requireAdminRole(request, "ADMIN_DELIVERY_URI_PREFIXES", "delivery", null, null);
        auditPublisher.publish(
                extractActor(request), extractRoles(request),
                "ADMIN_DELIVERY_URI_PREFIXES", "delivery", null, null, "SUCCESS");
        return destinationUriIndexService.collectDestinationUriPrefixes();
    }

    @GetMapping("/by-storage-uri")
    @Operation(summary = "按存储 URI 反查交付任务", description = "匹配 delivery_job.source_uri 或 remote_uri")
    public List<DeliveryRemoteUriIndexService.DeliveryUriHit> findByStorageUri(
            @RequestParam @NotBlank String storageUri,
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest request) {
        requireAdminRole(request, "ADMIN_DELIVERY_LOOKUP_BY_URI", "delivery", storageUri, null);
        auditPublisher.publish(
                extractActor(request), extractRoles(request),
                "ADMIN_DELIVERY_LOOKUP_BY_URI", "delivery", storageUri, null, "SUCCESS");
        return remoteUriIndexService.findByAnyUri(storageUri, projectId, limit);
    }

    @GetMapping("/jobs")
    @Operation(summary = "分页列出交付任务（平台管理员）")
    public List<AdminDeliveryJobResponse> listJobs(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest request) {
        requireAdminRole(request, "ADMIN_DELIVERY_LIST_JOBS", "delivery_job", null, tenantId);
        auditPublisher.publish(
                extractActor(request), extractRoles(request),
                "ADMIN_DELIVERY_LIST_JOBS", "delivery_job", null, tenantId, "SUCCESS");
        int limit = Math.min(Math.max(size, 1), 200);
        int offset = Math.max(page, 0) * limit;
        var condition = noCondition();
        if (tenantId != null && !tenantId.isBlank()) {
            condition = condition.and(field("tenant_id").eq(tenantId));
        }
        if (status != null && !status.isBlank()) {
            condition = condition.and(field("status").eq(status));
        }
        return dsl.select()
                .from(table("delivery_job"))
                .where(condition)
                .orderBy(field("created_at").desc())
                .limit(limit)
                .offset(offset)
                .fetch(this::mapAdminJob);
    }

    @GetMapping("/destinations")
    @Operation(summary = "列出交付目的地（可按租户过滤，平台管理员）")
    public List<DeliveryDestinationResponse> listDestinations(
            @RequestParam(required = false) String tenantId,
            HttpServletRequest request) {
        requireAdminRole(request, "ADMIN_DELIVERY_LIST_DESTINATIONS", "delivery_destination", null, tenantId);
        auditPublisher.publish(
                extractActor(request), extractRoles(request),
                "ADMIN_DELIVERY_LIST_DESTINATIONS", "delivery_destination", null, tenantId, "SUCCESS");
        var destCondition = noCondition();
        if (tenantId != null && !tenantId.isBlank()) {
            destCondition = destCondition.and(field("tenant_id").eq(tenantId));
        }
        return dsl.select()
                .from(table("delivery_destination"))
                .where(destCondition)
                .orderBy(field("created_at").desc())
                .fetch(r -> new DeliveryDestinationResponse(
                        r.get(field("id", String.class)),
                        r.get(field("tenant_id", String.class)),
                        r.get(field("name", String.class)),
                        r.get(field("protocol", String.class)),
                        Boolean.TRUE.equals(r.get(field("enabled", Boolean.class))),
                        r.get(field("credential_ref", String.class)),
                        credentialBundlePort.hasCredentials(
                                r.get(field("credential_ref", String.class)),
                                r.get(field("credential_json", String.class)))));
    }

    @PostMapping("/credentials/migrate")
    @Operation(summary = "将租户内 delivery_destination.credential_json 迁入 Vault（平台管理员）")
    public DeliveryCredentialMigrationService.MigrationReport migrateCredentials(
            @RequestParam String tenantId,
            @RequestParam(defaultValue = "false") boolean dryRun,
            HttpServletRequest request) {
        requireAdminRole(request, "ADMIN_DELIVERY_MIGRATE_CREDENTIALS", "delivery_credential", tenantId, tenantId);
        auditPublisher.publish(
                extractActor(request), extractRoles(request),
                "ADMIN_DELIVERY_MIGRATE_CREDENTIALS", "delivery_credential", tenantId, tenantId,
                dryRun ? "DRY_RUN" : "SUCCESS");
        return credentialMigrationService.migrateTenant(tenantId, dryRun);
    }

    @PostMapping("/jobs/{deliveryJobId}/retry")
    @Operation(summary = "运维重试失败交付（平台管理员）")
    public AdminDeliveryJobResponse retryJob(@PathVariable String deliveryJobId,
            HttpServletRequest request) {
        requireAdminRole(request, "ADMIN_DELIVERY_RETRY_JOB", "delivery_job", deliveryJobId, null);
        auditPublisher.publish(
                extractActor(request), extractRoles(request),
                "ADMIN_DELIVERY_RETRY_JOB", "delivery_job", deliveryJobId, null, "SUCCESS");
        Record row = dsl.select()
                .from(table("delivery_job"))
                .where(field("id").eq(deliveryJobId))
                .fetchOne();
        if (row == null) {
            throw new IllegalArgumentException("Delivery job not found");
        }
        deliveryJobService.retryDelivery(
                row.get(field("tenant_id", String.class)),
                row.get(field("project_id", String.class)),
                row.get(field("render_job_id", String.class)),
                deliveryJobId);
        Record updated = dsl.select()
                .from(table("delivery_job"))
                .where(field("id").eq(deliveryJobId))
                .fetchOne();
        return mapAdminJob(updated);
    }

    private void requireAdminRole(HttpServletRequest request) {
        requireAdminRole(request, "ADMIN_DELIVERY_OPERATION", "delivery", null, null);
    }

    private void requireAdminRole(HttpServletRequest request, String action,
            String resourceType, String resourceId, String tenantId) {
        if (request.isUserInRole("ADMIN")) {
            return;
        }
        // Legacy HMAC JWT path: check jwt.roles request attribute
        if (hasRoleFromRequestAttribute(request, "ADMIN")) {
            return;
        }
        auditPublisher.publish(
                extractActor(request), extractRoles(request),
                action, resourceType, resourceId, tenantId, "DENIED");
        throw new SecurityException("Admin role required for delivery admin operations");
    }

    private static boolean hasRoleFromRequestAttribute(HttpServletRequest request, String role) {
        Object rolesAttr = request.getAttribute("jwt.roles");
        if (rolesAttr instanceof java.util.List<?> roles) {
            return roles.stream().anyMatch(r -> r != null && role.equalsIgnoreCase(r.toString().trim()));
        } else if (rolesAttr instanceof String rolesStr) {
            for (String r : rolesStr.split(",")) {
                if (role.equalsIgnoreCase(r.trim())) return true;
            }
        }
        return false;
    }

    private static String extractActor(HttpServletRequest request) {
        Object subject = request.getAttribute("jwt.subject");
        return subject != null && !subject.toString().isBlank() ? subject.toString() : "anonymous";
    }

    private static String extractRoles(HttpServletRequest request) {
        Object rolesAttr = request.getAttribute("jwt.roles");
        if (rolesAttr instanceof java.util.List<?> roles) {
            return String.join(",", roles.stream().map(Object::toString).toList());
        } else if (rolesAttr instanceof String rolesStr) {
            return rolesStr;
        }
        return "none";
    }

    private AdminDeliveryJobResponse mapAdminJob(Record r) {
        return new AdminDeliveryJobResponse(
                r.get(field("id", String.class)),
                r.get(field("tenant_id", String.class)),
                r.get(field("project_id", String.class)),
                r.get(field("render_job_id", String.class)),
                r.get(field("destination_id", String.class)),
                r.get(field("status", String.class)),
                r.get(field("source_uri", String.class)),
                r.get(field("remote_uri", String.class)),
                r.get(field("bytes_transferred", Long.class)),
                r.get(field("attempt_count", Integer.class)),
                r.get(field("error_code", String.class)),
                r.get(field("error_message", String.class)),
                r.get(field("created_at", OffsetDateTime.class)),
                r.get(field("completed_at", OffsetDateTime.class)));
    }
}
