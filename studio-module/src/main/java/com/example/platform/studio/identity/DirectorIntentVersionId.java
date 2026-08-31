package com.example.platform.studio.identity;
public record DirectorIntentVersionId(String value) implements StudioId { public DirectorIntentVersionId { value=StudioId.requireValid(value,"DirectorIntentVersionId"); } }
