package com.example.platform.timeline.diff.merge;

public record EntityRef(EntityKind kind, String id) {

    public String key() {
        return kind.name() + ":" + id;
    }
}
