package com.example.platform.secrets.app;

import com.example.platform.secrets.api.port.SecretResolver;
import com.example.platform.secrets.config.SecretsProperties;
import com.example.platform.secrets.infrastructure.EnvSecretProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecretServiceTest {

    private SecretService secretService;

    @BeforeEach
    void setUp() {
        SecretsProperties props = new SecretsProperties();
        SecretResolver resolver = new CompositeSecretResolver(List.of(new EnvSecretProvider()), props);
        secretService = new SecretService(resolver);
    }

    @Test
    void resolveReturnsNullForBlankRef() {
        assertNull(secretService.resolve(null));
        assertNull(secretService.resolve(""));
        assertNull(secretService.resolve("   "));
    }

    @Test
    void resolveReturnsLiteralForNonRefPattern() {
        String value = "plain-value";
        assertEquals(value, secretService.resolve(value));
    }

    @Test
    void resolveReturnsDefaultWhenEnvVarNotSet() {
        String result = secretService.resolve("${NONEXISTENT_VAR_XYZ:default-val}");
        assertEquals("default-val", result);
    }

    @Test
    void resolveReturnsNullWhenNoDefaultAndEnvVarNotSet() {
        String result = secretService.resolve("${NONEXISTENT_VAR_XYZ}");
        assertNull(result);
    }

    @Test
    void resolveWithDefaultValueReturnsDefaultWhenNull() {
        String result = secretService.resolve(null, "fallback");
        assertEquals("fallback", result);
    }

}
