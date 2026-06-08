package com.example.platform.secrets.infrastructure;

import com.example.platform.secrets.config.SecretsProperties;
import java.net.URI;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.SimpleSessionManager;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.RestTemplateBuilder;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;

/**
 * Builds {@link VaultTemplate} for self-hosted HTTP Vault or HashiCorp Cloud (namespace + AppRole).
 */
public final class VaultClientFactory {

    private final SecretsProperties.Vault config;

    public VaultClientFactory(SecretsProperties properties) {
        this.config = properties.getVault();
    }

    public VaultTemplate createTemplate() {
        VaultEndpoint endpoint = VaultEndpoint.from(URI.create(config.getUri()));
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(config.getConnectionTimeoutMs());
        requestFactory.setReadTimeout(config.getReadTimeoutMs());

        RestTemplateBuilder builder = RestTemplateBuilder.builder()
                .endpoint(endpoint)
                .requestFactory(requestFactory);
        if (config.getNamespace() != null && !config.getNamespace().isBlank()) {
            builder.defaultHeader("X-Vault-Namespace", config.getNamespace().trim());
        }

        ClientAuthentication authentication = createAuthentication(endpoint, requestFactory);
        return new VaultTemplate(builder, new SimpleSessionManager(authentication));
    }

    private ClientAuthentication createAuthentication(
            VaultEndpoint endpoint, SimpleClientHttpRequestFactory requestFactory) {
        String method = config.getAuthMethod() != null ? config.getAuthMethod().trim().toLowerCase() : "token";
        if ("approle".equals(method)) {
            if (config.getRoleId() == null || config.getRoleId().isBlank()) {
                throw new IllegalStateException("app.secrets.vault.role-id is required for approle auth");
            }
            if (config.getSecretId() == null || config.getSecretId().isBlank()) {
                throw new IllegalStateException("app.secrets.vault.secret-id is required for approle auth");
            }
            AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
                    .roleId(AppRoleAuthenticationOptions.RoleId.provided(config.getRoleId()))
                    .secretId(AppRoleAuthenticationOptions.SecretId.provided(config.getSecretId()))
                    .build();
            return new AppRoleAuthentication(
                    options, VaultClients.createRestTemplate(endpoint, requestFactory));
        }
        if (config.getToken() == null || config.getToken().isBlank()) {
            throw new IllegalStateException(
                    "app.secrets.vault.token is required when auth-method=token (or use auth-method=approle)");
        }
        return new TokenAuthentication(config.getToken());
    }
}
