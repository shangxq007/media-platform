package com.example.platform.component;

import com.example.platform.analytics.AnalyticsConfiguration;
import com.example.platform.analytics.api.AnalyticsController;
import com.example.platform.analytics.app.*;
import com.example.platform.analytics.infrastructure.*;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsCompositionPostgresTest extends PostgresTestContainerSupport {
    static JdbcTemplate jdbc;
    @EnableScheduling static class Scheduling {}
    @BeforeAll static void migrate() {
        String schema = isolatedSchemaName();
        Flyway.configure().dataSource(jdbcUrl(), username(), password()).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(jdbcUrl() + (jdbcUrl().contains("?") ? "&" : "?")
                + "currentSchema=" + schema, username(), password()));
    }
    @BeforeEach void clean() {
        for (String table : new String[]{"user_behavior_event", "user_profile", "user_segment", "user_habits"}) jdbc.update("DELETE FROM " + table);
    }
    ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(AnalyticsConfiguration.class, Scheduling.class)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withBean(TaskScheduler.class, () -> mock(TaskScheduler.class));
    }
    ApplicationContextRunner persistent() {
        return runner().withPropertyValues("app.analytics.enabled=true").withBean(JdbcTemplate.class, () -> jdbc);
    }
    @Test void defaultAndDisabledDoNotWriteOrScheduleEvenWithOtherFlags() {
        for (String flag : new String[]{"", "app.analytics.enabled=false"}) {
            runner().withPropertyValues(flag, "app.analytics.scheduler.enabled=true", "app.analytics.persistence=invalid")
                    .withBean(JdbcTemplate.class, () -> jdbc).run(c -> {
                        assertThat(c).hasNotFailed().doesNotHaveBean(AnalyticsController.class).doesNotHaveBean(BehaviorEventService.class);
                        assertThat(c.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).isEmpty();
                        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_behavior_event", Long.class)).isZero();
                    });
        }
    }
    @Test void jdbcIsTheSingleDefaultAndTimedRebuildUsesSameDurableBackend() {
        persistent().withPropertyValues("app.analytics.scheduler.enabled=true", "app.analytics.scheduler.tenants=tenant").run(c -> {
            assertThat(c).hasNotFailed().hasSingleBean(UserProfileRepository.class).hasSingleBean(UserBehaviorEventRepository.class)
                    .hasSingleBean(UserHabitsRepository.class).hasSingleBean(UserSegmentRepository.class);
            assertThat(c.getBean(UserProfileRepository.class)).isInstanceOf(JdbcUserProfileRepository.class);
            var profiles = c.getBean(UserProfileService.class);
            profiles.getOrCreateProfile("tenant", "actor");
            var events = c.getBean(BehaviorEventService.class);
            events.ingestEvent("tenant", "actor", "USE", "edit", "timeline", "resource", Map.of());
            assertThat(events.findEventsByTenantAndUser("tenant", "actor", 10)).hasSize(1);
            var tasks = c.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();
            assertThat(tasks).hasSize(2);
            tasks.forEach(task -> task.getTask().getRunnable().run());
            assertThat(jdbc.queryForObject("SELECT total_actions FROM user_profile WHERE tenant_id='tenant'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_segment", Integer.class)).isEqualTo(6);
            assertThat(c.getBean(UserProfileRepository.class).findByTenantIdAndUserId("tenant", "actor").orElseThrow().totalActions()).isEqualTo(1);
        });
        persistent().run(c -> {
            assertThat(c.getBean(BehaviorEventService.class).findEventsByTenantAndUser("tenant", "actor", 10)).hasSize(1);
            assertThat(c.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).isEmpty();
        });
    }
    @Test void memoryRequiresExplicitSupportedProfileAndNeverWritesDatabase() {
        for (String profile : new String[]{"dev", "test"}) {
            persistent().withPropertyValues("app.analytics.persistence=memory", "spring.profiles.active=" + profile).run(c -> {
                assertThat(c).hasNotFailed().hasSingleBean(UserProfileRepository.class).doesNotHaveBean(JdbcUserProfileRepository.class);
                c.getBean(BehaviorEventService.class).ingestEvent("tenant", "actor", "USE", "edit", null, null, Map.of());
                assertThat(c.getBean(BehaviorEventService.class).findEventsByTenantAndUser("tenant", "actor", 10)).hasSize(1);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM user_behavior_event", Long.class)).isZero();
            });
        }
        for (String profile : new String[]{"prod", "dev,prod", ""}) {
            runner().withPropertyValues("app.analytics.enabled=true", "app.analytics.persistence=memory", "spring.profiles.active=" + profile)
                    .run(c -> assertThat(c).hasFailed());
        }
    }
    @Test void missingInvalidAndConflictingBackendFailWithoutFallback() {
        runner().withPropertyValues("app.analytics.enabled=true", "spring.profiles.active=test").run(c -> assertThat(c).hasFailed());
        persistent().withPropertyValues("app.analytics.persistence=unknown").run(c -> assertThat(c).hasFailed());
        persistent().withBean("competingProfile", UserProfileRepository.class, InMemoryUserProfileRepository::new,
                definition -> definition.setPrimary(true)).run(c -> assertThat(c).hasFailed());
        var broken = new JdbcTemplate(new DriverManagerDataSource(jdbcUrl() + (jdbcUrl().contains("?") ? "&" : "?")
                + "currentSchema=missing_analytics_schema", username(), password()));
        runner().withPropertyValues("app.analytics.enabled=true").withBean(JdbcTemplate.class, () -> broken)
                .run(c -> assertThat(c).hasFailed());
    }
    @Test void persistenceFailureRollsBackAndRecoveryKeepsSameBackend() {
        persistent().withPropertyValues("app.analytics.scheduler.enabled=false").run(c -> {
            var events = c.getBean(BehaviorEventService.class);
            var tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                events.ingestEvent("tenant", "actor", "USE", "edit", null, null, Map.of());
                jdbc.update("INSERT INTO user_behavior_event(event_id,tenant_id,user_id,event_type) VALUES ('invalid',null,'actor','USE')");
            })).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(events.findEventsByTenantAndUser("tenant", "actor", 10)).isEmpty();
            events.ingestEvent("tenant", "actor", "USE", "edit", null, null, Map.of());
            assertThat(events.findEventsByTenantAndUser("tenant", "actor", 10)).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_behavior_event", Long.class)).isEqualTo(1);
        });
    }
}
