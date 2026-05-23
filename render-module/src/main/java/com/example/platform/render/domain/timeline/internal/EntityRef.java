package com.example.platform.render.domain.timeline.internal;

public record EntityRef(EntityKind kind, String id) {

    public String key() {
        return kind.name() + ":" + id;
    }
}
