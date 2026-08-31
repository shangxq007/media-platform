package com.example.platform.studio.identity;
public record CameraPlanVersionId(String value) implements StudioId { public CameraPlanVersionId { value=StudioId.requireValid(value,"CameraPlanVersionId"); } }
