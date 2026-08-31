package com.example.platform.studio.shotplan;
import com.example.platform.studio.identity.ShotPlanId;
import com.example.platform.studio.scope.StudioScope;
public record ShotPlan(ShotPlanId id, StudioScope scope) { public ShotPlan { if(id==null||scope==null)throw new IllegalArgumentException("shot plan identity and scope are required"); } }
