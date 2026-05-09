package com.example.platform.datasource;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "app.datasource")
public class AppDataSourceProperties {

    private boolean federationEnabled;
    private Map<String, NamedDataSourceProperties> sources = new LinkedHashMap<>();

    public boolean isFederationEnabled() {
        return federationEnabled;
    }

    public void setFederationEnabled(boolean federationEnabled) {
        this.federationEnabled = federationEnabled;
    }

    public Map<String, NamedDataSourceProperties> getSources() {
        return sources;
    }

    public void setSources(Map<String, NamedDataSourceProperties> sources) {
        this.sources = sources;
    }
}
