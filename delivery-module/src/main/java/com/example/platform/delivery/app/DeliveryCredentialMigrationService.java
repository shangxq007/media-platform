package com.example.platform.delivery.app;

import com.example.platform.secrets.api.port.SecretRefRegistryPort;
import com.example.platform.secrets.api.port.SecretResolver;
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
import static com.example.platform.typedschema.jooq.generated.tables.DeliveryDestination.DELIVERY_DESTINATION;


/**
 * Migrates legacy {@code credential_json} on delivery destinations into Vault {@code credential_ref}.
 */
@Service
public class DeliveryCredentialMigrationService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryCredentialMigrationService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DSLContext dsl;
    private final SecretResolver secretResolver;
    private final SecretRefRegistryPort secretRefRegistry;

    public DeliveryCredentialMigrationService(
            DSLContext dsl,
            SecretResolver secretResolver,
            SecretRefRegistryPort secretRefRegistry) {
        this.dsl = dsl;
        this.secretResolver = secretResolver;
        this.secretRefRegistry = secretRefRegistry;
    }

    @Transactional
    public MigrationReport migrateTenant(String tenantId, boolean dryRun) {
        if (!secretResolver.isVaultEnabled()) {
            throw new IllegalStateException("Vault must be enabled for credential migration");
        }
        List<Record> rows = dsl.select()
                .from(DELIVERY_DESTINATION)
                .where(DELIVERY_DESTINATION.TENANT_ID.eq(tenantId))
                .and(DELIVERY_DESTINATION.CREDENTIAL_JSON.isNotNull())
                .and(DELIVERY_DESTINATION.CREDENTIAL_REF.isNull())
                .fetch();
        List<String> migrated = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (Record row : rows) {
            String destinationId = row.get(DELIVERY_DESTINATION.ID);
            String json = row.get(DELIVERY_DESTINATION.CREDENTIAL_JSON);
            try {
                Map<String, String> creds = parseJson(json);
                if (creds.isEmpty()) {
                    skipped.add(destinationId);
                    continue;
                }
                if (!dryRun) {
                    String logicalKey = "tenants/" + tenantId + "/destinations/" + destinationId;
                    String ref = secretResolver.storeCredentialMap("delivery", logicalKey, creds);
                    dsl.update(DELIVERY_DESTINATION)
                            .set(DELIVERY_DESTINATION.CREDENTIAL_REF, ref)
                            .set(DELIVERY_DESTINATION.CREDENTIAL_JSON, (String) null)
                            .where(DELIVERY_DESTINATION.ID.eq(destinationId))
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
