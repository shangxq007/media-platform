package com.example.platform.federation.graphql.limit;

import graphql.analysis.MaxQueryDepthInstrumentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GraphQLDepthLimiter extends MaxQueryDepthInstrumentation {
    private static final Logger log = LoggerFactory.getLogger(GraphQLDepthLimiter.class);

    public GraphQLDepthLimiter(GraphQLQueryLimitProperties properties) {
        super(properties.getMaxDepth());
        log.info("GraphQL depth limiter initialized with maxDepth={}", properties.getMaxDepth());
    }
}
