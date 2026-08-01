package com.example.platform.render.ir;

import java.util.Objects;

/**
 * A project identifier within the IR.
 */
public record Project(String id, String name) {
    public Project {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
