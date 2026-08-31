package com.example.platform.studio.identity;public record ShotSceneVersionId(String value)implements StudioId{public ShotSceneVersionId{value=StudioId.requireValid(value,"ShotSceneVersionId");}}
