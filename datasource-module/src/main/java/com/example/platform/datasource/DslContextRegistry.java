package com.example.platform.datasource;

import org.jooq.DSLContext;

import java.util.Map;
import java.util.Optional;

public class DslContextRegistry {
    private final Map<String, DSLContext> contexts;

    public DslContextRegistry(Map<String, DSLContext> contexts) {
        this.contexts = contexts;
    }

    public Optional<DSLContext> get(String name) {
        return Optional.ofNullable(contexts.get(name));
    }

    public Map<String, DSLContext> all() {
        return contexts;
    }
}
