package com.example.platform.federation.graphql.limit;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GraphQLQueryComplexityLimiter extends MaxQueryComplexityInstrumentation {
    private static final Logger log = LoggerFactory.getLogger(GraphQLQueryComplexityLimiter.class);

    public GraphQLQueryComplexityLimiter(GraphQLQueryLimitProperties properties) {
        super(properties.getMaxComplexity());
        log.info("GraphQL complexity limiter initialized with maxComplexity={}", properties.getMaxComplexity());
    }
}
