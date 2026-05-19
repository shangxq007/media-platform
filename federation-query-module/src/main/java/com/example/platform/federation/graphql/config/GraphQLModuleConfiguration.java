package com.example.platform.federation.graphql.config;

import com.example.platform.federation.graphql.limit.GraphQLQueryLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

@Configuration
@EnableConfigurationProperties(GraphQLQueryLimitProperties.class)
public class GraphQLModuleConfiguration {

    @Bean
    RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return builder -> builder
                .type("Query", typeWiring -> typeWiring
                        .dataFetcher("meOverview", env -> java.util.Map.of("stub", true))
                        .dataFetcher("navigationProfile", env -> java.util.Map.of("stub", true))
                        .dataFetcher("exportPanelState", env -> java.util.Map.of("stub", true))
                        .dataFetcher("promptTemplateDetail", env -> java.util.Map.of("stub", true))
                        .dataFetcher("myCapabilities", env -> java.util.List.of())
                        .dataFetcher("entitlementDecision", env -> java.util.Map.of("stub", true))
                        .dataFetcher("billingSummary", env -> java.util.Map.of("stub", true))
                        .dataFetcher("usageRecords", env -> java.util.Map.of("stub", true))
                        .dataFetcher("extensionOverview", env -> java.util.List.of())
                        .dataFetcher("monitoringFeedbackOverview", env -> java.util.Map.of("stub", true))
                        .dataFetcher("adminDashboard", env -> java.util.Map.of("stub", true))
                );
    }
}
