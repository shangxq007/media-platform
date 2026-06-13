package com.example.platform.federation.nlq.infrastructure;

import com.example.platform.federation.nlq.domain.QueryHistoryRecord;
import com.example.platform.federation.nlq.domain.ReportDefinition;
import com.example.platform.federation.nlq.domain.ReportExecution;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NlqJdbcRepositoryTest extends PostgresTestContainerSupport {

    private static javax.sql.DataSource dataSource;
    private NlqJdbcRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        var jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS nlq_report_definition (
                report_id varchar(64) primary key,
                tenant_id varchar(64),
                workspace_id varchar(64),
                name varchar(255) not null,
                description text,
                widgets_json text,
                query_definitions_json text,
                created_by varchar(128),
                visibility varchar(32) not null,
                schedule_json text,
                created_at timestamp not null,
                updated_at timestamp not null,
                archived boolean not null default false
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS nlq_query_history (
                query_id varchar(64) primary key,
                user_id varchar(128) not null,
                tenant_id varchar(64),
                workspace_id varchar(64),
                question_redacted text,
                sql_hash varchar(64),
                datasets_json text,
                row_count int not null,
                duration_ms bigint not null,
                risk_level varchar(32),
                status varchar(32) not null,
                error_code varchar(64),
                created_at timestamp not null
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS nlq_report_execution (
                execution_id varchar(64) primary key,
                report_id varchar(64) not null,
                status varchar(32) not null,
                row_count int not null,
                duration_ms bigint not null,
                error_code varchar(64),
                created_at timestamp not null
            )
        """);
    }

    @BeforeEach
    void setUp() {
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE TABLE nlq_report_execution CASCADE");
        jdbc.execute("TRUNCATE TABLE nlq_query_history CASCADE");
        jdbc.execute("TRUNCATE TABLE nlq_report_definition CASCADE");

        repository = new NlqJdbcRepository(jdbc);
    }

    @Test
    void shouldPersistReportQueryHistoryAndExecution() {
        Instant now = Instant.now();
        ReportDefinition report = new ReportDefinition(
                "rpt-1", "t1", "ws1", "Usage Report", "desc",
                List.of(), List.of("SELECT 1"), "user-1", "PRIVATE", null, now, now, false);
        repository.saveReport(report);

        QueryHistoryRecord history = new QueryHistoryRecord(
                "qry-1", "user-1", "t1", "ws1", "how many?", "hash1",
                List.of("usage"), 1, 10L, "LOW", "SUCCESS", null, now);
        repository.saveQueryHistory(history);

        ReportExecution execution = new ReportExecution(
                "rpx-1", "rpt-1", "SUCCESS", 1, 10L, null, now);
        repository.saveReportExecution(execution);

        assertEquals(1, repository.loadAllReports().size());
        assertEquals(1, repository.loadAllQueryHistory().size());
        assertEquals(1, repository.loadAllReportExecutions().size());
    }
}
