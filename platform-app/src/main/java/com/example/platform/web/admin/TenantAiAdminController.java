package com.example.platform.web.admin;

import com.example.platform.app.ai.TenantLitellmKeyService;
import com.example.platform.security.AdminAuditHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/tenants/{tenantId}/ai")
@Tag(name = "Tenant AI", description = "Per-tenant LiteLLM virtual key management")
public class TenantAiAdminController {

    private final TenantLitellmKeyService litellmKeyService;
    private final AdminAuditHelper auditHelper;

    public TenantAiAdminController(TenantLitellmKeyService litellmKeyService,
                                    AdminAuditHelper auditHelper) {
        this.litellmKeyService = litellmKeyService;
        this.auditHelper = auditHelper;
    }

    @GetMapping("/litellm-key")
    @Operation(summary = "查看租户 LiteLLM virtual key（掩码）")
    public TenantLitellmKeyService.TenantLitellmKeyView getLitellmKey(@PathVariable String tenantId,
            HttpServletRequest request, HttpServletResponse response) {
        requireAdminRole(request, response, "ADMIN_VIEW_LITELLM_KEY", "litellm_key", tenantId, tenantId);
        TenantLitellmKeyService.TenantLitellmKeyView view = litellmKeyService.getView(tenantId)
                .orElse(new TenantLitellmKeyService.TenantLitellmKeyView(
                        tenantId, null, null, false, "none", null));
        auditHelper.log(request, "ADMIN_VIEW_LITELLM_KEY", "litellm_key", tenantId, tenantId, "SUCCESS");
        return view;
    }

    @PutMapping("/litellm-key")
    @Operation(summary = "设置租户 LiteLLM virtual key")
    public TenantLitellmKeyService.TenantLitellmKeyView upsertLitellmKey(
            @PathVariable String tenantId, @RequestBody UpsertLitellmKeyRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        requireAdminRole(request, response, "ADMIN_SET_LITELLM_KEY", "litellm_key", tenantId, tenantId);
        TenantLitellmKeyService.TenantLitellmKeyView view = litellmKeyService.upsert(
                tenantId, body.virtualKey(), body.keyAlias(), body.enabled() != null ? body.enabled() : true);
        // NOTE: virtualKey is intentionally NOT logged (secret)
        auditHelper.log(request, "ADMIN_SET_LITELLM_KEY", "litellm_key", tenantId, tenantId, "SUCCESS");
        return view;
    }

    @DeleteMapping("/litellm-key")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除租户 virtual key（回退平台 master key）")
    public void deleteLitellmKey(@PathVariable String tenantId,
            HttpServletRequest request, HttpServletResponse response) {
        requireAdminRole(request, response, "ADMIN_DELETE_LITELLM_KEY", "litellm_key", tenantId, tenantId);
        litellmKeyService.remove(tenantId);
        auditHelper.log(request, "ADMIN_DELETE_LITELLM_KEY", "litellm_key", tenantId, tenantId, "SUCCESS");
    }

    private void requireAdminRole(HttpServletRequest request, HttpServletResponse response) {
        requireAdminRole(request, response, "ADMIN_TENANT_AI", "litellm_key", null, null);
    }

    private void requireAdminRole(HttpServletRequest request, HttpServletResponse response,
            String action, String resourceType, String resourceId, String tenantId) {
        // OAuth2 / Spring Security path
        if (request.isUserInRole("ADMIN")) {
            return;
        }
        // Legacy HMAC JWT path: check jwt.roles request attribute
        Object rolesAttr = request.getAttribute("jwt.roles");
        if (rolesAttr instanceof java.util.List<?> roles) {
            if (roles.stream().anyMatch(r -> r != null && "ADMIN".equalsIgnoreCase(r.toString().trim()))) {
                return;
            }
        } else if (rolesAttr instanceof String rolesStr) {
            for (String r : rolesStr.split(",")) {
                if ("ADMIN".equalsIgnoreCase(r.trim())) return;
            }
        }
        auditHelper.logDenied(request, action, resourceType, resourceId, tenantId);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        throw new AccessDeniedException("Admin role required for tenant AI operations");
    }

    public record UpsertLitellmKeyRequest(
            @NotBlank String virtualKey, String keyAlias, Boolean enabled) {}
}
