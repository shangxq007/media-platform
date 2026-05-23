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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/delivery")
@Tag(name = "Delivery Admin", description = "平台运维：渲染成品出站交付")
public class DeliveryAdminController {

    private final DSLContext dsl;
    private final DeliveryJobService deliveryJobService;
    private final CredentialBundlePort credentialBundlePort;
    private final DeliveryCredentialMigrationService credentialMigrationService;
    private final DeliveryRemoteUriIndexService remoteUriIndexService;
    private final DeliveryDestinationUriIndexService destinationUriIndexService;

    public DeliveryAdminController(
            DSLContext dsl,
            DeliveryJobService deliveryJobService,
            CredentialBundlePort credentialBundlePort,
            DeliveryCredentialMigrationService credentialMigrationService,
            DeliveryRemoteUriIndexService remoteUriIndexService,
            DeliveryDestinationUriIndexService destinationUriIndexService) {
        this.dsl = dsl;
        this.deliveryJobService = deliveryJobService;
        this.credentialBundlePort = credentialBundlePort;
        this.credentialMigrationService = credentialMigrationService;
        this.remoteUriIndexService = remoteUriIndexService;
        this.destinationUriIndexService = destinationUriIndexService;
    }

    @GetMapping("/destination-uri-prefixes")
    @Operation(summary = "交付目的地 URI 前缀索引", description = "来自 delivery_destination.config_json，供孤儿扫描对账")
    public java.util.Set<String> listDestinationUriPrefixes() {
        return destinationUriIndexService.collectDestinationUriPrefixes();
    }

    @GetMapping("/by-storage-uri")
    @Operation(summary = "按存储 URI 反查交付任务", description = "匹配 delivery_job.source_uri 或 remote_uri")
    public List<DeliveryRemoteUriIndexService.DeliveryUriHit> findByStorageUri(
            @RequestParam @NotBlank String storageUri,
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "50") int limit) {
        return remoteUriIndexService.findByAnyUri(storageUri, projectId, limit);
    }

    @GetMapping("/jobs")
    @Operation(summary = "分页列出交付任务")
    public List<AdminDeliveryJobResponse> listJobs(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
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
    @Operation(summary = "列出交付目的地（可按租户过滤）")
    public List<DeliveryDestinationResponse> listDestinations(
            @RequestParam(required = false) String tenantId) {
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
    @Operation(summary = "将租户内 delivery_destination.credential_json 迁入 Vault")
    public DeliveryCredentialMigrationService.MigrationReport migrateCredentials(
            @RequestParam String tenantId,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        return credentialMigrationService.migrateTenant(tenantId, dryRun);
    }

    @PostMapping("/jobs/{deliveryJobId}/retry")
    @Operation(summary = "运维重试失败交付")
    public AdminDeliveryJobResponse retryJob(@PathVariable String deliveryJobId) {
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
