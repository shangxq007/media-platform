package com.example.platform.secrets.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.platform.secrets.api.SecretRef;
import com.example.platform.secrets.api.port.SecretResolver;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CredentialBundleResolverTest {

    @Mock
    private SecretResolver secretResolver;

    private CredentialBundleResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CredentialBundleResolver(secretResolver);
    }

    @Test
    void resolvesFromCredentialRef() {
        when(secretResolver.resolveMap(any(SecretRef.class)))
                .thenReturn(Map.of("username", "u", "password", "p"));
        Map<String, String> creds = resolver.resolve("vault:delivery/tenants/t1/dst_1", null);
        assertEquals("u", creds.get("username"));
    }

    @Test
    void fallsBackToLegacyJson() {
        String json = "{\"username\":\"legacy\"}";
        Map<String, String> creds = resolver.resolve(null, json);
        assertEquals("legacy", creds.get("username"));
    }
}
