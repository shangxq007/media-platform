package com.example.platform.federation.graphql.resolver;

import com.example.platform.federation.graphql.context.GraphQLRequestContext;
import com.example.platform.federation.graphql.dto.*;
import com.example.platform.prompt.app.PromptTemplateService;
import com.example.platform.prompt.domain.PromptExecutionRun;
import com.example.platform.prompt.domain.PromptTemplate;
import com.example.platform.prompt.domain.PromptTemplateVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class PromptGraphQLResolver {

    private static final Logger log = LoggerFactory.getLogger(PromptGraphQLResolver.class);

    private final PromptTemplateService promptTemplateService;

    public PromptGraphQLResolver(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    @QueryMapping
    public PromptTemplateDetail promptTemplateDetail(@Argument String id, GraphQLRequestContext context) {
        String tenantId = context.tenantId();
        String userId = context.userId();

        PromptTemplate template = promptTemplateService.getTemplate(id);

        List<PromptVersion> versions = promptTemplateService.listVersions(id).stream()
                .map(v -> new PromptVersion(
                        v.promptVersion(),
                        v.createdAt() != null ? v.createdAt().toString() : null,
                        v.createdBy(),
                        v.changelog()
                ))
                .collect(Collectors.toList());

        PromptTemplateVersion currentVersion = promptTemplateService.getCurrentVersion(id);
        List<PromptExecution> executions = promptTemplateService.listExecutions(id).stream()
                .limit(20)
                .map(this::mapExecution)
                .collect(Collectors.toList());

        return new PromptTemplateDetail(
                template.templateId(),
                template.name(),
                template.status() != null ? template.status().name() : "UNKNOWN",
                currentVersion != null ? currentVersion.promptVersion() : null,
                template.tags(),
                versions,
                executions
        );
    }

    private PromptExecution mapExecution(PromptExecutionRun run) {
        MoneyDto costEstimate = new MoneyDto(run.costEstimate(), "USD");
        return new PromptExecution(
                run.executionId(),
                run.status() != null ? run.status().name() : "UNKNOWN",
                run.riskLevel() != null ? run.riskLevel().name() : null,
                costEstimate,
                run.startedAt() != null ? run.startedAt().toString() : null,
                run.finishedAt() != null ? run.finishedAt().toString() : null
        );
    }
}
