package com.example.platform.secrets.config;

import com.example.platform.secrets.api.port.SecretsConfigPort;
import org.springframework.stereotype.Component;

@Component
public class SecretsConfigPortAdapter implements SecretsConfigPort {

    private final SecretsProperties properties;

    public SecretsConfigPortAdapter(SecretsProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean vaultEnabled() {
        return properties.getVault().isEnabled();
    }

    @Override
    public boolean inlineCredentialsEnabled() {
        return properties.isInlineCredentialsEnabled();
    }
}
