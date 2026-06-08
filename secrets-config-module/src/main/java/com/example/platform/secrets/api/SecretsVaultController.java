package com.example.platform.secrets.api;

import com.example.platform.secrets.config.SecretsProperties;
import com.example.platform.secrets.infrastructure.VaultKv2SecretProvider;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/secrets/vault")
@ConditionalOnProperty(prefix = "app.secrets.vault", name = "enabled", havingValue = "true")
public class SecretsVaultController {

    private final SecretsProperties properties;
    private final Optional<VaultKv2SecretProvider> vaultProvider;

    public SecretsVaultController(
            SecretsProperties properties,
            @Autowired(required = false) VaultKv2SecretProvider vaultProvider) {
        this.properties = properties;
        this.vaultProvider = Optional.ofNullable(vaultProvider);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        boolean ok = vaultProvider.map(VaultKv2SecretProvider::probe).orElse(false);
        return Map.of(
                "enabled", true,
                "uri", properties.getVault().getUri(),
                "authMethod", properties.getVault().getAuthMethod(),
                "namespace", properties.getVault().getNamespace() != null
                        ? properties.getVault().getNamespace()
                        : "",
                "kvMount", properties.getVault().getKvMount(),
                "healthy", ok);
    }
}
