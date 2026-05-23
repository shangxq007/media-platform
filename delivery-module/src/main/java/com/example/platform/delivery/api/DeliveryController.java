package com.example.platform.delivery.api;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.delivery.api.dto.CreateDeliveryDestinationRequest;
import com.example.platform.delivery.api.dto.CreateDeliveryPolicyRequest;
import com.example.platform.delivery.api.dto.DeliveryDestinationResponse;
import com.example.platform.delivery.api.dto.UpdateDeliveryDestinationRequest;
import com.example.platform.delivery.api.dto.UpdateDeliveryPolicyRequest;
import com.example.platform.delivery.api.dto.DeliveryJobResponse;
import com.example.platform.delivery.api.dto.DeliveryPolicyResponse;
import com.example.platform.delivery.app.DeliveryDestinationCredentialService;
import com.example.platform.delivery.app.DeliveryJobService;
import com.example.platform.secrets.api.port.CredentialBundlePort;
import com.example.platform.shared.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
@Tag(name = "Delivery", description = "渲染成品出站交付")
public class DeliveryController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DSLContext dsl;
    private final DeliveryJobService deliveryJobService;
    private final DeliveryDestinationCredentialService destinationCredentialService;
    private final CredentialBundlePort credentialBundlePort;

    public DeliveryController(
            DSLContext dsl,
            DeliveryJobService deliveryJobService,
            DeliveryDestinationCredentialService destinationCredentialService,
            CredentialBundlePort credentialBundlePort) {
        this.dsl = dsl;
        this.deliveryJobService = deliveryJobService;
        this.destinationCredentialService = destinationCredentialService;
        this.credentialBundlePort = credentialBundlePort;
    }

    @PostMapping("/delivery/destinations")
    @Operation(summary = "创建交付目的地")
    public DeliveryDestinationResponse createDestination(
            @PathVariable String tenantId,
            @Valid @RequestBody CreateDeliveryDestinationRequest request) {
        String id = Ids.newId("dst");
        String configJson = toJson(request.config());
        var stored = destinationCredentialService.persist(
                tenantId, id, request.credentialRef(), request.credentials());
        dsl.insertInto(table("delivery_destination"))
                .columns(field("id"), field("tenant_id"), field("name"), field("protocol"),
                        field("config_json"), field("credential_ref"), field("credential_json"),
                        field("enabled"), field("created_at"))
                .values(id, tenantId, request.name(), request.protocol(), configJson,
                        stored.credentialRef(), stored.credentialJson(),
                        request.enabled() != null ? request.enabled() : true, OffsetDateTime.now())
                .execute();
        return toDestinationResponse(id, tenantId, request.name(), request.protocol(),
                request.enabled() != null ? request.enabled() : true,
                stored.credentialRef(), stored.credentialJson());
    }

    @PostMapping("/delivery/destinations/{destinationId}/probe")
    @Operation(summary = "探测交付目的地连通性")
    public Map<String, Object> probeDestination(
            @PathVariable String tenantId,
            @PathVariable String destinationId) {
        var result = deliveryJobService.probeDestination(tenantId, destinationId);
        return Map.of("ok", result.ok(), "message", result.message() != null ? result.message() : "");
    }

    @PatchMapping("/delivery/destinations/{destinationId}")
    @Operation(summary = "更新交付目的地")
    public DeliveryDestinationResponse updateDestination(
            @PathVariable String tenantId,
            @PathVariable String destinationId,
            @RequestBody UpdateDeliveryDestinationRequest request) {
        deliveryJobService.updateDestination(tenantId, destinationId, request);
        if (request.credentialRef() != null || (request.credentials() != null && !request.credentials().isEmpty())) {
            var stored = destinationCredentialService.persist(
                    tenantId, destinationId, request.credentialRef(), request.credentials());
            dsl.update(table("delivery_destination"))
                    .set(field("credential_ref"), stored.credentialRef())
                    .set(field("credential_json"), stored.credentialJson())
                    .where(field("id").eq(destinationId))
                    .execute();
        }
        Record row = dsl.select()
                .from(table("delivery_destination"))
                .where(field("id").eq(destinationId))
                .and(field("tenant_id").eq(tenantId))
                .fetchOne();
        if (row == null) {
            throw new IllegalArgumentException("Destination not found");
        }
        return mapDestinationRow(tenantId, row);
    }

    @DeleteMapping("/delivery/destinations/{destinationId}")
    @Operation(summary = "删除交付目的地")
    public Map<String, String> deleteDestination(
            @PathVariable String tenantId,
            @PathVariable String destinationId) {
        deliveryJobService.deleteDestination(tenantId, destinationId);
        return Map.of("destinationId", destinationId, "deleted", "true");
    }

    @GetMapping("/delivery/destinations")
    public List<DeliveryDestinationResponse> listDestinations(@PathVariable String tenantId) {
        return dsl.select()
                .from(table("delivery_destination"))
                .where(field("tenant_id").eq(tenantId))
                .fetch(r -> mapDestinationRow(tenantId, r));
    }

    @GetMapping("/projects/{projectId}/delivery/policies")
    @Operation(summary = "列出项目交付策略")
    public List<DeliveryPolicyResponse> listPolicies(
            @PathVariable String tenantId,
            @PathVariable String projectId) {
        return dsl.select()
                .from(table("delivery_policy"))
                .where(field("tenant_id").eq(tenantId))
                .and(field("project_id").eq(projectId))
                .fetch(r -> new DeliveryPolicyResponse(
                        r.get(field("id", String.class)),
                        tenantId,
                        projectId,
                        r.get(field("destination_id", String.class)),
                        r.get(field("artifact_selector", String.class)),
                        r.get(field("path_template", String.class)),
                        r.get(field("trigger_mode", String.class)),
                        Boolean.TRUE.equals(r.get(field("enabled", Boolean.class)))));
    }

    @PostMapping("/projects/{projectId}/delivery/policies")
    @Operation(summary = "绑定项目自动交付策略")
    public Map<String, String> createPolicy(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @Valid @RequestBody CreateDeliveryPolicyRequest request) {
        String id = Ids.newId("dlp");
        dsl.insertInto(table("delivery_policy"))
                .columns(field("id"), field("tenant_id"), field("project_id"), field("destination_id"),
                        field("artifact_selector"), field("path_template"), field("trigger_mode"),
                        field("enabled"), field("created_at"))
                .values(id, tenantId, projectId, request.destinationId(),
                        request.artifactSelectorOrDefault(), request.pathTemplateOrDefault(),
                        request.triggerModeOrDefault(), true, OffsetDateTime.now())
                .execute();
        return Map.of("policyId", id);
    }

    @PatchMapping("/projects/{projectId}/delivery/policies/{policyId}")
    @Operation(summary = "启用/禁用交付策略")
    public Map<String, String> updatePolicy(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String policyId,
            @RequestBody UpdateDeliveryPolicyRequest request) {
        if (request.enabled() == null) {
            throw new IllegalArgumentException("enabled is required");
        }
        deliveryJobService.updatePolicyEnabled(tenantId, projectId, policyId, request.enabled());
        return Map.of("policyId", policyId, "enabled", String.valueOf(request.enabled()));
    }

    @DeleteMapping("/projects/{projectId}/delivery/policies/{policyId}")
    @Operation(summary = "删除交付策略")
    public Map<String, String> deletePolicy(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String policyId) {
        deliveryJobService.deletePolicy(tenantId, projectId, policyId);
        return Map.of("policyId", policyId, "deleted", "true");
    }

    @GetMapping("/projects/{projectId}/render-jobs/{jobId}/deliveries")
    public List<DeliveryJobResponse> listDeliveries(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String jobId) {
        return dsl.select()
                .from(table("delivery_job"))
                .where(field("tenant_id").eq(tenantId))
                .and(field("project_id").eq(projectId))
                .and(field("render_job_id").eq(jobId))
                .fetch(this::mapJob);
    }

    @PostMapping("/projects/{projectId}/render-jobs/{jobId}/deliveries/{deliveryJobId}/retry")
    @Operation(summary = "重试失败的交付任务")
    public Map<String, String> retryDelivery(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String jobId,
            @PathVariable String deliveryJobId) {
        boolean ok = deliveryJobService.retryDelivery(tenantId, projectId, jobId, deliveryJobId);
        return Map.of("deliveryJobId", deliveryJobId, "status", ok ? "COMPLETED" : "RETRYING");
    }

    @PostMapping("/projects/{projectId}/render-jobs/{jobId}/deliver")
    @Operation(summary = "手动触发交付")
    public Map<String, String> triggerDeliver(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String jobId,
            @RequestParam String destinationId) {
        String dlvId = deliveryJobService.triggerManual(tenantId, projectId, jobId, destinationId);
        deliveryJobService.runJob(dlvId);
        return Map.of("deliveryJobId", dlvId);
    }

    private DeliveryJobResponse mapJob(Record r) {
        return new DeliveryJobResponse(
                r.get(field("id", String.class)),
                r.get(field("render_job_id", String.class)),
                r.get(field("destination_id", String.class)),
                r.get(field("status", String.class)),
                r.get(field("source_uri", String.class)),
                r.get(field("remote_uri", String.class)),
                r.get(field("bytes_transferred", Long.class)),
                r.get(field("error_message", String.class)));
    }

    private DeliveryDestinationResponse mapDestinationRow(String tenantId, Record r) {
        String credRef = r.get(field("credential_ref", String.class));
        String credJson = r.get(field("credential_json", String.class));
        return toDestinationResponse(
                r.get(field("id", String.class)),
                tenantId,
                r.get(field("name", String.class)),
                r.get(field("protocol", String.class)),
                Boolean.TRUE.equals(r.get(field("enabled", Boolean.class))),
                credRef,
                credJson);
    }

    private DeliveryDestinationResponse toDestinationResponse(
            String id,
            String tenantId,
            String name,
            String protocol,
            boolean enabled,
            String credentialRef,
            String credentialJson) {
        return new DeliveryDestinationResponse(
                id,
                tenantId,
                name,
                protocol,
                enabled,
                credentialRef,
                credentialBundlePort.hasCredentials(credentialRef, credentialJson));
    }

    private static String toJson(Map<String, ?> map) {
        try {
            return MAPPER.writeValueAsString(map != null ? map : Map.of());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON field");
        }
    }
}
