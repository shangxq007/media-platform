package com.example.platform.studio.identity;public record StoryboardPanelId(String value)implements StudioId{public StoryboardPanelId{value=StudioId.requireValid(value,"StoryboardPanelId");}}
