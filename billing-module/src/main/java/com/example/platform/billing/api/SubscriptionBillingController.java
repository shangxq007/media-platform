package com.example.platform.billing.api;

import com.example.platform.billing.api.dto.*;
import com.example.platform.billing.app.SubscriptionBillingService;
import com.example.platform.billing.domain.SubscriptionContract;
import com.example.platform.billing.domain.SubscriptionPlan;
import com.example.platform.shared.audit.AdminAuditPublisher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SubscriptionBillingController {

    private final SubscriptionBillingService subscriptionBillingService;
    private final AdminAuditPublisher auditPublisher;

    public SubscriptionBillingController(SubscriptionBillingService subscriptionBillingService,
                                          AdminAuditPublisher auditPublisher) {
        this.subscriptionBillingService = subscriptionBillingService;
        this.auditPublisher = auditPublisher;
    }

    @PostMapping("/admin/billing/plans")
    public SubscriptionPlanResponse createPlan(@RequestBody CreatePlanRequest request,
            HttpServletRequest httpRequest) {
        requireAdminRole(httpRequest, "ADMIN_CREATE_BILLING_PLAN", "billing_plan", request.planKey(), null);
        auditPublisher.publish(
                extractActor(httpRequest), extractRoles(httpRequest),
                "ADMIN_CREATE_BILLING_PLAN", "billing_plan", request.planKey(), null, "SUCCESS");
        SubscriptionPlan plan = subscriptionBillingService.createPlan(
                request.planKey(), request.name(), request.description(),
                request.billingInterval(), request.basePriceMinor(),
                request.currencyCode(), request.includedQuota());
        return toPlanResponse(plan);
    }

    @GetMapping("/admin/billing/plans")
    public List<SubscriptionPlanResponse> listPlans(HttpServletRequest httpRequest) {
        requireAdminRole(httpRequest, "ADMIN_LIST_BILLING_PLANS", "billing_plan", null, null);
        auditPublisher.publish(
                extractActor(httpRequest), extractRoles(httpRequest),
                "ADMIN_LIST_BILLING_PLANS", "billing_plan", null, null, "SUCCESS");
        return subscriptionBillingService.listPlans().stream()
                .map(this::toPlanResponse)
                .toList();
    }

    @PostMapping("/billing/subscriptions")
    public SubscriptionResponse createSubscription(@RequestBody CreateSubscriptionRequest request) {
        String tenantId = resolveTenantId(request.tenantId());
        SubscriptionContract contract = subscriptionBillingService.createSubscription(
                tenantId, request.userId(), request.planKey(), request.periodDays());
        return toSubscriptionResponse(contract);
    }

    @GetMapping("/billing/subscriptions/current")
    public SubscriptionResponse getCurrentSubscription(
            @RequestParam(required = false) String tenantId,
            @RequestParam String userId) {
        String effectiveTenant = resolveTenantId(tenantId);
        SubscriptionContract contract = subscriptionBillingService.getCurrentSubscription(effectiveTenant, userId);
        if (contract == null) {
            return null;
        }
        return toSubscriptionResponse(contract);
    }

    @GetMapping("/billing/subscriptions/active")
    public List<SubscriptionResponse> listActiveSubscriptions(
            @RequestParam(required = false) String tenantId,
            @RequestParam String userId) {
        String effectiveTenant = resolveTenantId(tenantId);
        return subscriptionBillingService.listActiveSubscriptions(effectiveTenant, userId).stream()
                .map(this::toSubscriptionResponse)
                .toList();
    }

    @GetMapping("/billing/subscriptions/effective-quota")
    public Map<String, Long> getEffectiveIncludedQuota(
            @RequestParam(required = false) String tenantId,
            @RequestParam String userId) {
        String effectiveTenant = resolveTenantId(tenantId);
        return subscriptionBillingService.getEffectiveIncludedQuota(effectiveTenant, userId);
    }

    @PostMapping("/billing/subscriptions/change-plan")
    public SubscriptionResponse changePlan(@RequestBody ChangePlanRequest request) {
        SubscriptionContract contract = subscriptionBillingService.changePlan(
                request.contractId(), request.newPlanKey(), request.periodDays());
        return toSubscriptionResponse(contract);
    }

    @PostMapping("/billing/subscriptions/cancel")
    public SubscriptionResponse cancel(@RequestBody CancelSubscriptionRequest request) {
        SubscriptionContract contract = subscriptionBillingService.cancelAtPeriodEnd(request.contractId());
        return toSubscriptionResponse(contract);
    }

    private String resolveTenantId(String requestedTenantId) {
        String contextTenant = com.example.platform.shared.web.TenantContext.get();
        if (contextTenant == null || contextTenant.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        if (requestedTenantId != null && !requestedTenantId.isBlank()
                && !requestedTenantId.equals(contextTenant)) {
            throw new IllegalArgumentException("Tenant ID does not match authenticated tenant");
        }
        return contextTenant;
    }

    private void requireAdminRole(HttpServletRequest request) {
        requireAdminRole(request, "ADMIN_BILLING_OPERATION", "billing", null, null);
    }

    private void requireAdminRole(HttpServletRequest request, String action,
            String resourceType, String resourceId, String tenantId) {
        if (request.isUserInRole("ADMIN")) {
            return;
        }
        if (hasRoleFromRequestAttribute(request, "ADMIN")) {
            return;
        }
        auditPublisher.publish(
                extractActor(request), extractRoles(request),
                action, resourceType, resourceId, tenantId, "DENIED");
        throw new SecurityException("Admin role required for this operation");
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

    private SubscriptionPlanResponse toPlanResponse(SubscriptionPlan plan) {
        return new SubscriptionPlanResponse(
                plan.planId(), plan.planKey(), plan.name(), plan.description(),
                plan.billingInterval(), plan.basePriceMinor(), plan.currencyCode(),
                plan.includedQuota(), plan.status());
    }

    private SubscriptionResponse toSubscriptionResponse(SubscriptionContract contract) {
        return new SubscriptionResponse(
                contract.contractId(), contract.tenantId(), contract.userId(),
                contract.planKey(), contract.periodStartAt(), contract.periodEndAt(),
                contract.lifecycleState(), contract.basePriceMinor(),
                contract.currencyCode(),
                contract.contractRole().name(),
                contract.productCode());
    }
}
