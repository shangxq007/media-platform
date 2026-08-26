package com.example.platform.sandbox;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Exact environment values; ambient inheritance has no representation. */
@org.springframework.modulith.NamedInterface("API")
public record EnvironmentPolicy(Map<String, String> values) {
    public EnvironmentPolicy {
        Objects.requireNonNull(values, "values");
        TreeMap<String, String> copy = new TreeMap<>();
        values.forEach((name, value) -> {
            Objects.requireNonNull(name, "environment name");
            Objects.requireNonNull(value, "environment value");
            if (!name.matches("[A-Za-z_][A-Za-z0-9_]*") || value.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("invalid exact environment entry");
            }
            copy.put(name, value);
        });
        values = Map.copyOf(copy);
    }

    public static EnvironmentPolicy exact(Map<String, String> values) {
        return new EnvironmentPolicy(values);
    }
}
