package com.example.platform.secrets.api.port;

/** Read-only secrets runtime configuration for business modules. */
public interface SecretsConfigPort {

    boolean vaultEnabled();

    boolean inlineCredentialsEnabled();
}
