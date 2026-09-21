package com.example.platform.social;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.social.app.*;
import com.example.platform.social.domain.*;
import com.example.platform.social.infrastructure.persistence.*;
import com.example.platform.social.infrastructure.platform.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SocialSchedulerCompositionPostgresTest extends PostgresTestContainerSupport {
    static JdbcTemplate jdbc;
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @EnableTransactionManagement
    @Import({PostSchedulerService.class, SocialPublishService.class, SocialPostRepository.class, ConnectedPlatformRepository.class})
    static class Assembly {}
    @BeforeAll static void migrate() {
        String schema = isolatedSchemaName();
        Flyway.configure().dataSource(jdbcUrl(), username(), password()).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(jdbcUrl() + (jdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema, username(), password()));
    }
    @BeforeEach void fixture() {
        jdbc.update("DELETE FROM social_post");
        jdbc.update("DELETE FROM social_connected_platform");
        jdbc.update("INSERT INTO social_connected_platform(id,tenant_id,user_id,platform_type,platform_user_id,platform_username,status,binding_version) VALUES ('account','tenant','actor','TWITTER','external','name','ACTIVE',1)");
        jdbc.update("INSERT INTO social_post(id,tenant_id,user_id,platform_type,status,scheduled_at) VALUES ('post','tenant','actor','TWITTER','SCHEDULED',?)", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
    }
    @Test void registeredSchedulerUsesDurableOwnerPathAndDisabledFlagsLeaveRowsUnchanged() {
        var provider = mock(PlatformAdapter.class);
        when(provider.platform()).thenReturn(PlatformType.TWITTER);
        when(provider.validateCredentials(any())).thenReturn(true);
        when(provider.publish(any(), any())).thenReturn(new PublishResult(false, null, null, "RETRY", "retry"));
        var runner = new ApplicationContextRunner().withUserConfiguration(Assembly.class)
                .withBean(JdbcTemplate.class, () -> jdbc)
                .withBean(DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(jdbc.getDataSource()))
                .withBean(TaskScheduler.class, () -> mock(TaskScheduler.class))
                .withBean(PlatformAdapter.class, () -> provider);
        for (String[] flags : new String[][]{{}, {"app.social-publish.enabled=false", "app.social-publish.scheduler.enabled=true"},
                {"app.social-publish.enabled=true", "app.social-publish.scheduler.enabled=false"}}) {
            runner.withPropertyValues(flags).run(c -> {
                assertThat(c).hasNotFailed();
                assertThat(c.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).isEmpty();
                assertThat(jdbc.queryForObject("SELECT status FROM social_post WHERE id='post'", String.class)).isEqualTo("SCHEDULED");
                verify(provider, never()).publish(any(), any());
            });
        }
        runner.withPropertyValues("app.social-publish.enabled=true", "app.social-publish.scheduler.enabled=true").run(c -> {
            assertThat(c).hasNotFailed();
            var tasks = c.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();
            assertThat(tasks).hasSize(1);
            tasks.iterator().next().getTask().getRunnable().run();
            assertThat(jdbc.queryForObject("SELECT status FROM social_post WHERE id='post'", String.class)).isEqualTo("FAILED");
            assertThat(jdbc.queryForObject("SELECT retry_count FROM social_post WHERE id='post'", Integer.class)).isEqualTo(1);
            jdbc.update("UPDATE social_post SET status='SCHEDULED' WHERE id='post'");
            when(provider.publish(any(), any())).thenReturn(new PublishResult(true, "external", "https://example.test/post", null, null));
            tasks.iterator().next().getTask().getRunnable().run();
            assertThat(jdbc.queryForObject("SELECT status FROM social_post WHERE id='post'", String.class)).isEqualTo("PUBLISHED");
            assertThat(jdbc.queryForObject("SELECT retry_count FROM social_post WHERE id='post'", Integer.class)).isEqualTo(1);
        });
    }
}
