package com.example.platform.studio.identity;

public record ScreenplayId(String value) implements StudioId {
    public ScreenplayId { value = StudioId.requireValid(value, "ScreenplayId"); }
}
