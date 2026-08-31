package com.example.platform.studio.identity;
public record ShotPlanVersionId(String value) implements StudioId { public ShotPlanVersionId { value = StudioId.requireValid(value, "ShotPlanVersionId"); } }
