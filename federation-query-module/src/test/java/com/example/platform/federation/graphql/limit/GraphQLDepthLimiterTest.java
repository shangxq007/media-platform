package com.example.platform.federation.graphql.limit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLDepthLimiterTest {

    @Test
    void createsWithConfiguredMaxDepth() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        props.setMaxDepth(15);
        GraphQLDepthLimiter limiter = new GraphQLDepthLimiter(props);

        assertNotNull(limiter);
    }

    @Test
    void createsWithDefaultMaxDepth() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        GraphQLDepthLimiter limiter = new GraphQLDepthLimiter(props);

        assertNotNull(limiter);
    }
}
