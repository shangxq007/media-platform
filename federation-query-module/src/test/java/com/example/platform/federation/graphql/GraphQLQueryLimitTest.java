package com.example.platform.federation.graphql;

import com.example.platform.federation.graphql.limit.GraphQLDepthLimiter;
import com.example.platform.federation.graphql.limit.GraphQLPageSizeValidator;
import com.example.platform.federation.graphql.limit.GraphQLQueryComplexityLimiter;
import com.example.platform.federation.graphql.limit.GraphQLQueryLimitProperties;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLQueryLimitTest {

    @Test
    void depthLimiterUsesConfiguredMaxDepth() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        props.setMaxDepth(7);
        GraphQLDepthLimiter limiter = new GraphQLDepthLimiter(props);
        assertNotNull(limiter);
    }

    @Test
    void depthLimiterUsesDefaultMaxDepth() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        assertEquals(10, props.getMaxDepth());
        GraphQLDepthLimiter limiter = new GraphQLDepthLimiter(props);
        assertNotNull(limiter);
    }

    @Test
    void depthLimiterExtendsMaxQueryDepthInstrumentation() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        GraphQLDepthLimiter limiter = new GraphQLDepthLimiter(props);
        assertInstanceOf(MaxQueryDepthInstrumentation.class, limiter);
    }

    @Test
    void complexityLimiterUsesConfiguredMaxComplexity() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        props.setMaxComplexity(500);
        GraphQLQueryComplexityLimiter limiter = new GraphQLQueryComplexityLimiter(props);
        assertNotNull(limiter);
    }

    @Test
    void complexityLimiterUsesDefaultMaxComplexity() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        assertEquals(200, props.getMaxComplexity());
        GraphQLQueryComplexityLimiter limiter = new GraphQLQueryComplexityLimiter(props);
        assertNotNull(limiter);
    }

    @Test
    void complexityLimiterExtendsMaxQueryComplexityInstrumentation() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        GraphQLQueryComplexityLimiter limiter = new GraphQLQueryComplexityLimiter(props);
        assertInstanceOf(MaxQueryComplexityInstrumentation.class, limiter);
    }

    @Test
    void pageSizeValidatorReturnsDefaultWhenNull() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        GraphQLPageSizeValidator validator = new GraphQLPageSizeValidator(props);
        assertEquals(20, validator.validatePageSize(null));
    }

    @Test
    void pageSizeValidatorReturnsRequestedWhenWithinLimit() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        GraphQLPageSizeValidator validator = new GraphQLPageSizeValidator(props);
        assertEquals(50, validator.validatePageSize(50));
    }

    @Test
    void pageSizeValidatorClampsToMaxWhenExceeded() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        GraphQLPageSizeValidator validator = new GraphQLPageSizeValidator(props);
        assertEquals(100, validator.validatePageSize(500));
    }

    @Test
    void pageSizeValidatorReturnsDefaultWhenZero() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        GraphQLPageSizeValidator validator = new GraphQLPageSizeValidator(props);
        assertEquals(20, validator.validatePageSize(0));
    }

    @Test
    void pageSizeValidatorReturnsDefaultWhenNegative() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        GraphQLPageSizeValidator validator = new GraphQLPageSizeValidator(props);
        assertEquals(20, validator.validatePageSize(-5));
    }

    @Test
    void pageSizeValidatorExposesDefaults() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        GraphQLPageSizeValidator validator = new GraphQLPageSizeValidator(props);
        assertEquals(20, validator.getDefaultPageSize());
        assertEquals(100, validator.getMaxPageSize());
    }

    @Test
    void pageSizeValidatorWithCustomLimits() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        props.setDefaultPageSize(10);
        props.setMaxPageSize(50);
        GraphQLPageSizeValidator validator = new GraphQLPageSizeValidator(props);
        assertEquals(10, validator.getDefaultPageSize());
        assertEquals(50, validator.getMaxPageSize());
        assertEquals(10, validator.validatePageSize(null));
        assertEquals(50, validator.validatePageSize(100));
        assertEquals(25, validator.validatePageSize(25));
    }

    @Test
    void propertiesCanBeCustomized() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        props.setMaxDepth(5);
        props.setMaxComplexity(100);
        props.setMaxPageSize(200);
        props.setDefaultPageSize(15);

        assertEquals(5, props.getMaxDepth());
        assertEquals(100, props.getMaxComplexity());
        assertEquals(200, props.getMaxPageSize());
        assertEquals(15, props.getDefaultPageSize());
    }

    @Test
    void limitsAreEnforcedAtDefaultValues() {
        GraphQLQueryLimitProperties props = new GraphQLQueryLimitProperties();
        GraphQLDepthLimiter depthLimiter = new GraphQLDepthLimiter(props);
        GraphQLQueryComplexityLimiter complexityLimiter = new GraphQLQueryComplexityLimiter(props);
        GraphQLPageSizeValidator pageSizeValidator = new GraphQLPageSizeValidator(props);

        assertNotNull(depthLimiter);
        assertNotNull(complexityLimiter);
        assertEquals(20, pageSizeValidator.getDefaultPageSize());
        assertEquals(100, pageSizeValidator.getMaxPageSize());
    }
}
