package com.example.platform.studio.identity;public record StoryboardId(String value)implements StudioId{public StoryboardId{value=StudioId.requireValid(value,"StoryboardId");}}
