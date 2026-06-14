package com.example.platform.datasource;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(AppDataSourceProperties.class)
public class DataSourceConfiguration {

    @Bean
    public NamedDataSourceRegistry namedDataSourceRegistry(AppDataSourceProperties properties) {
        Map<String, DataSource> map = new LinkedHashMap<>();
        properties.getSources().forEach((name, spec) -> {
            if ("jdbc".equalsIgnoreCase(spec.getKind())) {
                DriverManagerDataSource ds = new DriverManagerDataSource();
                ds.setUrl(spec.getUrl());
                ds.setUsername(spec.getUsername());
                ds.setPassword(spec.getPassword());
                map.put(name, ds);
            }
        });
        return new NamedDataSourceRegistry(map);
    }

    @Bean
    public DslContextRegistry dslContextRegistry(NamedDataSourceRegistry registry, AppDataSourceProperties properties) {
        Map<String, DSLContext> map = new LinkedHashMap<>();
        registry.all().forEach((name, ds) -> {
            map.put(name, DSL.using(ds, SQLDialect.POSTGRES));
        });
        return new DslContextRegistry(map);
    }

    @Bean
    @ConditionalOnMissingBean(DSLContext.class)
    public DSLContext dslContext(DataSource dataSource) throws java.sql.SQLException {
        return DSL.using(dataSource, SQLDialect.POSTGRES);
    }
}
