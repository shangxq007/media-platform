package com.example.platform.studio.identity;
public record CameraPlanId(String value) implements StudioId { public CameraPlanId { value=StudioId.requireValid(value,"CameraPlanId"); } }
