package com.example.platform.policy.featureflag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.aop.support.AopUtils;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class JdbcFeatureFlagSnapshotStoreSpringTest {
    @Test
    void repositoryBeanCanBeCreatedWhenSpringAppliesRepositoryAndTransactionalProxies() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            Object bean = context.getBean(FeatureFlagSnapshotStore.class);
            assertInstanceOf(FeatureFlagSnapshotStore.class, bean);
            org.junit.jupiter.api.Assertions.assertTrue(AopUtils.isCglibProxy(bean));
        }
    }

    @Test
    void persistenceEntryPointRetainsTransactionalBoundary() throws NoSuchMethodException {
        Method persist = JdbcFeatureFlagSnapshotStore.class.getMethod("persist",
                com.example.platform.policy.featureflag.domain.FeatureFlagSnapshot.class);
        org.junit.jupiter.api.Assertions.assertTrue(persist.isAnnotationPresent(Transactional.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration {
        @Bean DataSource dataSource() { return new org.springframework.jdbc.datasource.DriverManagerDataSource(); }
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean JdbcFeatureFlagSnapshotStore jdbcFeatureFlagSnapshotStore(JdbcTemplate jdbc, ObjectMapper mapper) {
            return new JdbcFeatureFlagSnapshotStore(jdbc, mapper);
        }
        @Bean PersistenceExceptionTranslationPostProcessor exceptionTranslation() {
            return new PersistenceExceptionTranslationPostProcessor();
        }
    }
}
