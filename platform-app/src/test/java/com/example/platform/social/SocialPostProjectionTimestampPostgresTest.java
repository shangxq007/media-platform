package com.example.platform.social;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.social.domain.*;
import com.example.platform.social.infrastructure.persistence.SocialPostRepository;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Regression coverage for the independently reproduced entity/projection UTC mismatch. */
class SocialPostProjectionTimestampPostgresTest extends PostgresTestContainerSupport {
    static final Instant T = Instant.parse("2026-09-20T12:00:00.123456Z");
    static org.springframework.jdbc.core.JdbcTemplate jdbc;
    @BeforeAll static void database() {
        String schema = isolatedSchemaName();
        org.flywaydb.core.Flyway.configure().dataSource(jdbcUrl(), username(), password())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate();
        jdbc = new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                jdbcUrl() + (jdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
                username(), password()));
    }
    @Test void allEntityBindingsAndRawCounterexampleInDifferentTimezones() {
        inZone(repo -> {
            assertThatThrownBy(() -> jdbc.queryForList("SELECT id FROM social_post WHERE scheduled_at <= ?", T))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class)
                    .hasStackTraceContaining("Can't infer the SQL type");
            // JDBC rejects Instant client-side, before sending a statement/aborting this transaction.
            repo.save(post("entity", null, null, null, T, T.plusSeconds(1), T.plusSeconds(2)));
            var saved=repo.findById("entity").orElseThrow();
            assertThat(saved.scheduledAt()).isEqualTo(T);
            assertThat(saved.publishedAt()).isEqualTo(T.plusSeconds(1));
            assertThat(saved.failedAt()).isEqualTo(T.plusSeconds(2));
            assertThat(saved.createdAt()).isEqualTo(T);
            assertThat(saved.updatedAt()).isEqualTo(T);
            var attempt=repo.claim("social-test","actor","entity","token",PostStatus.SCHEDULED,T).orElseThrow();
            repo.markDispatched(attempt,T);
            repo.complete(attempt,"external","url",T.plusSeconds(3));
            saved=repo.findById("entity").orElseThrow();
            assertThat(saved.publishedAt()).isEqualTo(T.plusSeconds(3));
            assertThat(saved.updatedAt()).isEqualTo(T.plusSeconds(3));
            repo.save(post("failure",null,null,null,T,null,null));
            var failed=repo.claim("social-test","actor","failure","failed",PostStatus.SCHEDULED,T).orElseThrow();
            repo.failBeforeDispatch(failed,T.plusSeconds(5));
            assertThat(repo.findById("failure").orElseThrow().failedAt()).isEqualTo(T.plusSeconds(5));
            assertThat(repo.findById("failure").orElseThrow().updatedAt()).isEqualTo(T.plusSeconds(5));
            repo.save(post("nulls",null,null,null,null,null,null));
            saved=repo.findById("nulls").orElseThrow();
            assertThat(saved.scheduledAt()).isNull(); assertThat(saved.publishedAt()).isNull(); assertThat(saved.failedAt()).isNull();
            var nanos=T.plusNanos(789);
            repo.save(post("precision",null,null,null,nanos,null,null));
            assertThat(repo.findById("precision").orElseThrow().scheduledAt()).isEqualTo(T.plusNanos(1000));
        });
    }
    @Test void detailProjectionMustAgreeWithSavedEntity() {
        inZone(repo -> {
            bound(repo,"detail");
            assertThat(repo.findById("detail").orElseThrow().scheduledAt()).isEqualTo(T);
            assertThat(repo.findReadProjectionById("social-test","actor","review-project","review-account",1,"detail")
                    .orElseThrow().scheduledAt()).isEqualTo(T);
        });
    }
    @Test void listWindowMustIncludeSavedInstant() {
        inZone(repo -> {
            bound(repo,"list");
            assertThat(repo.findReadProjection("social-test","actor","review-project","review-account",1,
                    T.minusSeconds(1),T.plusSeconds(1),10)).extracting(p -> p.postId()).containsExactly("list");
        });
    }
    @Test void halfOpenBoundsOrderingAndPrecisionAgreeAcrossAllReads() {
        inZone(repo -> {
            bound(repo, "lower");
            repo.save(post("before", "review-project", "review-account", 1L, T.minusNanos(1000), null, null));
            repo.save(post("upper", "review-project", "review-account", 1L, T.plusNanos(2000), null, null));
            repo.save(post("z-rounded", "review-project", "review-account", 1L, T.plusNanos(789), null, null));
            repo.save(post("a-exact", "review-project", "review-account", 1L, T.plusNanos(1000), null, null));
            repo.save(post("unscheduled", "review-project", "review-account", 1L, null, null, null));
            var rows = repo.findReadProjection("social-test", "actor", "review-project", "review-account", 1,
                    T, T.plusNanos(2000), 10);
            assertThat(rows).extracting(p -> p.postId()).containsExactly("lower", "a-exact", "z-rounded");
            assertThat(rows).extracting(p -> p.scheduledAt()).containsExactly(T, T.plusNanos(1000), T.plusNanos(1000));
            for (var row : rows) {
                assertThat(repo.findById(row.postId()).orElseThrow().scheduledAt()).isEqualTo(row.scheduledAt());
                assertThat(repo.findReadProjectionById("social-test", "actor", "review-project", "review-account", 1,
                        row.postId()).orElseThrow().scheduledAt()).isEqualTo(row.scheduledAt());
            }
            assertThat(repo.findReadProjection("social-test", "actor", "review-project", "review-account", 1,
                    T, T.plusNanos(2000), 1)).extracting(p -> p.postId()).containsExactly("lower");
            assertThat(repo.findReadProjectionById("social-test", "actor", "review-project", "review-account", 1,
                    "unscheduled").orElseThrow().scheduledAt()).isNull();
        });
    }

    @Test void scopeBindingAndActiveAccountRejectionsLeaveStoredInstantUnchanged() {
        inZone(repo -> {
            bound(repo, "scoped");
            for (String[] scope : List.of(
                    new String[]{"foreign", "actor", "review-project", "review-account"},
                    new String[]{"social-test", "outsider", "review-project", "review-account"},
                    new String[]{"social-test", "actor", "other-project", "review-account"},
                    new String[]{"social-test", "actor", "review-project", "other-account"})) {
                assertThat(repo.findReadProjection(scope[0], scope[1], scope[2], scope[3], 1,
                        T.minusSeconds(1), T.plusSeconds(1), 10)).isEmpty();
                assertThat(repo.findReadProjectionById(scope[0], scope[1], scope[2], scope[3], 1, "scoped")).isEmpty();
            }
            assertRejected(repo, 2);
            assertThat(repo.findReadProjectionById("social-test", "actor", "review-project", "review-account", 1,
                    "scoped")).isPresent();
            jdbc.update("UPDATE social_connected_platform SET status='INACTIVE' WHERE id='review-account'");
            assertRejected(repo, 1);
            assertThat(repo.findById("scoped").orElseThrow().scheduledAt()).isEqualTo(T);
            jdbc.update("UPDATE social_connected_platform SET status='ACTIVE' WHERE id='review-account'");
            assertThat(repo.findReadProjection("social-test", "actor", "review-project", "review-account", 1,
                    T.minusSeconds(1), T.plusSeconds(1), 10)).extracting(p -> p.scheduledAt()).containsExactly(T);
        });
    }

    static void assertRejected(SocialPostRepository repo, long version) {
        assertThat(repo.findReadProjection("social-test", "actor", "review-project", "review-account", version,
                T.minusSeconds(1), T.plusSeconds(1), 10)).isEmpty();
        assertThat(repo.findReadProjectionById("social-test", "actor", "review-project", "review-account", version,
                "scoped")).isEmpty();
    }

    static SocialPost post(String id,String project,String account,Long version,Instant schedule,Instant published,Instant failed) {
        return new SocialPost(id,"social-test","actor",project,account,version,null,"text",List.of(),
                PlatformType.TWITTER,PostStatus.SCHEDULED,null,null,schedule,published,failed,null,null,0,T,T);
    }
    static void bound(SocialPostRepository repo,String id) {
        jdbc.update("INSERT INTO tenant(id,name,created_at) VALUES ('social-test','review',localtimestamp)");
        jdbc.update("INSERT INTO project(id,tenant_id,name,created_at) VALUES ('review-project','social-test','review',localtimestamp)");
        jdbc.update("INSERT INTO social_connected_platform(id,tenant_id,user_id,platform_type) VALUES ('review-account','social-test','actor','TWITTER')");
        repo.save(post(id,"review-project","review-account",1L,T,null,null));
    }
    static void inZone(java.util.function.Consumer<SocialPostRepository> probe) {
        TimeZone previous=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            TenantContext.set("social-test");
            new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())).executeWithoutResult(tx -> {
                tx.setRollbackOnly();
                jdbc.execute("SET LOCAL TIME ZONE 'Pacific/Auckland'");
                assertThat(jdbc.queryForObject("SHOW TimeZone",String.class)).isEqualTo("Pacific/Auckland");
                probe.accept(new SocialPostRepository(jdbc));
            });
            // DriverManagerDataSource opens a fresh connection: SET LOCAL cannot leak.
            assertThat(jdbc.queryForObject("SHOW TimeZone",String.class)).isEqualTo("Pacific/Honolulu");
        } finally { TenantContext.clear(); TimeZone.setDefault(previous); }
        assertThat(TimeZone.getDefault()).isEqualTo(previous);
    }
}
