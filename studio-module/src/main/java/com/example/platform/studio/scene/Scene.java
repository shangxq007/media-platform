package com.example.platform.studio.scene;

import com.example.platform.studio.identity.SceneId;
import com.example.platform.studio.scope.StudioScope;

public record Scene(SceneId id, StudioScope scope) {
    public Scene { if (id == null || scope == null) throw new IllegalArgumentException("scene identity and scope are required"); }
}
