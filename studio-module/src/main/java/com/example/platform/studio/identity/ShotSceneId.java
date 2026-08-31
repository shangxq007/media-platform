package com.example.platform.studio.identity;public record ShotSceneId(String value)implements StudioId{public ShotSceneId{value=StudioId.requireValid(value,"ShotSceneId");}}
