package com.example.platform.web.admin;

import com.example.platform.app.ai.TenantLitellmKeyService;
import com.example.platform.app.ai.TenantLitellmKeyVaultMigrationService;
import com.example.platform.render.infrastructure.RenderCacheProperties;
import com.example.platform.storage.infrastructure.StorageS3Properties;
import com.example.platform.workflow.temporal.AppTemporalProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pre-deploy / post-deploy readiness snapshot (Temporal namespace, R2 cache, storage).
 */
@RestController
@RequestMapping("/api/v1/admin/platform")
@Tag(name = "Platform Admin", description = "部署就绪与集成验收")
public class PlatformDeploymentReadinessController {

    private final StorageS3Properties storageS3Properties;
    private final RenderCacheProperties renderCacheProperties;
    private final AppTemporalProperties temporalProperties;
    private final TenantLitellmKeyService litellmKeyService;
    private final TenantLitellmKeyVaultMigrationService litellmKeyVaultMigrationService;

    @Value("${platform.environment:dev}")
    private String platformEnvironment;

    @Value("${app.ai.providers.openai.enabled:false}")
    private boolean openAiProviderEnabled;

    public PlatformDeploymentReadinessController(
            StorageS3Properties storageS3Properties,
            RenderCacheProperties renderCacheProperties,
            TenantLitellmKeyService litellmKeyService,
            TenantLitellmKeyVaultMigrationService litellmKeyVaultMigrationService,
            @Autowired(required = false) AppTemporalProperties temporalProperties) {
        this.storageS3Properties = storageS3Properties;
        this.renderCacheProperties = renderCacheProperties;
        this.litellmKeyService = litellmKeyService;
        this.litellmKeyVaultMigrationService = litellmKeyVaultMigrationService;
        this.temporalProperties = temporalProperties != null ? temporalProperties : new AppTemporalProperties();
    }

    @PostMapping("/migrate/litellm-keys-to-vault")
    @Operation(summary = "迁移 LiteLLM 租户密钥到 Vault", description = "将 DB 明文 virtual_key 写入 Vault 并改为 vault_ref；先 dryRun=true 预检")
    public TenantLitellmKeyVaultMigrationService.MigrationReport migrateLitellmKeysToVault(
            @RequestParam(defaultValue = "true") boolean dryRun) {
        return litellmKeyVaultMigrationService.migrateInlineKeysToVault(dryRun);
    }

    @GetMapping("/readiness")
    @Operation(summary = "部署就绪检查", description = "返回 Temporal / S3 R2 / 段缓存配置快照，供发布前清单勾选")
    public ReadinessReport readiness() {
        Map<String, Object> temporal = new LinkedHashMap<>();
        temporal.put("enabled", temporalProperties.isEnabled());
        temporal.put("namespace", temporalProperties.resolveNamespace());
        temporal.put("taskQueue", temporalProperties.getTaskQueue());
        temporal.put("workerRequired", temporalProperties.isWorkerRequired());
        temporal.put("executionModeHint", temporalProperties.isEnabled() ? "temporal" : "local");

        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("s3Enabled", storageS3Properties.isEnabled());
        storage.put("compatibility", storageS3Properties.getCompatibility().name());
        storage.put("defaultBucket", storageS3Properties.getDefaultBucket());

        Map<String, Object> renderCache = new LinkedHashMap<>();
        renderCache.put("remoteEnabled", renderCacheProperties.isRemoteEnabled());
        renderCache.put("uploadEnabled", renderCacheProperties.isUploadEnabled());
        renderCache.put("cleanupEnabled", renderCacheProperties.isCleanupEnabled());
        renderCache.put("retentionDays", renderCacheProperties.getRetentionDays());

        Map<String, Object> ai = new LinkedHashMap<>();
        ai.put("openaiProviderEnabled", openAiProviderEnabled);
        ai.put("tenantVirtualKeysEnabled", litellmKeyService.isTenantVirtualKeysEnabled());
        ai.put("tenantKeysVaultBacked", litellmKeyService.isTenantKeysVaultBacked());
        ai.put("vaultAvailableForLitellmKeys", litellmKeyService.isVaultAvailable());

        boolean storageReady = storageS3Properties.isEnabled();
        boolean cacheReady = renderCacheProperties.isRemoteEnabled() && renderCacheProperties.isUploadEnabled();
        boolean temporalReady = !temporalProperties.isEnabled() || temporalProperties.isEnabled();
        boolean aiReady = !openAiProviderEnabled || litellmKeyService.isTenantVirtualKeysEnabled();

        return new ReadinessReport(
                platformEnvironment,
                temporal,
                storage,
                renderCache,
                ai,
                new Checks(storageReady, cacheReady, temporalReady, aiReady));
    }

    public record Checks(boolean storageS3, boolean renderCacheRemote, boolean temporalConfigured, boolean aiLitellm) {}

    public record ReadinessReport(
            String platformEnvironment,
            Map<String, Object> temporal,
            Map<String, Object> storage,
            Map<String, Object> renderCache,
            Map<String, Object> ai,
            Checks checks) {}
}
