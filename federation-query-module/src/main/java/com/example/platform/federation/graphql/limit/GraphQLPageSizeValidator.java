package com.example.platform.federation.graphql.limit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GraphQLPageSizeValidator {
    private static final Logger log = LoggerFactory.getLogger(GraphQLPageSizeValidator.class);

    private final GraphQLQueryLimitProperties properties;

    public GraphQLPageSizeValidator(GraphQLQueryLimitProperties properties) {
        this.properties = properties;
    }

    public int validatePageSize(Integer requestedSize) {
        int size = requestedSize != null ? requestedSize : properties.getDefaultPageSize();
        if (size > properties.getMaxPageSize()) {
            log.warn("Requested page size {} exceeds max {}, clamping to max", size, properties.getMaxPageSize());
            return properties.getMaxPageSize();
        }
        if (size < 1) {
            return properties.getDefaultPageSize();
        }
        return size;
    }

    public int getDefaultPageSize() {
        return properties.getDefaultPageSize();
    }

    public int getMaxPageSize() {
        return properties.getMaxPageSize();
    }
}
