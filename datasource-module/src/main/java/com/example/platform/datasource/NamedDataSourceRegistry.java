package com.example.platform.datasource;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;

public class NamedDataSourceRegistry {
    private final Map<String, DataSource> dataSources;

    public NamedDataSourceRegistry(Map<String, DataSource> dataSources) {
        this.dataSources = dataSources;
    }

    public Optional<DataSource> get(String name) {
        return Optional.ofNullable(dataSources.get(name));
    }

    public Map<String, DataSource> all() {
        return dataSources;
    }
}
