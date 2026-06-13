package com.example.platform.datasource;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceConfigurationTest extends PostgresTestContainerSupport {

    private static String postgresUrl;

    @BeforeAll
    static void setUp() {
        postgresUrl = jdbcUrl();
    }

    private AppDataSourceProperties buildProperties(String name, String url, String dialect, boolean primary) {
        NamedDataSourceProperties props = new NamedDataSourceProperties();
        props.setKind("jdbc");
        props.setUrl(url);
        props.setUsername(username());
        props.setPassword(password());
        props.setDialect(dialect);
        props.setPrimary(primary);

        AppDataSourceProperties properties = new AppDataSourceProperties();
        Map<String, NamedDataSourceProperties> sources = new LinkedHashMap<>();
        sources.put(name, props);
        properties.setSources(sources);
        properties.setFederationEnabled(false);
        return properties;
    }

    @Test
    void namedDataSourceRegistryContainsConfiguredSource() {
        AppDataSourceProperties properties = buildProperties("primary", postgresUrl, "postgres", true);

        DataSourceConfiguration config = new DataSourceConfiguration();
        NamedDataSourceRegistry registry = config.namedDataSourceRegistry(properties);

        assertThat(registry.get("primary")).isPresent();
        assertThat(registry.all()).hasSize(1);
    }

    @Test
    void namedDataSourceRegistryReturnsEmptyForUnknownSource() {
        AppDataSourceProperties properties = buildProperties("primary", postgresUrl, "postgres", true);

        DataSourceConfiguration config = new DataSourceConfiguration();
        NamedDataSourceRegistry registry = config.namedDataSourceRegistry(properties);

        assertThat(registry.get("nonexistent")).isEmpty();
    }

    @Test
    void namedDataSourceRegistryIgnoresNonJdbcKinds() {
        NamedDataSourceProperties nonJdbc = new NamedDataSourceProperties();
        nonJdbc.setKind("noop");
        nonJdbc.setPrimary(false);

        NamedDataSourceProperties jdbc = new NamedDataSourceProperties();
        jdbc.setKind("jdbc");
        jdbc.setUrl(postgresUrl);
        jdbc.setUsername(username());
        jdbc.setPassword(password());
        jdbc.setDialect("postgres");
        jdbc.setPrimary(true);

        AppDataSourceProperties properties = new AppDataSourceProperties();
        Map<String, NamedDataSourceProperties> sources = new LinkedHashMap<>();
        sources.put("noop-source", nonJdbc);
        sources.put("jdbc-source", jdbc);
        properties.setSources(sources);

        DataSourceConfiguration config = new DataSourceConfiguration();
        NamedDataSourceRegistry registry = config.namedDataSourceRegistry(properties);

        assertThat(registry.all()).containsOnlyKeys("jdbc-source");
    }

    @Test
    void dslContextRegistryCreatesContextsForAllDataSources() {
        AppDataSourceProperties properties = buildProperties("primary", postgresUrl, "postgres", true);

        DataSourceConfiguration config = new DataSourceConfiguration();
        NamedDataSourceRegistry registry = config.namedDataSourceRegistry(properties);
        DslContextRegistry dslRegistry = config.dslContextRegistry(registry, properties);

        assertThat(dslRegistry.get("primary")).isPresent();
        assertThat(dslRegistry.all()).hasSize(1);
    }

    @Test
    void dslContextRegistryReturnsEmptyForUnknownContext() {
        AppDataSourceProperties properties = buildProperties("primary", postgresUrl, "postgres", true);

        DataSourceConfiguration config = new DataSourceConfiguration();
        NamedDataSourceRegistry registry = config.namedDataSourceRegistry(properties);
        DslContextRegistry dslRegistry = config.dslContextRegistry(registry, properties);

        assertThat(dslRegistry.get("nonexistent")).isEmpty();
    }

    @Test
    void primaryDataSourceBeanIsNotNull() {
        AppDataSourceProperties properties = buildProperties("primary", postgresUrl, "postgres", true);

        DataSourceConfiguration config = new DataSourceConfiguration();
        NamedDataSourceRegistry registry = config.namedDataSourceRegistry(properties);

        DataSource ds = registry.get("primary").orElseThrow();
        assertThat(ds).isNotNull();
    }
}
