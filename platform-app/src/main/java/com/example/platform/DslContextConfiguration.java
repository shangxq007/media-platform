package com.example.platform;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.platform.identity.app.BuiltinDataInitializer;
import com.example.platform.shared.monitoring.SentryMonitoringService;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameCase;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class DslContextConfiguration implements BeanFactoryPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DslContextConfiguration.class);
    private static boolean flywayMigrated = false;

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (flywayMigrated) return;
        try {
            DataSource ds = beanFactory.getBean(DataSource.class);
            Flyway flyway = Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion("999")
                    .validateOnMigrate(false)
                    .load();
            flyway.migrate();
            flywayMigrated = true;
            try {
                BuiltinDataInitializer initializer = beanFactory.getBean(BuiltinDataInitializer.class);
                initializer.init();
            } catch (Exception e) {
                log.warn("BuiltinDataInitializer not available: {}", e.getMessage());
            }
        } catch (Exception e) {
            throw new RuntimeException("Flyway migration failed", e);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public DSLContext dslContext(DataSource dataSource) {
        Settings settings = new Settings().withRenderNameCase(RenderNameCase.LOWER);
        return DSL.using(dataSource, SQLDialect.H2, settings);
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    @Bean
    @ConditionalOnMissingBean
    public SentryMonitoringService sentryMonitoringService() {
        return new SentryMonitoringService(false, "development", 1.0);
    }
}
