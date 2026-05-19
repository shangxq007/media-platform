package com.example.platform.federation.graphql;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLSchemaLoadTest {

    private static final List<String> SCHEMA_FILES = List.of(
            "graphql/common.graphqls",
            "graphql/me.graphqls",
            "graphql/render.graphqls",
            "graphql/admin-dashboard.graphqls",
            "graphql/prompt.graphqls",
            "graphql/navigation.graphqls",
            "graphql/entitlement.graphqls",
            "graphql/billing.graphqls",
            "graphql/extension.graphqls",
            "graphql/monitoring.graphqls"
    );

    @Test
    void allSchemaFilesExist() {
        for (String schemaFile : SCHEMA_FILES) {
            ClassPathResource resource = new ClassPathResource(schemaFile);
            assertTrue(resource.exists(), "Schema file must exist: " + schemaFile);
        }
    }

    @Test
    void allSchemaFilesAreReadable() throws IOException {
        for (String schemaFile : SCHEMA_FILES) {
            ClassPathResource resource = new ClassPathResource(schemaFile);
            assertTrue(resource.contentLength() > 0, "Schema file must not be empty: " + schemaFile);
        }
    }

    @Test
    void commonSchemaContainsSharedTypes() throws IOException {
        ClassPathResource resource = new ClassPathResource("graphql/common.graphqls");
        String content = new String(resource.getInputStream().readAllBytes());
        assertTrue(content.contains("type PageInfo"), "common.graphqls must define PageInfo");
        assertTrue(content.contains("type ErrorInfo"), "common.graphqls must define ErrorInfo");
        assertTrue(content.contains("type Money"), "common.graphqls must define Money");
        assertTrue(content.contains("type DateTime"), "common.graphqls must define DateTime");
        assertTrue(content.contains("type Decision"), "common.graphqls must define Decision");
        assertTrue(content.contains("type Capability"), "common.graphqls must define Capability");
        assertTrue(content.contains("scalar Map"), "common.graphqls must define Map scalar");
    }

    @Test
    void meSchemaContainsMeOverview() throws IOException {
        ClassPathResource resource = new ClassPathResource("graphql/me.graphqls");
        String content = new String(resource.getInputStream().readAllBytes());
        assertTrue(content.contains("meOverview"), "me.graphqls must define meOverview query");
        assertTrue(content.contains("type MeOverview"), "me.graphqls must define MeOverview type");
        assertTrue(content.contains("type TenantInfo"), "me.graphqls must define TenantInfo");
        assertTrue(content.contains("type WorkspaceInfo"), "me.graphqls must define WorkspaceInfo");
        assertTrue(content.contains("type NavigationRoute"), "me.graphqls must define NavigationRoute");
        assertTrue(content.contains("type BillingSummary"), "me.graphqls must define BillingSummary");
    }

    @Test
    void renderSchemaContainsExportPanelState() throws IOException {
        ClassPathResource resource = new ClassPathResource("graphql/render.graphqls");
        String content = new String(resource.getInputStream().readAllBytes());
        assertTrue(content.contains("exportPanelState"), "render.graphqls must define exportPanelState query");
        assertTrue(content.contains("type ExportPanelState"), "render.graphqls must define ExportPanelState type");
        assertTrue(content.contains("type ExportOption"), "render.graphqls must define ExportOption");
        assertTrue(content.contains("type WorkerStatus"), "render.graphqls must define WorkerStatus");
        assertTrue(content.contains("type ExportValidation"), "render.graphqls must define ExportValidation");
    }

    @Test
    void adminDashboardSchemaContainsAdminDashboard() throws IOException {
        ClassPathResource resource = new ClassPathResource("graphql/admin-dashboard.graphqls");
        String content = new String(resource.getInputStream().readAllBytes());
        assertTrue(content.contains("adminDashboard"), "admin-dashboard.graphqls must define adminDashboard query");
        assertTrue(content.contains("type AdminDashboard"), "admin-dashboard.graphqls must define AdminDashboard type");
        assertTrue(content.contains("type RenderStats"), "admin-dashboard.graphqls must define RenderStats");
        assertTrue(content.contains("type ProviderHealth"), "admin-dashboard.graphqls must define ProviderHealth");
    }

    @Test
    void promptSchemaContainsPromptTemplateDetail() throws IOException {
        ClassPathResource resource = new ClassPathResource("graphql/prompt.graphqls");
        String content = new String(resource.getInputStream().readAllBytes());
        assertTrue(content.contains("promptTemplateDetail"), "prompt.graphqls must define promptTemplateDetail query");
        assertTrue(content.contains("type PromptTemplateDetail"), "prompt.graphqls must define PromptTemplateDetail type");
        assertTrue(content.contains("type PromptVersion"), "prompt.graphqls must define PromptVersion");
        assertTrue(content.contains("type PromptExecution"), "prompt.graphqls must define PromptExecution");
    }

    @Test
    void navigationSchemaContainsNavigationProfile() throws IOException {
        ClassPathResource resource = new ClassPathResource("graphql/navigation.graphqls");
        String content = new String(resource.getInputStream().readAllBytes());
        assertTrue(content.contains("navigationProfile"), "navigation.graphqls must define navigationProfile query");
        assertTrue(content.contains("type NavigationProfile"), "navigation.graphqls must define NavigationProfile type");
    }

    @Test
    void entitlementSchemaContainsCapabilityQueries() throws IOException {
        ClassPathResource resource = new ClassPathResource("graphql/entitlement.graphqls");
        String content = new String(resource.getInputStream().readAllBytes());
        assertTrue(content.contains("myCapabilities"), "entitlement.graphqls must define myCapabilities query");
        assertTrue(content.contains("entitlementDecision"), "entitlement.graphqls must define entitlementDecision query");
    }

    @Test
    void billingSchemaContainsBillingQueries() throws IOException {
        ClassPathResource resource = new ClassPathResource("graphql/billing.graphqls");
        String content = new String(resource.getInputStream().readAllBytes());
        assertTrue(content.contains("billingSummary"), "billing.graphqls must define billingSummary query");
        assertTrue(content.contains("usageRecords"), "billing.graphqls must define usageRecords query");
    }

    @Test
    void extensionSchemaContainsExtensionOverview() throws IOException {
        ClassPathResource resource = new ClassPathResource("graphql/extension.graphqls");
        String content = new String(resource.getInputStream().readAllBytes());
        assertTrue(content.contains("extensionOverview"), "extension.graphqls must define extensionOverview query");
        assertTrue(content.contains("type ExtensionInfo"), "extension.graphqls must define ExtensionInfo type");
    }

    @Test
    void monitoringSchemaContainsMonitoringOverview() throws IOException {
        ClassPathResource resource = new ClassPathResource("graphql/monitoring.graphqls");
        String content = new String(resource.getInputStream().readAllBytes());
        assertTrue(content.contains("monitoringFeedbackOverview"), "monitoring.graphqls must define monitoringFeedbackOverview query");
        assertTrue(content.contains("type MonitoringStatus"), "monitoring.graphqls must define MonitoringStatus type");
        assertTrue(content.contains("type FeedbackSummary"), "monitoring.graphqls must define FeedbackSummary type");
    }

    @Test
    void allSchemaFilesContainQueryType() throws IOException {
        for (String schemaFile : SCHEMA_FILES) {
            if (schemaFile.equals("graphql/common.graphqls")) {
                continue;
            }
            ClassPathResource resource = new ClassPathResource(schemaFile);
            String content = new String(resource.getInputStream().readAllBytes());
            assertTrue(content.contains("type Query"),
                    "Schema file must define Query type: " + schemaFile);
        }
    }
}
