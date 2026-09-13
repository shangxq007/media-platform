package com.example.platform.delivery.api;

import com.example.platform.delivery.api.dto.AdminDeliveryJobResponse;
import com.example.platform.delivery.api.dto.DeliveryDestinationResponse;
import com.example.platform.delivery.app.DeliveryAdministrationService;
import com.example.platform.delivery.app.DeliveryCredentialMigrationService;
import com.example.platform.delivery.app.DeliveryDestinationUriIndexService;
import com.example.platform.delivery.app.DeliveryRemoteUriIndexService;
import com.example.platform.shared.audit.AdminAuditPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/delivery")
@Tag(name = "Delivery Admin", description = "平台运维：渲染成品出站交付（需 ADMIN 角色）")
public class DeliveryAdminController {

    private final DeliveryAdministrationService deliveryAdministrationService;
    private final DeliveryCredentialMigrationService credentialMigrationService;
    private final DeliveryRemoteUriIndexService remoteUriIndexService;
    private final DeliveryDestinationUriIndexService destinationUriIndexService;
    private final AdminAuditPublisher auditPublisher;

    public DeliveryAdminController(
            DeliveryAdministrationService deliveryAdministrationService,
            DeliveryCredentialMigrationService credentialMigrationService,
            DeliveryRemoteUriIndexService remoteUriIndexService,
            DeliveryDestinationUriIndexService destinationUriIndexService,
            AdminAuditPublisher auditPublisher) {
        this.deliveryAdministrationService = deliveryAdministrationService;
        this.credentialMigrationService = credentialMigrationService;
        this.remoteUriIndexService = remoteUriIndexService;
        this.destinationUriIndexService = destinationUriIndexService;
        this.auditPublisher = auditPublisher;
    }

    @GetMapping("/destination-uri-prefixes")
    @Operation(summary = "交付目的地 URI 前缀索引", description = "来自 delivery_destination.config_json，供孤儿扫描对账")
    public java.util.Set<String> listDestinationUriPrefixes(HttpServletRequest request) {
        requireAdminRole(request, "ADMIN_DELIVERY_URI_PREFIXES", "delivery", null, null);
        var result = destinationUriIndexService.collectDestinationUriPrefixes();
        auditPublisher.publish(extractActor(request), extractRoles(request),
                "ADMIN_DELIVERY_URI_PREFIXES", "delivery", null, null, "SUCCESS");
        return result;
    }

    @GetMapping("/by-storage-uri")
    @Operation(summary = "按存储 URI 反查交付任务", description = "通过 Artifact/Storage owner 解析来源引用，或匹配 remote_uri")
    public List<DeliveryRemoteUriIndexService.DeliveryUriHit> findByStorageUri(
            @RequestParam @NotBlank String storageUri,
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest request) {
        requireAdminRole(request, "ADMIN_DELIVERY_LOOKUP_BY_URI", "delivery", storageUri, null);
        var result = remoteUriIndexService.findByAnyUri(storageUri, projectId, limit);
        auditPublisher.publish(extractActor(request), extractRoles(request),
                "ADMIN_DELIVERY_LOOKUP_BY_URI", "delivery", storageUri, null, "SUCCESS");
        return result;
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
        var result = deliveryAdministrationService.listAdministrativeJobs(tenantId, status, page, size);
        auditPublisher.publish(extractActor(request), extractRoles(request),
                "ADMIN_DELIVERY_LIST_JOBS", "delivery_job", null, tenantId, "SUCCESS");
        return result;
    }

    @GetMapping("/destinations")
    @Operation(summary = "列出交付目的地（可按租户过滤，平台管理员）")
    public List<DeliveryDestinationResponse> listDestinations(
            @RequestParam(required = false) String tenantId,
            HttpServletRequest request) {
        requireAdminRole(request, "ADMIN_DELIVERY_LIST_DESTINATIONS", "delivery_destination", null, tenantId);
        var result = deliveryAdministrationService.listAdministrativeDestinations(tenantId);
        auditPublisher.publish(extractActor(request), extractRoles(request),
                "ADMIN_DELIVERY_LIST_DESTINATIONS", "delivery_destination", null, tenantId, "SUCCESS");
        return result;
    }

    @PostMapping("/credentials/migrate")
    @Operation(summary = "将租户内 delivery_destination.credential_json 迁入 Vault（平台管理员）")
    public DeliveryCredentialMigrationService.MigrationReport migrateCredentials(
            @RequestParam String tenantId,
            @RequestParam(defaultValue = "false") boolean dryRun,
            HttpServletRequest request) {
        requireAdminRole(request, "ADMIN_DELIVERY_MIGRATE_CREDENTIALS", "delivery_credential", tenantId, tenantId);
        var result = credentialMigrationService.migrateTenant(tenantId, dryRun);
        auditPublisher.publish(extractActor(request), extractRoles(request),
                "ADMIN_DELIVERY_MIGRATE_CREDENTIALS", "delivery_credential", tenantId, tenantId,
                dryRun ? "DRY_RUN" : result.failed() == 0 ? "SUCCESS" : "PARTIAL_FAILURE");
        return result;
    }

    @PostMapping("/jobs/{deliveryJobId}/retry")
    @Operation(summary = "运维重试失败交付（平台管理员）")
    public AdminDeliveryJobResponse retryJob(
            @PathVariable String deliveryJobId, HttpServletRequest request) {
        requireAdminRole(request, "ADMIN_DELIVERY_RETRY_JOB", "delivery_job", deliveryJobId, null);
        var result = deliveryAdministrationService.retryAdministrativeJob(deliveryJobId);
        auditPublisher.publish(extractActor(request), extractRoles(request),
                "ADMIN_DELIVERY_RETRY_JOB", "delivery_job", deliveryJobId, null, "SUCCESS");
        return result;
    }

    private void requireAdminRole(HttpServletRequest request, String action,
            String resourceType, String resourceId, String tenantId) {
        if (request.isUserInRole("ADMIN") || hasRoleFromRequestAttribute(request, "ADMIN")) {
            return;
        }
        auditPublisher.publish(extractActor(request), extractRoles(request),
                action, resourceType, resourceId, tenantId, "DENIED");
        throw new SecurityException("Admin role required for delivery admin operations");
    }

    private static boolean hasRoleFromRequestAttribute(HttpServletRequest request, String role) {
        Object rolesAttr = request.getAttribute("jwt.roles");
        if (rolesAttr instanceof java.util.List<?> roles) {
            return roles.stream().anyMatch(candidate -> candidate != null
                    && role.equalsIgnoreCase(candidate.toString().trim()));
        }
        if (rolesAttr instanceof String rolesString) {
            for (String candidate : rolesString.split(",")) {
                if (role.equalsIgnoreCase(candidate.trim())) {
                    return true;
                }
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
        }
        return rolesAttr instanceof String rolesString ? rolesString : "none";
    }
}
