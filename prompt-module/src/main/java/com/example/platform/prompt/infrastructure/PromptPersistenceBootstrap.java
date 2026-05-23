package com.example.platform.prompt.infrastructure;

import com.example.platform.prompt.app.PromptTemplateService;
import com.example.platform.prompt.domain.PromptExecutionRun;
import com.example.platform.prompt.domain.PromptTemplate;
import com.example.platform.prompt.domain.PromptTemplateVersion;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Loads JDBC-backed prompt data into the in-memory service cache on startup.
 */
@Component
@ConditionalOnBean(JdbcTemplate.class)
public class PromptPersistenceBootstrap {

    private final PromptJdbcRepository repository;
    private final PromptTemplateService templateService;

    public PromptPersistenceBootstrap(PromptJdbcRepository repository,
                                      PromptTemplateService templateService) {
        this.repository = repository;
        this.templateService = templateService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void hydrateFromDatabase() {
        for (PromptTemplate template : repository.loadAllTemplates()) {
            templateService.hydrateTemplate(template);
            for (PromptTemplateVersion version : repository.loadVersionsForTemplate(template.templateId())) {
                templateService.hydrateVersion(version);
            }
        }
        for (PromptExecutionRun run : repository.loadAllExecutions()) {
            templateService.hydrateExecution(run);
        }
    }
}
