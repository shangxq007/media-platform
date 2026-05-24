package com.example.platform.federation.graphql.resolver;

import com.example.platform.federation.graphql.context.GraphQLRequestContext;
import com.example.platform.federation.graphql.dto.*;
import com.example.platform.prompt.app.PromptTemplateService;
import com.example.platform.prompt.domain.PromptExecutionRun;
import com.example.platform.prompt.domain.PromptExecutionStatus;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.domain.RenderJobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class MonitoringFeedbackGraphQLResolver {

    private static final Logger log = LoggerFactory.getLogger(MonitoringFeedbackGraphQLResolver.class);

    private final RenderJobService renderJobService;
    private final PromptTemplateService promptTemplateService;

    public MonitoringFeedbackGraphQLResolver(RenderJobService renderJobService,
                                             PromptTemplateService promptTemplateService) {
        this.renderJobService = renderJobService;
        this.promptTemplateService = promptTemplateService;
    }

    @QueryMapping
    public MonitoringFeedbackOverview monitoringFeedbackOverview(@Argument String range,
                                                                  GraphQLRequestContext context) {
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
            List<RenderJobResponse> failedJobs = renderJobService.list().stream()
                    .filter(j -> RenderJobStatus.FAILED.name().equals(j.status()))
                    .toList();
            linkedRenderJobs = failedJobs.size();
        } catch (Exception e) {
            log.debug("Failed to count linked render jobs: {}", e.getMessage());
        }

        try {
            List<PromptExecutionRun> failedExecutions = promptTemplateService.listAllExecutions().stream()
                    .filter(e -> PromptExecutionStatus.FAILED.equals(e.status()))
                    .toList();
            linkedPromptExecutions = failedExecutions.size();
        } catch (Exception e) {
            log.debug("Failed to count linked prompt executions: {}", e.getMessage());
        }

        return new FeedbackSummary(0, 0, linkedRenderJobs, linkedPromptExecutions, false);
    }

    private ProblematicDataSummary resolveProblematicDataSummary() {
        return new ProblematicDataSummary(0, 0, 0, 0);
    }
}
