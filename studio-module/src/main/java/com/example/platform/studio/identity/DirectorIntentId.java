package com.example.platform.studio.identity;
public record DirectorIntentId(String value) implements StudioId { public DirectorIntentId { value=StudioId.requireValid(value,"DirectorIntentId"); } }
