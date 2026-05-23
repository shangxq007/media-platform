package com.example.platform.app.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.secrets.api.port.SecretRefRegistryPort;
import com.example.platform.secrets.api.port.SecretResolver;
import com.example.platform.secrets.api.port.SecretsConfigPort;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TenantLitellmKeyCredentialServiceTest {

    private SecretResolver secretResolver;
    private SecretRefRegistryPort registry;
    private SecretsConfigPort secretsConfig;
    private TenantLitellmKeyCredentialService service;

    @BeforeEach
    void setUp() {
        secretResolver = mock(SecretResolver.class);
        registry = mock(SecretRefRegistryPort.class);
        secretsConfig = mock(SecretsConfigPort.class);
        when(secretsConfig.inlineCredentialsEnabled()).thenReturn(true);
        when(secretsConfig.vaultEnabled()).thenReturn(false);
        service = new TenantLitellmKeyCredentialService(secretResolver, registry, secretsConfig);
        ReflectionTestUtils.setField(service, "tenantKeysVaultBacked", false);
    }

    @Test
    void persistInlineWhenVaultBackedDisabled() {
        var stored = service.persist("ten-1", "sk-abc", null);
        assertEquals("sk-abc", stored.inlineVirtualKey());
        assertEquals(TenantLitellmKeyCredentialService.StorageBackend.INLINE, stored.storageBackend());
    }

    @Test
    void persistToVaultWhenVaultBackedEnabled() {
        when(secretsConfig.vaultEnabled()).thenReturn(true);
        ReflectionTestUtils.setField(service, "tenantKeysVaultBacked", true);
        when(secretResolver.storeCredentialMap(eq("ai-litellm"), eq("tenants/ten-2/litellm"), any()))
                .thenReturn("vault:secret/data/platform/ai-litellm/tenants/ten-2/litellm");

        var stored = service.persist("ten-2", "sk-vault", null);

        assertEquals(TenantLitellmKeyCredentialService.StorageBackend.VAULT, stored.storageBackend());
        assertTrue(stored.vaultRef().startsWith("vault:"));
        verify(registry).register(eq("ai-litellm"), eq("ten-2"), eq("vault"), any());
    }

    @Test
    void vaultBackedRequiresVaultEnabled() {
        ReflectionTestUtils.setField(service, "tenantKeysVaultBacked", true);
        assertThrows(IllegalStateException.class, () -> service.persist("ten-3", "sk-x", null));
    }

    @Test
    void resolveFromVaultRef() {
        when(secretResolver.resolveMap(any())).thenReturn(Map.of("virtualKey", "sk-resolved"));
        Optional<String> key = service.resolveVirtualKey(null, "vault:secret/data/x");
        assertEquals("sk-resolved", key.orElseThrow());
    }
}
