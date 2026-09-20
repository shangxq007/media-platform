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
                jdbcUrl() + (jdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema
                        + "&options=-c%20TimeZone%3DPacific%2FAuckland",
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
            assertThat(jdbc.queryForObject("SHOW TimeZone", String.class)).isEqualTo("Pacific/Auckland");
            insert("zone", CUTOFF, "SCHEDULED");
            assertThat(repository.findScheduledBefore(CUTOFF)).singleElement()
                    .extracting(SocialPost::scheduledAt).isEqualTo(CUTOFF);
            repository.updateStatus("zone", PostStatus.SCHEDULED, CUTOFF);
            assertThat(repository.findById("zone").orElseThrow().updatedAt()).isEqualTo(CUTOFF);
            assertThat(jdbc.queryForObject("SELECT updated_at FROM social_post WHERE id='zone'", LocalDateTime.class))
                    .isEqualTo(LocalDateTime.ofInstant(CUTOFF, ZoneOffset.UTC));
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
        repository.updateStatus("updated", PostStatus.PUBLISHING, CUTOFF);
        assertThat(repository.findById("updated").orElseThrow().updatedAt()).isEqualTo(CUTOFF);
        repository.updatePublishResult("updated", "external", "url", PostStatus.PUBLISHED, CUTOFF, CUTOFF);
        assertThat(repository.findById("updated").orElseThrow().publishedAt()).isEqualTo(CUTOFF);
        repository.updateFailure("updated", "test", "controlled", PostStatus.FAILED, CUTOFF, 1, CUTOFF);
        assertThat(repository.findById("updated").orElseThrow().failedAt()).isEqualTo(CUTOFF);
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
                .publishNow("social-test", "actor", "due");
        new PostSchedulerService(repository, publisher).processScheduledPosts();
        verify(publisher).publishNow("social-test", "actor", "due");
        assertThat(repository.findById("due").orElseThrow().status()).isEqualTo(PostStatus.FAILED);
        new PostSchedulerService(repository, publisher).processScheduledPosts();
        verifyNoMoreInteractions(publisher);
    }
}
