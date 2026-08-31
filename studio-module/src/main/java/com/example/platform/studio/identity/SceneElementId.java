package com.example.platform.studio.identity;public record SceneElementId(String value)implements StudioId{public SceneElementId{value=StudioId.requireValid(value,"SceneElementId");}}
