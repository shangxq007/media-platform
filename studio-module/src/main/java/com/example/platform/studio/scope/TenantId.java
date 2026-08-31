package com.example.platform.studio.scope;

import com.example.platform.studio.identity.StudioId;

public record TenantId(String value) {
    public TenantId { value = StudioId.requireValid(value, "TenantId"); }
}
