package com.example.platform.studio.identity;
public record ShotVersionId(String value) implements StudioId { public ShotVersionId { value = StudioId.requireValid(value, "ShotVersionId"); } }
