package com.example.platform.federation.graphql.resolver;

import com.example.platform.billing.app.BillingProjectionService;
import com.example.platform.billing.app.UsageMeteringService;
import com.example.platform.billing.domain.UsageRecord;
import com.example.platform.extension.app.ExtensionRegistryService;
import com.example.platform.extension.app.ExtensionRegistryService.ExtensionInfo;
import com.example.platform.federation.graphql.context.GraphQLRequestContext;
import com.example.platform.federation.graphql.dto.*;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.domain.RenderJobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;

import java.util.*;
import java.util.stream.Collectors;

// @Controller disabled - GraphQL schema conflict
public class AdminDashboardGraphQLResolver {

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardGraphQLResolver.class);

    private final RenderJobService renderJobService;
    private final BillingProjectionService billingProjectionService;
    private final UsageMeteringService usageMeteringService;
    private final ExtensionRegistryService extensionRegistryService;

    public AdminDashboardGraphQLResolver(RenderJobService renderJobService,
                                         BillingProjectionService billingProjectionService,
                                         UsageMeteringService usageMeteringService,
                                         ExtensionRegistryService extensionRegistryService) {
        this.renderJobService = renderJobService;
        this.billingProjectionService = billingProjectionService;
        this.usageMeteringService = usageMeteringService;
        this.extensionRegistryService = extensionRegistryService;
    }

    @QueryMapping
    public AdminDashboard adminDashboard(@Argument String range, GraphQLRequestContext context) {
        if (range == null || range.isBlank()) {
            range = "7d";
        }
        List<String> roles = context.roles();
        if (roles == null || (!roles.contains("ADMIN") && !roles.contains("DASHBOARD_ADMIN"))) {
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
            List<RenderJobResponse> jobs = renderJobService.list();
            int submitted = jobs.size();
            int completed = (int) jobs.stream()
                    .filter(j -> RenderJobStatus.COMPLETED.name().equals(j.status()))
                    .count();
            int failed = (int) jobs.stream()
                    .filter(j -> RenderJobStatus.FAILED.name().equals(j.status()))
                    .count();
            return new RenderStats(submitted, completed, failed, null);
        } catch (Exception e) {
            log.debug("Render stats resolution failed: {}", e.getMessage());
            return new RenderStats(0, 0, 0, null);
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
            List<UsageRecord> allUsage = usageMeteringService.getUsage(null, null);
            double totalAmount = allUsage.stream()
                    .mapToDouble(r -> r.quantity())
                    .sum();
            return new AdminBillingSummary(
                    new MoneyDto(totalAmount, "USD"),
                    new MoneyDto(totalAmount * 1.2, "USD"),
                    new MoneyDto(totalAmount * 0.8, "USD")
            );
        } catch (Exception e) {
            log.debug("Billing summary resolution failed: {}", e.getMessage());
            return new AdminBillingSummary(new MoneyDto(0, "USD"), new MoneyDto(0, "USD"), new MoneyDto(0, "USD"));
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
