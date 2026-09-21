package com.example.platform.federation.graphql.resolver;

import com.example.platform.federation.graphql.context.GraphQLRequestContext;
import com.example.platform.federation.graphql.dto.*;
import com.example.platform.prompt.api.reads.PromptReadQuery;
import com.example.platform.federation.graphql.context.GraphQLReadScope;
import com.example.platform.prompt.api.reads.PromptReadQuery.Execution;
import com.example.platform.prompt.api.reads.PromptReadQuery.Template;
import com.example.platform.prompt.api.reads.PromptReadQuery.Version;
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

    private final GraphQLReadScope scope;
    private final PromptReadQuery promptTemplateService;

    public PromptGraphQLResolver(PromptReadQuery promptTemplateService, GraphQLReadScope scope) {
        this.scope = scope;
        this.promptTemplateService = promptTemplateService;
    }

    @QueryMapping
    public PromptTemplateDetail promptTemplateDetail(@Argument String id, @org.springframework.graphql.data.method.annotation.ContextValue("graphqlContext") GraphQLRequestContext context) {
        String tenantId = context.tenantId();
        String userId = context.userId();

        Template template = promptTemplateService.template(scope.actor(), id);

        List<PromptVersion> versions = promptTemplateService.versions(scope.actor(), id).stream()
                .map(v -> new PromptVersion(
                        v.promptVersion(),
                        v.createdAt() != null ? v.createdAt().toString() : null,
                        v.createdBy(),
                        v.changelog()
                ))
                .collect(Collectors.toList());

        Version currentVersion = promptTemplateService.currentVersion(scope.actor(), id);
        List<PromptExecution> executions = promptTemplateService.executions(scope.actor(), id).stream()
                .limit(20)
                .map(this::mapExecution)
                .collect(Collectors.toList());

        return new PromptTemplateDetail(
                template.templateId(),
                template.name(),
                template.status() != null ? template.status() : "UNKNOWN",
                currentVersion != null ? currentVersion.promptVersion() : null,
                template.tags(),
                versions,
                executions
        );
    }

    private PromptExecution mapExecution(Execution run) {
        MoneyDto costEstimate = new MoneyDto(run.costEstimate(), "USD");
        return new PromptExecution(
                run.executionId(),
                run.status() != null ? run.status() : "UNKNOWN",
                run.riskLevel() != null ? run.riskLevel() : null,
                costEstimate,
                run.startedAt() != null ? run.startedAt().toString() : null,
                run.finishedAt() != null ? run.finishedAt().toString() : null
        );
    }
}
