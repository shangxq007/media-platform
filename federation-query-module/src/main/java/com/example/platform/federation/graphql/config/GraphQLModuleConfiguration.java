package com.example.platform.federation.graphql.config;

import com.example.platform.federation.graphql.limit.GraphQLQueryLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

@Configuration
@EnableConfigurationProperties(GraphQLQueryLimitProperties.class)
@ComponentScan(basePackages = "com.example.platform.federation.graphql.resolver")
public class GraphQLModuleConfiguration {

    @Bean
    RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return builder -> builder
                .type("Query", typeWiring -> typeWiring
                        .dataFetcher("navigationProfile", env -> java.util.Map.of("stub", true))
                        .dataFetcher("myCapabilities", env -> java.util.List.of())
                        .dataFetcher("entitlementDecision", env -> java.util.Map.of("stub", true))
                        .dataFetcher("billingSummary", env -> java.util.Map.of("stub", true))
                        .dataFetcher("usageRecords", env -> java.util.Map.of("stub", true))
                );
    }
}
