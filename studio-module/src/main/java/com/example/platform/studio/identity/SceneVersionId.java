package com.example.platform.studio.identity;

public record SceneVersionId(String value) implements StudioId {
    public SceneVersionId { value = StudioId.requireValid(value, "SceneVersionId"); }
}
