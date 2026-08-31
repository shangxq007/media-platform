package com.example.platform.studio.identity;
public record ShotPlanId(String value) implements StudioId { public ShotPlanId { value = StudioId.requireValid(value, "ShotPlanId"); } }
