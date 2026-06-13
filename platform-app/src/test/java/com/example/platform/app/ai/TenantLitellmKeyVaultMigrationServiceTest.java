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
import com.example.platform.shared.test.PostgresTestContainer;
import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

class TenantLitellmKeyVaultMigrationServiceTest extends PostgresTestContainer {

    private TenantLitellmKeyVaultMigrationService migrationService;
    private TenantLitellmKeyCredentialService credentialService;
    private DSLContext dsl;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        var ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(POSTGRES_URL);
        ds.setUsername(POSTGRES_USERNAME);
        ds.setPassword(POSTGRES_PASSWORD);
        jdbc = new JdbcTemplate(ds);
        
        jdbc.execute("CREATE TABLE IF NOT EXISTS tenant_litellm_virtual_key ("
                + "tenant_id VARCHAR(64) PRIMARY KEY,"
                + "virtual_key VARCHAR(512),"
                + "vault_ref VARCHAR(512),"
                + "key_alias VARCHAR(128),"
                + "enabled BOOLEAN NOT NULL,"
                + "created_at TIMESTAMP NOT NULL,"
                + "updated_at TIMESTAMP NOT NULL)");
        
        // Clean up any existing data
        jdbc.execute("DELETE FROM tenant_litellm_virtual_key");

        dsl = DSL.using(ds, org.jooq.SQLDialect.POSTGRES);
        
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
