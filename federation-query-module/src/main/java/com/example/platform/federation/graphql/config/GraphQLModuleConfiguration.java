package com.example.platform.federation.graphql.config;

import com.example.platform.federation.graphql.limit.GraphQLQueryLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GraphQLQueryLimitProperties.class)
@ComponentScan(basePackages = "com.example.platform.federation.graphql.resolver")
public class GraphQLModuleConfiguration {
}
