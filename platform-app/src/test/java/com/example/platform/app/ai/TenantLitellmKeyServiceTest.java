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
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class TenantLitellmKeyServiceTest extends PostgresTestContainerSupport {

    private TenantLitellmKeyService service;
    private TenantLitellmKeyCredentialService credentialService;
    private DSLContext dsl;

    @BeforeEach
    void setUp() {
        var ds = new org.springframework.jdbc.datasource.DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(jdbcUrl());
        ds.setUsername(username());
        ds.setPassword(password());
        var jdbc = new JdbcTemplate(ds);
        
        jdbc.execute("CREATE TABLE IF NOT EXISTS tenant_litellm_virtual_key ("
                + "tenant_id VARCHAR(64) PRIMARY KEY,"
                + "virtual_key VARCHAR(512),"
                + "vault_ref VARCHAR(512),"
                + "key_alias VARCHAR(128),"
                + "enabled BOOLEAN NOT NULL,"
                + "created_at TIMESTAMP NOT NULL,"
                + "updated_at TIMESTAMP NOT NULL)");

        dsl = DSL.using(ds, org.jooq.SQLDialect.POSTGRES);

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
