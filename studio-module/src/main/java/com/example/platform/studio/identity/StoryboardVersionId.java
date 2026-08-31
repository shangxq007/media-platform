package com.example.platform.studio.identity;public record StoryboardVersionId(String value)implements StudioId{public StoryboardVersionId{value=StudioId.requireValid(value,"StoryboardVersionId");}}
