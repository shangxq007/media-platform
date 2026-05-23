package com.example.platform.federation.nlq.infrastructure;

import com.example.platform.federation.nlq.domain.QueryHistoryRecord;
import com.example.platform.federation.nlq.domain.ReportDefinition;
import com.example.platform.federation.nlq.domain.ReportExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NlqJdbcRepositoryTest {

    private NlqJdbcRepository repository;

    @BeforeEach
    void setUp() {
        var dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema-nlq-h2.sql")
                .build();
        repository = new NlqJdbcRepository(new JdbcTemplate(dataSource));
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
