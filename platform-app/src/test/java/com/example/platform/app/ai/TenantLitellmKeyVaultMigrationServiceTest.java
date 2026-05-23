package com.example.platform.app.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.secrets.api.port.SecretRefRegistryPort;
import com.example.platform.secrets.api.port.SecretResolver;
import com.example.platform.secrets.api.port.SecretsConfigPort;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TenantLitellmKeyVaultMigrationServiceTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private TenantLitellmKeyVaultMigrationService migrationService;
    private TenantLitellmKeyCredentialService credentialService;

    @BeforeEach
    void setUp() throws Exception {
        String db = "litellmmig" + COUNTER.incrementAndGet();
        Connection conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + db + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        DSLContext dsl = DSL.using(conn, org.jooq.SQLDialect.H2);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("create table tenant_litellm_virtual_key ("
                    + "tenant_id varchar(64) primary key,"
                    + "virtual_key varchar(512),"
                    + "vault_ref varchar(512),"
                    + "key_alias varchar(128),"
                    + "enabled boolean not null,"
                    + "created_at timestamp not null,"
                    + "updated_at timestamp not null)");
        }
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(DSL.table("tenant_litellm_virtual_key"))
                .columns(
                        DSL.field("tenant_id"),
                        DSL.field("virtual_key"),
                        DSL.field("vault_ref"),
                        DSL.field("key_alias"),
                        DSL.field("enabled"),
                        DSL.field("created_at"),
                        DSL.field("updated_at"))
                .values("ten-inline", "sk-inline", null, "alias", true, now, now)
                .execute();

        SecretResolver secretResolver = mock(SecretResolver.class);
        when(secretResolver.storeCredentialMap(eq("ai-litellm"), eq("tenants/ten-inline/litellm"), any()))
                .thenReturn("vault:secret/data/platform/ai-litellm/tenants/ten-inline/litellm");

        SecretsConfigPort secretsConfig = mock(SecretsConfigPort.class);
        when(secretsConfig.vaultEnabled()).thenReturn(true);
        when(secretsConfig.inlineCredentialsEnabled()).thenReturn(false);
        credentialService = new TenantLitellmKeyCredentialService(
                secretResolver, mock(SecretRefRegistryPort.class), secretsConfig);
        ReflectionTestUtils.setField(credentialService, "tenantKeysVaultBacked", true);

        migrationService = new TenantLitellmKeyVaultMigrationService(
                new TenantLitellmKeyRepository(dsl), credentialService);
    }

    @Test
    void dryRunDoesNotWriteVault() {
        var report = migrationService.migrateInlineKeysToVault(true);
        assertEquals(1, report.migratedCount());
        assertEquals("ten-inline", report.migratedTenantIds().get(0));
    }

    @Test
    void migrateWritesVaultRef() {
        var report = migrationService.migrateInlineKeysToVault(false);
        assertEquals(1, report.migratedCount());
        assertEquals(0, report.failedCount());
    }

    @Test
    void requiresVaultBackedMode() {
        ReflectionTestUtils.setField(credentialService, "tenantKeysVaultBacked", false);
        assertThrows(IllegalStateException.class, () -> migrationService.migrateInlineKeysToVault(true));
    }
}
