package com.example.platform.federation.graphql.limit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLQueryComplexityLimiterTest {

    @Test
    void createsWithConfiguredMaxComplexity() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        props.setMaxComplexity(500);
        GraphQLQueryComplexityLimiter limiter = new GraphQLQueryComplexityLimiter(props);

        assertNotNull(limiter);
    }

    @Test
    void createsWithDefaultMaxComplexity() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        GraphQLQueryComplexityLimiter limiter = new GraphQLQueryComplexityLimiter(props);

        assertNotNull(limiter);
    }
}
