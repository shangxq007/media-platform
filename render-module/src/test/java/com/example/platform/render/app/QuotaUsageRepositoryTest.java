package com.example.platform.render.app;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuotaUsageRepositoryTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private QuotaUsageRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES);
        RenderTestSchemaFixture.createSchema(dsl);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        RenderTestSchemaFixture.truncate(dsl);
        repository = new QuotaUsageRepository(dsl);
    }

    @Test
    void incrementUsageCreatesNewRecord() {
        int value = repository.incrementUsage("ten_1", "render", 5);
        assertEquals(5, value);

        int usage = repository.getUsage("ten_1", "render");
        assertEquals(5, usage);
    }

    @Test
    void incrementUsageAccumulates() {
        repository.incrementUsage("ten_1", "render", 5);
        repository.incrementUsage("ten_1", "render", 3);

        int usage = repository.getUsage("ten_1", "render");
        assertEquals(8, usage);
    }

    @Test
    void getUsageReturnsZeroForUnknown() {
        int usage = repository.getUsage("ten_nonexistent", "render");
        assertEquals(0, usage);
    }

    @Test
    void findByTenantAndFeatureReturnsRecord() {
        repository.incrementUsage("ten_1", "render", 10);

        Optional<QuotaUsageRepository.QuotaUsageRecord> found =
                repository.findByTenantAndFeature("ten_1", "render");
        assertTrue(found.isPresent());
        assertEquals(10, found.get().usageValue());
        assertEquals("ten_1", found.get().tenantId());
        assertEquals("render", found.get().featureCode());
    }

    @Test
    void findByTenantAndFeatureReturnsEmptyForUnknown() {
        Optional<QuotaUsageRepository.QuotaUsageRecord> found =
                repository.findByTenantAndFeature("ten_1", "nonexistent");
        assertTrue(found.isEmpty());
    }

    @Test
    void getUsageByTenantReturnsAllFeatures() {
        repository.incrementUsage("ten_1", "render", 10);
        repository.incrementUsage("ten_1", "ai", 5);
        repository.incrementUsage("ten_2", "render", 20);

        Map<String, Integer> usage = repository.getUsageByTenant("ten_1");
        assertEquals(2, usage.size());
        assertEquals(10, usage.get("render"));
        assertEquals(5, usage.get("ai"));

        Map<String, Integer> usage2 = repository.getUsageByTenant("ten_2");
        assertEquals(1, usage2.size());
        assertEquals(20, usage2.get("render"));
    }

    @Test
    void getUsageByTenantReturnsEmptyForUnknownTenant() {
        Map<String, Integer> usage = repository.getUsageByTenant("ten_nonexistent");
        assertTrue(usage.isEmpty());
    }
}
