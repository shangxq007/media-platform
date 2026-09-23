package com.example.platform.policy.featureflag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class JdbcFeatureFlagSnapshotStoreSpringTest {
    @Test
    void repositoryBeanCanBeCreatedWhenSpringAppliesRepositoryAndTransactionalProxies() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            assertInstanceOf(FeatureFlagSnapshotStore.class,
                    context.getBean(FeatureFlagSnapshotStore.class));
        }
    }

    @Test
    void persistenceEntryPointRetainsTransactionalBoundary() throws NoSuchMethodException {
        Method persist = JdbcFeatureFlagSnapshotStore.class.getMethod("persist",
                com.example.platform.policy.featureflag.domain.FeatureFlagSnapshot.class);
        org.junit.jupiter.api.Assertions.assertTrue(persist.isAnnotationPresent(Transactional.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {
        @Bean DataSource dataSource() { return new org.springframework.jdbc.datasource.DriverManagerDataSource(); }
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean JdbcFeatureFlagSnapshotStore jdbcFeatureFlagSnapshotStore(JdbcTemplate jdbc, ObjectMapper mapper) {
            return new JdbcFeatureFlagSnapshotStore(jdbc, mapper);
        }
        @Bean PersistenceExceptionTranslationPostProcessor exceptionTranslation() {
            return new PersistenceExceptionTranslationPostProcessor();
        }
    }
}
