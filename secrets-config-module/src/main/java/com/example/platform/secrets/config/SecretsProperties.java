package com.example.platform.secrets.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.secrets")
public class SecretsProperties {

    private Vault vault = new Vault();
    /** Allow storing credentials in DB column {@code credential_json} when Vault is off (dev only). */
    private boolean inlineCredentialsEnabled = true;

    public Vault getVault() {
        return vault;
    }

    public void setVault(Vault vault) {
        this.vault = vault;
    }

    public boolean isInlineCredentialsEnabled() {
        return inlineCredentialsEnabled;
    }

    public void setInlineCredentialsEnabled(boolean inlineCredentialsEnabled) {
        this.inlineCredentialsEnabled = inlineCredentialsEnabled;
    }

    public static class Vault {
        private boolean enabled;
        /** Self-hosted HTTP(S) or HCP Vault API base URL. */
        private String uri = "http://127.0.0.1:8200";
        /** {@code token} (dev/ops) or {@code approle} (production). */
        private String authMethod = "token";
        private String token = "";
        private String roleId = "";
        private String secretId = "";
        /** HCP Vault namespace header; empty for open-source single-tenant. */
        private String namespace = "";
        private String kvMount = "secret";
        private String pathPrefix = "media-platform";
        private int connectionTimeoutMs = 5_000;
        private int readTimeoutMs = 15_000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getAuthMethod() {
            return authMethod;
        }

        public void setAuthMethod(String authMethod) {
            this.authMethod = authMethod;
        }

        public String getRoleId() {
            return roleId;
        }

        public void setRoleId(String roleId) {
            this.roleId = roleId;
        }

        public String getSecretId() {
            return secretId;
        }

        public void setSecretId(String secretId) {
            this.secretId = secretId;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public int getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(int connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public String getKvMount() {
            return kvMount;
        }

        public void setKvMount(String kvMount) {
            this.kvMount = kvMount;
        }

        public String getPathPrefix() {
            return pathPrefix;
        }

        public void setPathPrefix(String pathPrefix) {
            this.pathPrefix = pathPrefix;
        }
    }
}
