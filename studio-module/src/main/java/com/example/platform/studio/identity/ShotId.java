package com.example.platform.studio.identity;
public record ShotId(String value) implements StudioId { public ShotId { value = StudioId.requireValid(value, "ShotId"); } }
