package com.example.platform.federation.graphql.resolver;


import com.example.platform.billing.api.reads.BillingReadQuery;
import com.example.platform.federation.graphql.context.GraphQLReadScope;
import com.example.platform.billing.api.reads.BillingReadQuery.Usage;
import com.example.platform.extension.api.port.ExtensionQueries;
import com.example.platform.extension.api.port.ExtensionQueries.ExtensionInfo;
import com.example.platform.federation.graphql.context.GraphQLRequestContext;
import com.example.platform.federation.graphql.dto.*;
import com.example.platform.render.api.RenderReadQuery;
import com.example.platform.render.api.RenderReadQuery.Job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.*;
import java.util.stream.Collectors;

@Controller
public class AdminDashboardGraphQLResolver {

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardGraphQLResolver.class);

    private final RenderReadQuery renderJobService;
    private final GraphQLReadScope scope;
    private final BillingReadQuery usageMeteringService;
    private final ExtensionQueries extensionRegistryService;

    public AdminDashboardGraphQLResolver(RenderReadQuery renderJobService,
                                         BillingReadQuery usageMeteringService,
                                         ExtensionQueries extensionRegistryService, GraphQLReadScope scope) {
        this.scope = scope;
        this.renderJobService = renderJobService;
        this.usageMeteringService = usageMeteringService;
        this.extensionRegistryService = extensionRegistryService;
    }

    @QueryMapping
    public AdminDashboard adminDashboard(@Argument String range, @org.springframework.graphql.data.method.annotation.ContextValue("graphqlContext") GraphQLRequestContext context) {
        if (range == null || range.isBlank()) {
            range = "7d";
        }
        List<String> roles = context.roles();
        if (roles == null || (!roles.contains("ADMIN") && !roles.contains("ROLE_ADMIN") && !roles.contains("DASHBOARD_ADMIN") && !roles.contains("ROLE_DASHBOARD_ADMIN"))) {
            throw new IllegalArgumentException("Access denied: requires ADMIN or DASHBOARD_ADMIN role");
        }

        RenderStats renderStats = resolveRenderStats(context.tenantId());
        List<ProviderHealth> providerHealth = resolveProviderHealth();
        AdminBillingSummary billingSummary = resolveBillingSummary(range);
        FeedbackSummary feedbackSummary = resolveFeedbackSummary();
        ExtensionSummary extensionSummary = resolveExtensionSummary();

        return new AdminDashboard(renderStats, providerHealth, billingSummary, feedbackSummary, extensionSummary);
    }

    private RenderStats resolveRenderStats(String tenantId) {
        try {
            List<Job> jobs = renderJobService.jobs();
            int submitted = jobs.size();
            int completed = (int) jobs.stream()
                    .filter(j -> "COMPLETED".equals(j.status()))
                    .count();
            int failed = (int) jobs.stream()
                    .filter(j -> "FAILED".equals(j.status()))
                    .count();
            return new RenderStats(submitted, completed, failed, null);
        } catch (Exception e) { throw new IllegalStateException("Owner query unavailable", e);
        }
    }

    private List<ProviderHealth> resolveProviderHealth() {
        List<ProviderHealth> health = new ArrayList<>();
        health.add(new ProviderHealth("javacv", "HEALTHY", 50, 0.01));
        health.add(new ProviderHealth("ofx", "HEALTHY", 120, 0.02));
        health.add(new ProviderHealth("ffmpeg", "DEGRADED", 300, 0.05));
        return health;
    }

    private AdminBillingSummary resolveBillingSummary(String range) {
        try {
            List<Usage> allUsage = usageMeteringService.usage(scope.actor(), scope.actor().tenantId());
            double totalAmount = allUsage.stream()
                    .mapToDouble(r -> (double) r.quantity().baseUnits())
                    .sum();
            return new AdminBillingSummary(
                    new MoneyDto(totalAmount, "USD"),
                    new MoneyDto(totalAmount * 1.2, "USD"),
                    new MoneyDto(totalAmount * 0.8, "USD")
            );
        } catch (Exception e) { throw new IllegalStateException("Owner query unavailable", e);
        }
    }

    private FeedbackSummary resolveFeedbackSummary() {
        return new FeedbackSummary(0, 0, 0, 0, false);
    }

    private ExtensionSummary resolveExtensionSummary() {
        try {
            List<ExtensionInfo> extensions = extensionRegistryService.listExtensions();
            int installed = extensions.size();
            int enabled = (int) extensions.stream()
                    .filter(e -> "ACTIVE".equals(e.status()))
                    .count();
            int highRisk = (int) extensions.stream()
                    .filter(e -> "UNTRUSTED".equals(e.trustLevel()))
                    .count();
            return new ExtensionSummary(installed, enabled, highRisk, 0);
        } catch (Exception e) {
            log.debug("Extension summary resolution failed: {}", e.getMessage());
            return new ExtensionSummary(0, 0, 0, 0);
        }
    }
}
