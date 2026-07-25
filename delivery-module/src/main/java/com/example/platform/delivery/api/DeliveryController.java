package com.example.platform.delivery.api;

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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.web.bind.annotation.*;
import static com.example.platform.typedschema.jooq.generated.tables.DeliveryDestination.DELIVERY_DESTINATION;
import static com.example.platform.typedschema.jooq.generated.tables.DeliveryJob.DELIVERY_JOB;
import static com.example.platform.typedschema.jooq.generated.tables.DeliveryPolicy.DELIVERY_POLICY;


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
        dsl.insertInto(DELIVERY_DESTINATION)
                .columns(DELIVERY_DESTINATION.ID, DELIVERY_DESTINATION.TENANT_ID, DELIVERY_DESTINATION.NAME, DELIVERY_DESTINATION.PROTOCOL,
                        DELIVERY_DESTINATION.CONFIG_JSON, DELIVERY_DESTINATION.CREDENTIAL_REF, DELIVERY_DESTINATION.CREDENTIAL_JSON,
                        DELIVERY_DESTINATION.ENABLED, DELIVERY_DESTINATION.CREATED_AT)
                .values(id, tenantId, request.name(), request.protocol(), configJson,
                        stored.credentialRef(), stored.credentialJson(),
                        request.enabled() != null ? request.enabled() : true, LocalDateTime.now())
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
            dsl.update(DELIVERY_DESTINATION)
                    .set(DELIVERY_DESTINATION.CREDENTIAL_REF, stored.credentialRef())
                    .set(DELIVERY_DESTINATION.CREDENTIAL_JSON, stored.credentialJson())
                    .where(DELIVERY_DESTINATION.ID.eq(destinationId))
                    .execute();
        }
        Record row = dsl.select()
                .from(DELIVERY_DESTINATION)
                .where(DELIVERY_DESTINATION.ID.eq(destinationId))
                .and(DELIVERY_DESTINATION.TENANT_ID.eq(tenantId))
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
                .from(DELIVERY_DESTINATION)
                .where(DELIVERY_DESTINATION.TENANT_ID.eq(tenantId))
                .fetch(r -> mapDestinationRow(tenantId, r));
    }

    @GetMapping("/projects/{projectId}/delivery/policies")
    @Operation(summary = "列出项目交付策略")
    public List<DeliveryPolicyResponse> listPolicies(
            @PathVariable String tenantId,
            @PathVariable String projectId) {
        return dsl.select()
                .from(DELIVERY_POLICY)
                .where(DELIVERY_POLICY.TENANT_ID.eq(tenantId))
                .and(DELIVERY_POLICY.PROJECT_ID.eq(projectId))
                .fetch(r -> new DeliveryPolicyResponse(
                        r.get(DELIVERY_POLICY.ID),
                        tenantId,
                        projectId,
                        r.get(DELIVERY_POLICY.DESTINATION_ID),
                        r.get(DELIVERY_POLICY.ARTIFACT_SELECTOR),
                        r.get(DELIVERY_POLICY.PATH_TEMPLATE),
                        r.get(DELIVERY_POLICY.TRIGGER_MODE),
                        Boolean.TRUE.equals(r.get(DELIVERY_DESTINATION.ENABLED))));
    }

    @PostMapping("/projects/{projectId}/delivery/policies")
    @Operation(summary = "绑定项目自动交付策略")
    public Map<String, String> createPolicy(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @Valid @RequestBody CreateDeliveryPolicyRequest request) {
        String id = Ids.newId("dlp");
        dsl.insertInto(DELIVERY_POLICY)
                .columns(DELIVERY_POLICY.ID, DELIVERY_POLICY.TENANT_ID, DELIVERY_POLICY.PROJECT_ID, DELIVERY_POLICY.DESTINATION_ID,
                        DELIVERY_POLICY.ARTIFACT_SELECTOR, DELIVERY_POLICY.PATH_TEMPLATE, DELIVERY_POLICY.TRIGGER_MODE,
                        DELIVERY_POLICY.ENABLED, DELIVERY_POLICY.CREATED_AT)
                .values(id, tenantId, projectId, request.destinationId(),
                        request.artifactSelectorOrDefault(), request.pathTemplateOrDefault(),
                        request.triggerModeOrDefault(), true, LocalDateTime.now())
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
                .from(DELIVERY_JOB)
                .where(DELIVERY_JOB.TENANT_ID.eq(tenantId))
                .and(DELIVERY_JOB.PROJECT_ID.eq(projectId))
                .and(DELIVERY_JOB.RENDER_JOB_ID.eq(jobId))
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
        return Map.of("deliveryJobId", deliveryJobId, "status", ok ? "COMPLETED" : "PENDING_RETRY");
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
                r.get(DELIVERY_JOB.ID),
                r.get(DELIVERY_JOB.RENDER_JOB_ID),
                r.get(DELIVERY_JOB.DESTINATION_ID),
                r.get(DELIVERY_JOB.STATUS),
                r.get(DELIVERY_JOB.SOURCE_URI),
                r.get(DELIVERY_JOB.REMOTE_URI),
                r.get(DELIVERY_JOB.BYTES_TRANSFERRED),
                r.get(DELIVERY_JOB.ERROR_MESSAGE));
    }

    private DeliveryDestinationResponse mapDestinationRow(String tenantId, Record r) {
        String credRef = r.get(DELIVERY_DESTINATION.CREDENTIAL_REF);
        String credJson = r.get(DELIVERY_DESTINATION.CREDENTIAL_JSON);
        return toDestinationResponse(
                r.get(DELIVERY_DESTINATION.ID),
                tenantId,
                r.get(DELIVERY_DESTINATION.NAME),
                r.get(DELIVERY_DESTINATION.PROTOCOL),
                Boolean.TRUE.equals(r.get(DELIVERY_DESTINATION.ENABLED)),
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
