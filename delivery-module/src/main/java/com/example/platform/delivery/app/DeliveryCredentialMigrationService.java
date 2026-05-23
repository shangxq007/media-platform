package com.example.platform.delivery.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.secrets.api.port.SecretResolver;
import com.example.platform.secrets.app.SecretRefRegistryService;
import com.example.platform.secrets.config.SecretsProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Migrates legacy {@code credential_json} on delivery destinations into Vault {@code credential_ref}.
 */
@Service
public class DeliveryCredentialMigrationService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryCredentialMigrationService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DSLContext dsl;
    private final SecretResolver secretResolver;
    private final SecretRefRegistryService secretRefRegistry;
    private final SecretsProperties secretsProperties;

    public DeliveryCredentialMigrationService(
            DSLContext dsl,
            SecretResolver secretResolver,
            SecretRefRegistryService secretRefRegistry,
            SecretsProperties secretsProperties) {
        this.dsl = dsl;
        this.secretResolver = secretResolver;
        this.secretRefRegistry = secretRefRegistry;
        this.secretsProperties = secretsProperties;
    }

    @Transactional
    public MigrationReport migrateTenant(String tenantId, boolean dryRun) {
        if (!secretResolver.isVaultEnabled()) {
            throw new IllegalStateException("Vault must be enabled for credential migration");
        }
        List<Record> rows = dsl.select()
                .from(table("delivery_destination"))
                .where(field("tenant_id").eq(tenantId))
                .and(field("credential_json").isNotNull())
                .and(field("credential_ref").isNull())
                .fetch();
        List<String> migrated = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (Record row : rows) {
            String destinationId = row.get(field("id", String.class));
            String json = row.get(field("credential_json", String.class));
            try {
                Map<String, String> creds = parseJson(json);
                if (creds.isEmpty()) {
                    skipped.add(destinationId);
                    continue;
                }
                if (!dryRun) {
                    String logicalKey = "tenants/" + tenantId + "/destinations/" + destinationId;
                    String ref = secretResolver.storeCredentialMap("delivery", logicalKey, creds);
                    dsl.update(table("delivery_destination"))
                            .set(field("credential_ref"), ref)
                            .set(field("credential_json"), (String) null)
                            .where(field("id").eq(destinationId))
                            .execute();
                    secretRefRegistry.register("delivery", destinationId, "vault", ref);
                }
                migrated.add(destinationId);
            } catch (Exception e) {
                log.warn("Migration failed for destination {}: {}", destinationId, e.getMessage());
                failed.add(destinationId + ": " + e.getMessage());
            }
        }
        return new MigrationReport(tenantId, dryRun, migrated.size(), skipped.size(), failed.size(), migrated, skipped, failed);
    }

    private Map<String, String> parseJson(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, String> map = MAPPER.readValue(json, new TypeReference<>() {});
        return map != null ? map : Map.of();
    }

    public record MigrationReport(
            String tenantId,
            boolean dryRun,
            int migrated,
            int skipped,
            int failed,
            List<String> migratedIds,
            List<String> skippedIds,
            List<String> errors) {}
}
