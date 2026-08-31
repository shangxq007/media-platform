package com.example.platform.studio.shot;
import com.example.platform.studio.identity.ShotId;
import com.example.platform.studio.scope.StudioScope;
public record Shot(ShotId id, StudioScope scope) { public Shot { if (id == null || scope == null) throw new IllegalArgumentException("shot identity and scope are required"); } }
