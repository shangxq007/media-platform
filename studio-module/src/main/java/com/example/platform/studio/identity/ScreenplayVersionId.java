package com.example.platform.studio.identity;

public record ScreenplayVersionId(String value) implements StudioId {
    public ScreenplayVersionId { value = StudioId.requireValid(value, "ScreenplayVersionId"); }
}
