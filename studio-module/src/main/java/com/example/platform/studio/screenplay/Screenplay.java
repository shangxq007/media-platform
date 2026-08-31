package com.example.platform.studio.screenplay;

import com.example.platform.studio.identity.ScreenplayId;
import com.example.platform.studio.scope.StudioScope;

public record Screenplay(ScreenplayId id, StudioScope scope) {
    public Screenplay { if (id == null || scope == null) throw new IllegalArgumentException("screenplay identity and scope are required"); }
}
