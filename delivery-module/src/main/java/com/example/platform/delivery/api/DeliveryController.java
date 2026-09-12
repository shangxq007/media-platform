package com.example.platform.delivery.api;

import com.example.platform.delivery.api.dto.CreateDeliveryDestinationRequest;
import com.example.platform.delivery.api.dto.CreateDeliveryPolicyRequest;
import com.example.platform.delivery.api.dto.DeliveryDestinationResponse;
import com.example.platform.delivery.api.dto.DeliveryJobResponse;
import com.example.platform.delivery.api.dto.DeliveryPolicyResponse;
import com.example.platform.delivery.api.dto.UpdateDeliveryDestinationRequest;
import com.example.platform.delivery.api.dto.UpdateDeliveryPolicyRequest;
import com.example.platform.delivery.app.DeliveryAdministrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{tenantId}")
@Tag(name = "Delivery", description = "渲染成品出站交付")
public class DeliveryController {

    private final DeliveryAdministrationService deliveryAdministrationService;

    public DeliveryController(DeliveryAdministrationService deliveryAdministrationService) {
        this.deliveryAdministrationService = deliveryAdministrationService;
    }

    @PostMapping("/delivery/destinations")
    @Operation(summary = "创建交付目的地")
    public DeliveryDestinationResponse createDestination(
            @PathVariable String tenantId,
            @Valid @RequestBody CreateDeliveryDestinationRequest request) {
        return deliveryAdministrationService.createDestination(tenantId, request);
    }

    @PostMapping("/delivery/destinations/{destinationId}/probe")
    @Operation(summary = "探测交付目的地连通性")
    public Map<String, Object> probeDestination(
            @PathVariable String tenantId,
            @PathVariable String destinationId) {
        var result = deliveryAdministrationService.probeDestination(tenantId, destinationId);
        return Map.of("ok", result.ok(), "message", result.message() != null ? result.message() : "");
    }

    @PatchMapping("/delivery/destinations/{destinationId}")
    @Operation(summary = "更新交付目的地")
    public DeliveryDestinationResponse updateDestination(
            @PathVariable String tenantId,
            @PathVariable String destinationId,
            @RequestBody UpdateDeliveryDestinationRequest request) {
        return deliveryAdministrationService.updateDestination(tenantId, destinationId, request);
    }

    @DeleteMapping("/delivery/destinations/{destinationId}")
    @Operation(summary = "删除交付目的地")
    public Map<String, String> deleteDestination(
            @PathVariable String tenantId,
            @PathVariable String destinationId) {
        deliveryAdministrationService.deleteDestination(tenantId, destinationId);
        return Map.of("destinationId", destinationId, "deleted", "true");
    }

    @GetMapping("/delivery/destinations")
    public List<DeliveryDestinationResponse> listDestinations(@PathVariable String tenantId) {
        return deliveryAdministrationService.listDestinations(tenantId);
    }

    @GetMapping("/projects/{projectId}/delivery/policies")
    @Operation(summary = "列出项目交付策略")
    public List<DeliveryPolicyResponse> listPolicies(
            @PathVariable String tenantId,
            @PathVariable String projectId) {
        return deliveryAdministrationService.listPolicies(tenantId, projectId);
    }

    @PostMapping("/projects/{projectId}/delivery/policies")
    @Operation(summary = "绑定项目自动交付策略")
    public Map<String, String> createPolicy(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @Valid @RequestBody CreateDeliveryPolicyRequest request) {
        return Map.of("policyId", deliveryAdministrationService.createPolicy(tenantId, projectId, request));
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
        deliveryAdministrationService.updatePolicyEnabled(tenantId, projectId, policyId, request.enabled());
        return Map.of("policyId", policyId, "enabled", String.valueOf(request.enabled()));
    }

    @DeleteMapping("/projects/{projectId}/delivery/policies/{policyId}")
    @Operation(summary = "删除交付策略")
    public Map<String, String> deletePolicy(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String policyId) {
        deliveryAdministrationService.deletePolicy(tenantId, projectId, policyId);
        return Map.of("policyId", policyId, "deleted", "true");
    }

    @GetMapping("/projects/{projectId}/render-jobs/{jobId}/deliveries")
    public List<DeliveryJobResponse> listDeliveries(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String jobId) {
        return deliveryAdministrationService.listDeliveries(tenantId, projectId, jobId);
    }

    @PostMapping("/projects/{projectId}/render-jobs/{jobId}/deliveries/{deliveryJobId}/retry")
    @Operation(summary = "重试失败的交付任务")
    public Map<String, String> retryDelivery(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String jobId,
            @PathVariable String deliveryJobId) {
        boolean ok = deliveryAdministrationService.retryDelivery(tenantId, projectId, jobId, deliveryJobId);
        return Map.of("deliveryJobId", deliveryJobId, "status", ok ? "COMPLETED" : "PENDING_RETRY");
    }

    @PostMapping("/projects/{projectId}/render-jobs/{jobId}/deliver")
    @Operation(summary = "手动触发交付")
    public Map<String, String> triggerDeliver(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String jobId,
            @RequestParam String destinationId) {
        return Map.of("deliveryJobId", deliveryAdministrationService.triggerDelivery(
                tenantId, projectId, jobId, destinationId));
    }
}
