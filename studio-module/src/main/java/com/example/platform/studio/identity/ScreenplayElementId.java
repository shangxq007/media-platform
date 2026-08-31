package com.example.platform.studio.identity;

public record ScreenplayElementId(String value) implements StudioId {
    public ScreenplayElementId { value = StudioId.requireValid(value, "ScreenplayElementId"); }
}
