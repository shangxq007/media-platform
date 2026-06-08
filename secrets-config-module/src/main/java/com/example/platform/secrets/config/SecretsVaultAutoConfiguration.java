package com.example.platform.secrets.config;

import com.example.platform.secrets.infrastructure.VaultClientFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.core.VaultTemplate;

@Configuration
@ConditionalOnProperty(prefix = "app.secrets.vault", name = "enabled", havingValue = "true")
public class SecretsVaultAutoConfiguration {

    @Bean
    public VaultClientFactory vaultClientFactory(SecretsProperties properties) {
        return new VaultClientFactory(properties);
    }

    @Bean
    public VaultTemplate vaultTemplate(VaultClientFactory vaultClientFactory) {
        return vaultClientFactory.createTemplate();
    }
}
