package com.example.platform.studio.scope;

import com.example.platform.studio.identity.StudioId;

public record ProjectId(String value) {
    public ProjectId { value = StudioId.requireValid(value, "ProjectId"); }
}
