package com.example.platform.federation.graphql.limit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLPageSizeValidatorTest {

    @Test
    void returnsDefaultWhenNull() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        GraphQLPageSizeValidator validator = new GraphQLPageSizeValidator(props);

        assertEquals(20, validator.validatePageSize(null));
    }

    @Test
    void returnsRequestedWhenWithinLimit() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        GraphQLPageSizeValidator validator = new GraphQLPageSizeValidator(props);

        assertEquals(50, validator.validatePageSize(50));
    }

    @Test
    void clampsToMaxWhenExceeded() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        GraphQLPageSizeValidator validator = new GraphQLPageSizeValidator(props);

        assertEquals(100, validator.validatePageSize(500));
    }

    @Test
    void returnsDefaultWhenZeroOrNegative() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        GraphQLPageSizeValidator validator = new GraphQLPageSizeValidator(props);

        assertEquals(20, validator.validatePageSize(0));
        assertEquals(20, validator.validatePageSize(-5));
    }

    @Test
    void exposesDefaults() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        GraphQLPageSizeValidator validator = new GraphQLPageSizeValidator(props);

        assertEquals(20, validator.getDefaultPageSize());
        assertEquals(100, validator.getMaxPageSize());
    }
}
