package com.example.platform.secrets.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.example.platform.secrets.api.SecretRef;
import com.example.platform.secrets.api.port.SecretProvider;
import com.example.platform.secrets.config.SecretsProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompositeSecretResolverDeletionTest {
    @Test void actualVaultAdapterDoesNotSwallowExternalDeleteFailure() {
        var vault = mock(org.springframework.vault.core.VaultTemplate.class, RETURNS_DEEP_STUBS);
        var properties = new SecretsProperties();
        var operations = vault.opsForVersionedKeyValue(properties.getVault().getKvMount());
        doThrow(new IllegalStateException("external client failure")).when(operations).delete("test-only/ref");
        var provider = new com.example.platform.secrets.infrastructure.VaultKv2SecretProvider(vault, properties);
        var resolver = new CompositeSecretResolver(List.of(provider), properties);
        assertThrows(IllegalStateException.class, () -> resolver.deleteByRef("vault:test-only/ref"));
    }
    @Test void unsupportedDeletionMustNotReportSuccess() {
        SecretProvider provider = mock(SecretProvider.class);
        when(provider.supports(any())).thenReturn(true);
        var resolver = new CompositeSecretResolver(List.of(provider), new SecretsProperties());
        assertThrows(IllegalStateException.class, () -> resolver.deleteByRef("vault:test-only/ref"));
        verify(provider, never()).delete(any());
    }
    @Test void providerFailurePropagatesAndSupportedDeletionExecutes() {
        SecretProvider provider = mock(SecretProvider.class);
        when(provider.supports(any())).thenReturn(true);
        when(provider.canDelete(any())).thenReturn(true);
        var resolver = new CompositeSecretResolver(List.of(provider), new SecretsProperties());
        resolver.deleteByRef("vault:test-only/ref");
        verify(provider).delete(SecretRef.parse("vault:test-only/ref"));
        doThrow(new IllegalStateException("test failure")).when(provider).delete(any());
        assertThrows(IllegalStateException.class, () -> resolver.deleteByRef("vault:test-only/ref"));
    }
}
