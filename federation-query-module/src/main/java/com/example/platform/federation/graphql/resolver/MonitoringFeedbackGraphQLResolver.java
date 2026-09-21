package com.example.platform.federation.graphql.resolver;

import com.example.platform.federation.graphql.context.GraphQLRequestContext;
import com.example.platform.federation.graphql.dto.*;
import com.example.platform.prompt.api.reads.PromptReadQuery;
import com.example.platform.federation.graphql.context.GraphQLReadScope;
import com.example.platform.prompt.api.reads.PromptReadQuery.Execution;

import com.example.platform.render.api.RenderReadQuery;
import com.example.platform.render.api.RenderReadQuery.Job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class MonitoringFeedbackGraphQLResolver {

    private static final Logger log = LoggerFactory.getLogger(MonitoringFeedbackGraphQLResolver.class);

    private final RenderReadQuery renderJobService;
    private final GraphQLReadScope scope;
    private final PromptReadQuery promptTemplateService;

    public MonitoringFeedbackGraphQLResolver(RenderReadQuery renderJobService,
                                             PromptReadQuery promptTemplateService, GraphQLReadScope scope) {
        this.scope = scope;
        this.renderJobService = renderJobService;
        this.promptTemplateService = promptTemplateService;
    }

    @QueryMapping
    public MonitoringFeedbackOverview monitoringFeedbackOverview(@Argument String range,
                                                                  @org.springframework.graphql.data.method.annotation.ContextValue("graphqlContext") GraphQLRequestContext context) {
        if (range == null || range.isBlank()) {
            range = "7d";
        }
        MonitoringStatus monitoringStatus = resolveMonitoringStatus();
        FeedbackSummary feedbackSummary = resolveFeedbackSummary();
        ProblematicDataSummary problematicDataSummary = resolveProblematicDataSummary();

        return new MonitoringFeedbackOverview(monitoringStatus, feedbackSummary, problematicDataSummary);
    }

    private MonitoringStatus resolveMonitoringStatus() {
        return new MonitoringStatus(true, true, null, null);
    }

    private FeedbackSummary resolveFeedbackSummary() {
        int linkedRenderJobs = 0;
        int linkedPromptExecutions = 0;

        try {
            List<Job> failedJobs = renderJobService.jobs().stream()
                    .filter(j -> "FAILED".equals(j.status()))
                    .toList();
            linkedRenderJobs = failedJobs.size();
        } catch (Exception e) { throw new IllegalStateException("Owner query unavailable", e);
        }

        try {
            List<Execution> failedExecutions = promptTemplateService.executions(scope.actor()).stream()
                    .filter(e -> "FAILED".equals(e.status()))
                    .toList();
            linkedPromptExecutions = failedExecutions.size();
        } catch (Exception e) { throw new IllegalStateException("Owner query unavailable", e);
        }

        return new FeedbackSummary(0, 0, linkedRenderJobs, linkedPromptExecutions, false);
    }

    private ProblematicDataSummary resolveProblematicDataSummary() {
        return new ProblematicDataSummary(0, 0, 0, 0);
    }
}
