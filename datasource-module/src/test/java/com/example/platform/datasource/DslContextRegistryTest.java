package com.example.platform.datasource;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameCase;
import org.jooq.conf.Settings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DslContextRegistryTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;

    @BeforeAll
    static void setUp() {
        dataSource = createDataSource();
    }

    private DslContextRegistry createRegistryWithSources(String... names) {
        Map<String, DSLContext> contexts = new LinkedHashMap<>();
        var settings = new Settings().withRenderNameCase(RenderNameCase.LOWER);
        for (String name : names) {
            contexts.put(name, org.jooq.impl.DSL.using(dataSource, SQLDialect.POSTGRES, settings));
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
    void dslContextHasPostgresDialect() {
        DslContextRegistry registry = createRegistryWithSources("primary");

        DSLContext ctx = registry.get("primary").orElseThrow();
        assertThat(ctx.configuration().dialect()).isEqualTo(SQLDialect.POSTGRES);
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
