package com.example.platform.federation.graphql.config;

import graphql.schema.Coercing;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.time.Instant;
import java.util.Map;

@Configuration
public class GraphQLScalarConfiguration {

    @Bean
    RuntimeWiringConfigurer graphQlScalarConfigurer() {
        return builder -> builder
                .scalar(mapScalar())
                .scalar(dateTimeScalar());
    }

    private static GraphQLScalarType mapScalar() {
        return GraphQLScalarType.newScalar()
                .name("Map")
                .description("Generic JSON map")
                .coercing(new Coercing<Map<String, Object>, Map<String, Object>>() {
                    @Override
                    public Map<String, Object> serialize(Object dataFetcherResult) {
                        if (dataFetcherResult instanceof Map<?, ?> map) {
                            return (Map<String, Object>) map;
                        }
                        throw new CoercingSerializeException("Expected Map");
                    }

                    @Override
                    public Map<String, Object> parseValue(Object input) {
                        return serialize(input);
                    }

                    @Override
                    public Map<String, Object> parseLiteral(Object input) {
                        return parseValue(input);
                    }
                })
                .build();
    }

    private static GraphQLScalarType dateTimeScalar() {
        return GraphQLScalarType.newScalar()
                .name("DateTime")
                .description("ISO-8601 date time")
                .coercing(new Coercing<Map<String, Object>, Map<String, Object>>() {
                    @Override
                    public Map<String, Object> serialize(Object dataFetcherResult) {
                        return toDateTimeMap(dataFetcherResult);
                    }

                    @Override
                    public Map<String, Object> parseValue(Object input) {
                        return toDateTimeMap(input);
                    }

                    @Override
                    public Map<String, Object> parseLiteral(Object input) {
                        return parseValue(input);
                    }

                    private Map<String, Object> toDateTimeMap(Object value) {
                        if (value instanceof Map<?, ?> map && map.containsKey("iso")) {
                            return (Map<String, Object>) map;
                        }
                        if (value instanceof String iso) {
                            long epoch = Instant.parse(iso).toEpochMilli();
                            return Map.of("iso", iso, "epochMillis", (int) Math.min(epoch, Integer.MAX_VALUE));
                        }
                        if (value instanceof Number n) {
                            long epoch = n.longValue();
                            return Map.of(
                                    "iso",
                                    Instant.ofEpochMilli(epoch).toString(),
                                    "epochMillis",
                                    (int) Math.min(epoch, Integer.MAX_VALUE));
                        }
                        throw new CoercingSerializeException("Invalid DateTime value");
                    }
                })
                .build();
    }
}
