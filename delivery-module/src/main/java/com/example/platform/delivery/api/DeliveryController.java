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
import com.example.platform.shared.authorization.FailClosedAuthorization;
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
@RequestMapping("/api/tenants/{tenantId}")
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
        throw FailClosedAuthorization.unavailable("delivery destination creation");
    }

    @PostMapping("/delivery/destinations/{destinationId}/probe")
    @Operation(summary = "探测交付目的地连通性")
    public Map<String, Object> probeDestination(
            @PathVariable String tenantId,
            @PathVariable String destinationId) {
        throw FailClosedAuthorization.unavailable("delivery destination probe");
    }

    @PatchMapping("/delivery/destinations/{destinationId}")
    @Operation(summary = "更新交付目的地")
    public DeliveryDestinationResponse updateDestination(
            @PathVariable String tenantId,
            @PathVariable String destinationId,
            @RequestBody UpdateDeliveryDestinationRequest request) {
        throw FailClosedAuthorization.unavailable("delivery destination update");
    }

    @DeleteMapping("/delivery/destinations/{destinationId}")
    @Operation(summary = "删除交付目的地")
    public Map<String, String> deleteDestination(
            @PathVariable String tenantId,
            @PathVariable String destinationId) {
        throw FailClosedAuthorization.unavailable("delivery destination deletion");
    }

    @GetMapping("/delivery/destinations")
    public List<DeliveryDestinationResponse> listDestinations(@PathVariable String tenantId) {
        throw FailClosedAuthorization.unavailable("delivery destination listing");
    }

    @GetMapping("/projects/{projectId}/delivery/policies")
    @Operation(summary = "列出项目交付策略")
    public List<DeliveryPolicyResponse> listPolicies(
            @PathVariable String tenantId,
            @PathVariable String projectId) {
        throw FailClosedAuthorization.unavailable("delivery policy listing");
    }

    @PostMapping("/projects/{projectId}/delivery/policies")
    @Operation(summary = "绑定项目自动交付策略")
    public Map<String, String> createPolicy(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @Valid @RequestBody CreateDeliveryPolicyRequest request) {
        throw FailClosedAuthorization.unavailable("delivery policy creation");
    }

    @PatchMapping("/projects/{projectId}/delivery/policies/{policyId}")
    @Operation(summary = "启用/禁用交付策略")
    public Map<String, String> updatePolicy(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String policyId,
            @RequestBody UpdateDeliveryPolicyRequest request) {
        throw FailClosedAuthorization.unavailable("delivery policy update");
    }

    @DeleteMapping("/projects/{projectId}/delivery/policies/{policyId}")
    @Operation(summary = "删除交付策略")
    public Map<String, String> deletePolicy(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String policyId) {
        throw FailClosedAuthorization.unavailable("delivery policy deletion");
    }

    @GetMapping("/projects/{projectId}/render-jobs/{jobId}/deliveries")
    public List<DeliveryJobResponse> listDeliveries(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String jobId) {
        throw FailClosedAuthorization.unavailable("delivery job listing");
    }

    @PostMapping("/projects/{projectId}/render-jobs/{jobId}/deliveries/{deliveryJobId}/retry")
    @Operation(summary = "重试失败的交付任务")
    public Map<String, String> retryDelivery(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String jobId,
            @PathVariable String deliveryJobId) {
        throw FailClosedAuthorization.unavailable("delivery retry");
    }

    @PostMapping("/projects/{projectId}/render-jobs/{jobId}/deliver")
    @Operation(summary = "手动触发交付")
    public Map<String, String> triggerDeliver(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String jobId,
            @RequestParam String destinationId) {
        throw FailClosedAuthorization.unavailable("manual delivery execution");
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
