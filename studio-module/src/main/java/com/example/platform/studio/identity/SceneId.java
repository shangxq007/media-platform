package com.example.platform.studio.identity;

public record SceneId(String value) implements StudioId {
    public SceneId { value = StudioId.requireValid(value, "SceneId"); }
}
