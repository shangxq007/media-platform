package com.example.platform.web.admin;

import com.example.platform.app.ai.TenantLitellmKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
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

    public TenantAiAdminController(TenantLitellmKeyService litellmKeyService) {
        this.litellmKeyService = litellmKeyService;
    }

    @GetMapping("/litellm-key")
    @Operation(summary = "查看租户 LiteLLM virtual key（掩码）")
    public TenantLitellmKeyService.TenantLitellmKeyView getLitellmKey(@PathVariable String tenantId) {
        return litellmKeyService.getView(tenantId).orElse(new TenantLitellmKeyService.TenantLitellmKeyView(
                tenantId, null, null, false, "none", null));
    }

    @PutMapping("/litellm-key")
    @Operation(summary = "设置租户 LiteLLM virtual key")
    public TenantLitellmKeyService.TenantLitellmKeyView upsertLitellmKey(
            @PathVariable String tenantId, @RequestBody UpsertLitellmKeyRequest body) {
        return litellmKeyService.upsert(
                tenantId, body.virtualKey(), body.keyAlias(), body.enabled() != null ? body.enabled() : true);
    }

    @DeleteMapping("/litellm-key")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除租户 virtual key（回退平台 master key）")
    public void deleteLitellmKey(@PathVariable String tenantId) {
        litellmKeyService.remove(tenantId);
    }

    public record UpsertLitellmKeyRequest(
            @NotBlank String virtualKey, String keyAlias, Boolean enabled) {}
}
