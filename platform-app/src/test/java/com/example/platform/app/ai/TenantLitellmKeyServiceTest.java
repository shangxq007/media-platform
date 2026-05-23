package com.example.platform.app.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.secrets.api.port.SecretRefRegistryPort;
import com.example.platform.secrets.api.port.SecretResolver;
import com.example.platform.secrets.api.port.SecretsConfigPort;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TenantLitellmKeyServiceTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private TenantLitellmKeyService service;
    private TenantLitellmKeyCredentialService credentialService;

    @BeforeEach
    void setUp() throws Exception {
        String db = "litellmkey" + COUNTER.incrementAndGet();
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

        SecretResolver secretResolver = mock(SecretResolver.class);
        SecretRefRegistryPort registry = mock(SecretRefRegistryPort.class);
        SecretsConfigPort secretsConfig = mock(SecretsConfigPort.class);
        when(secretsConfig.inlineCredentialsEnabled()).thenReturn(true);

        credentialService = new TenantLitellmKeyCredentialService(secretResolver, registry, secretsConfig);
        ReflectionTestUtils.setField(credentialService, "tenantKeysVaultBacked", false);

        service = new TenantLitellmKeyService(new TenantLitellmKeyRepository(dsl), credentialService);
        ReflectionTestUtils.setField(service, "platformMasterKey", "sk-master");
        ReflectionTestUtils.setField(service, "tenantVirtualKeysEnabled", true);
    }

    @Test
    void resolvesTenantVirtualKeyWhenPresentInline() {
        service.upsert("tenant-a", "sk-tenant-a", "alias-a", true);
        TenantLitellmKeyService.ResolvedLitellmKey resolved = service.resolveForTenant("tenant-a");
        assertEquals("sk-tenant-a", resolved.apiKey());
        assertTrue(resolved.tenantScoped());
    }

    @Test
    void fallsBackToMasterWhenTenantKeyMissing() {
        TenantLitellmKeyService.ResolvedLitellmKey resolved = service.resolveForTenant("tenant-x");
        assertEquals("sk-master", resolved.apiKey());
        assertFalse(resolved.tenantScoped());
    }

    @Test
    void resolvesFromVaultRefWhenStored() throws Exception {
        SecretResolver secretResolver = mock(SecretResolver.class);
        when(secretResolver.resolve(any())).thenReturn(Optional.of("sk-from-vault"));

        SecretRefRegistryPort registry = mock(SecretRefRegistryPort.class);
        SecretsConfigPort secretsConfig = mock(SecretsConfigPort.class);
        when(secretsConfig.inlineCredentialsEnabled()).thenReturn(true);

        TenantLitellmKeyCredentialService vaultCredentials =
                new TenantLitellmKeyCredentialService(secretResolver, registry, secretsConfig);

        String db = "litellmkeyvault" + COUNTER.incrementAndGet();
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
        TenantLitellmKeyRepository repository = new TenantLitellmKeyRepository(dsl);
        repository.upsert("tenant-v", null, "vault:secret/data/ai-litellm/tenants/t1/litellm", "alias", true);

        TenantLitellmKeyService vaultService = new TenantLitellmKeyService(repository, vaultCredentials);
        ReflectionTestUtils.setField(vaultService, "platformMasterKey", "sk-master");
        ReflectionTestUtils.setField(vaultService, "tenantVirtualKeysEnabled", true);

        TenantLitellmKeyService.ResolvedLitellmKey resolved = vaultService.resolveForTenant("tenant-v");
        assertEquals("sk-from-vault", resolved.apiKey());
        assertEquals("tenant-virtual-key-vault", resolved.source());
    }

    @Test
    void masksKeyForView() {
        assertEquals("sk-1...-xyz", TenantLitellmKeyService.maskKey("sk-12345-xyz"));
    }

    @Test
    void viewShowsVaultStorageBackend() {
        service.upsert("tenant-b", "sk-tenant-b", "alias-b", true);
        var view = service.getView("tenant-b").orElseThrow();
        assertEquals("inline", view.storageBackend());
    }
}
