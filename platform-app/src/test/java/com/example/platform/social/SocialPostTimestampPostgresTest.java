package com.example.platform.social;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.social.app.PostSchedulerService;
import com.example.platform.social.app.SocialPublishService;
import com.example.platform.social.domain.*;
import com.example.platform.social.infrastructure.persistence.SocialPostRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SocialPostTimestampPostgresTest extends PostgresTestContainerSupport {
    static JdbcTemplate jdbc;
    SocialPostRepository repository;
    static final Instant CUTOFF = Instant.parse("2026-09-20T12:00:00.123456Z");

    @BeforeAll
    static void database() {
        String schema = isolatedSchemaName();
        Flyway.configure().dataSource(jdbcUrl(), username(), password())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                jdbcUrl() + (jdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
                username(), password()));
    }

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM social_post");
        repository = new SocialPostRepository(jdbc);
        TenantContext.set("social-test");
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    void insert(String id, Instant scheduled, String status) {
        jdbc.update("INSERT INTO social_post(id,tenant_id,user_id,platform_type,status,scheduled_at) VALUES (?,'social-test','actor','TWITTER',?,?)",
                id, status, LocalDateTime.ofInstant(scheduled, ZoneOffset.UTC));
    }

    @Test
    void cutoffIsInclusiveAndStatusFilteredAtMicrosecondPrecision() {
        insert("before", CUTOFF.minusNanos(1000), "SCHEDULED");
        insert("exact", CUTOFF, "SCHEDULED");
        insert("after", CUTOFF.plusNanos(1000), "SCHEDULED");
        insert("draft", CUTOFF.minusSeconds(1), "DRAFT");
        insert("published", CUTOFF.minusSeconds(1), "PUBLISHED");
        assertThat(repository.findScheduledBefore(CUTOFF)).extracting(SocialPost::id)
                .containsExactlyInAnyOrder("before", "exact");
        assertThat(repository.findScheduledBefore(CUTOFF.minusSeconds(2))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='social_post' AND column_name='scheduled_at'", String.class))
                .isEqualTo("timestamp without time zone");
    }

    @Test
    void utcInstantDoesNotDependOnJvmOrDatabaseTimezone() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()))
                    .executeWithoutResult(transaction -> {
                        jdbc.execute("SET LOCAL TIME ZONE 'Pacific/Auckland'");
                        assertThat(jdbc.queryForObject("SHOW TimeZone", String.class)).isEqualTo("Pacific/Auckland");
                        insert("zone", CUTOFF, "SCHEDULED");
                        assertThat(repository.findScheduledBefore(CUTOFF)).singleElement()
                                .extracting(SocialPost::scheduledAt).isEqualTo(CUTOFF);
                        repository.schedule("social-test", "actor", "zone", CUTOFF, CUTOFF);
                        assertThat(repository.findById("zone").orElseThrow().updatedAt()).isEqualTo(CUTOFF);
                        assertThat(jdbc.queryForObject("SELECT updated_at FROM social_post WHERE id='zone'", LocalDateTime.class))
                                .isEqualTo(LocalDateTime.ofInstant(CUTOFF, ZoneOffset.UTC));
                    });
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void saveBindsNullableAndNonNullInstants() {
        repository.save(new SocialPost("saved", "social-test", "actor", null, null, null, null,
                "text", List.of(), PlatformType.TWITTER, PostStatus.SCHEDULED, null, null,
                CUTOFF, null, null, null, null, 0, CUTOFF, CUTOFF));
        SocialPost saved = repository.findById("saved").orElseThrow();
        assertThat(saved.scheduledAt()).isEqualTo(CUTOFF);
        assertThat(saved.createdAt()).isEqualTo(CUTOFF);
        assertThat(saved.publishedAt()).isNull();
    }

    @Test
    void lifecycleUpdatesBindInstants() {
        insert("updated", CUTOFF, "SCHEDULED");
        var attempt=repository.claim("social-test","actor","updated","token",PostStatus.SCHEDULED,CUTOFF).orElseThrow();
        assertThat(repository.findById("updated").orElseThrow().updatedAt()).isEqualTo(CUTOFF);
        repository.markDispatched(attempt,CUTOFF);
        repository.complete(attempt,"external","url",CUTOFF);
        assertThat(repository.findById("updated").orElseThrow().publishedAt()).isEqualTo(CUTOFF);
        insert("failure", CUTOFF, "SCHEDULED");
        var failed=repository.claim("social-test","actor","failure","failed",PostStatus.SCHEDULED,CUTOFF).orElseThrow();
        repository.failBeforeDispatch(failed,CUTOFF);
        assertThat(repository.findById("failure").orElseThrow().failedAt()).isEqualTo(CUTOFF);
    }

    @Test
    void databaseFailurePropagatesToCaller() {
        JdbcTemplate broken = new JdbcTemplate(new DriverManagerDataSource(
                jdbcUrl() + (jdbcUrl().contains("?") ? "&" : "?") + "currentSchema=missing_social_schema",
                username(), password()));
        assertThatThrownBy(() -> new SocialPostRepository(broken).findScheduledBefore(CUTOFF))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void schedulerUsesRealRepositoryWithoutExternalPublishing() {
        insert("due", Instant.now().minusSeconds(60), "SCHEDULED");
        SocialPublishService publisher = mock(SocialPublishService.class);
        doThrow(new IllegalStateException("controlled publisher failure")).when(publisher)
                .publishScheduled("social-test", "actor", "due");
        new PostSchedulerService(repository, publisher).processScheduledPosts();
        verify(publisher).publishScheduled("social-test", "actor", "due");
        assertThat(repository.findById("due").orElseThrow().status()).isEqualTo(PostStatus.SCHEDULED);
        new PostSchedulerService(repository, publisher).processScheduledPosts();
        verify(publisher, times(2)).publishScheduled("social-test", "actor", "due");
        assertThat(repository.findById("due").orElseThrow().retryCount()).isZero();
    }
}
