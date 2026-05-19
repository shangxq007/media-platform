package com.example.platform.federation.graphql.limit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLQueryLimitPropertiesTest {

    @Test
    void defaultValues() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();

        assertEquals(10, props.getMaxDepth());
        assertEquals(200, props.getMaxComplexity());
        assertEquals(100, props.getMaxPageSize());
        assertEquals(20, props.getDefaultPageSize());
    }

    @Test
    void settersUpdateValues() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        props.setMaxDepth(15);
        props.setMaxComplexity(500);
        props.setMaxPageSize(200);
        props.setDefaultPageSize(50);

        assertEquals(15, props.getMaxDepth());
        assertEquals(500, props.getMaxComplexity());
        assertEquals(200, props.getMaxPageSize());
        assertEquals(50, props.getDefaultPageSize());
    }
}
