package com.example.platform.federation.nlq.infrastructure;

import com.example.platform.federation.nlq.app.QueryHistoryService;
import com.example.platform.federation.nlq.app.ReportDefinitionService;
import com.example.platform.federation.nlq.app.ReportExecutionService;
import com.example.platform.federation.nlq.domain.QueryHistoryRecord;
import com.example.platform.federation.nlq.domain.ReportDefinition;
import com.example.platform.federation.nlq.domain.ReportExecution;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(JdbcTemplate.class)
public class NlqPersistenceBootstrap {

    private final NlqJdbcRepository repository;
    private final ReportDefinitionService reportDefinitionService;
    private final QueryHistoryService queryHistoryService;
    private final ReportExecutionService reportExecutionService;

    public NlqPersistenceBootstrap(NlqJdbcRepository repository,
                                   ReportDefinitionService reportDefinitionService,
                                   QueryHistoryService queryHistoryService,
                                   ReportExecutionService reportExecutionService) {
        this.repository = repository;
        this.reportDefinitionService = reportDefinitionService;
        this.queryHistoryService = queryHistoryService;
        this.reportExecutionService = reportExecutionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void hydrate() {
        for (ReportDefinition report : repository.loadAllReports()) {
            reportDefinitionService.hydrateReport(report);
        }
        for (QueryHistoryRecord record : repository.loadAllQueryHistory()) {
            queryHistoryService.hydrateRecord(record);
        }
        for (ReportExecution execution : repository.loadAllReportExecutions()) {
            reportExecutionService.hydrateExecution(execution);
        }
    }
}
