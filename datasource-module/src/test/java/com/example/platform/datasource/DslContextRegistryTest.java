package com.example.platform.datasource;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DslContextRegistryTest {

    private DslContextRegistry createRegistryWithSources(String... names) {
        Map<String, DSLContext> contexts = new LinkedHashMap<>();
        for (String name : names) {
            DataSource ds = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                    "jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
            contexts.put(name, org.jooq.impl.DSL.using(ds, SQLDialect.H2));
        }
        return new DslContextRegistry(contexts);
    }

    @Test
    void getReturnsPresentForExistingContext() {
        DslContextRegistry registry = createRegistryWithSources("primary");

        assertThat(registry.get("primary")).isPresent();
    }

    @Test
    void getReturnsEmptyForMissingContext() {
        DslContextRegistry registry = createRegistryWithSources("primary");

        assertThat(registry.get("nonexistent")).isEmpty();
    }

    @Test
    void allReturnsAllContexts() {
        DslContextRegistry registry = createRegistryWithSources("primary", "secondary");

        assertThat(registry.all()).hasSize(2);
        assertThat(registry.all()).containsKeys("primary", "secondary");
    }

    @Test
    void allReturnsEmptyMapWhenNoContexts() {
        DslContextRegistry registry = new DslContextRegistry(new LinkedHashMap<>());

        assertThat(registry.all()).isEmpty();
    }

    @Test
    void dslContextHasH2Dialect() {
        DslContextRegistry registry = createRegistryWithSources("primary");

        DSLContext ctx = registry.get("primary").orElseThrow();
        assertThat(ctx.configuration().dialect()).isEqualTo(SQLDialect.H2);
    }

    @Test
    void dslContextCanExecuteQuery() {
        DslContextRegistry registry = createRegistryWithSources("primary");

        DSLContext ctx = registry.get("primary").orElseThrow();
        Integer result = ctx.selectOne().fetchOne(0, Integer.class);
        assertThat(result).isEqualTo(1);
    }

    @Test
    void multipleContextsAreIndependent() {
        DslContextRegistry registry = createRegistryWithSources("ds1", "ds2", "ds3");

        assertThat(registry.get("ds1")).isPresent();
        assertThat(registry.get("ds2")).isPresent();
        assertThat(registry.get("ds3")).isPresent();
        assertThat(registry.all()).hasSize(3);
    }
}
