package com.example.platform.timeline.internal;

public record EntityRef(EntityKind kind, String id) {

    public String key() {
        return kind.name() + ":" + id;
    }
}
