package com.example.platform.studio.camera;
import com.example.platform.studio.identity.CameraPlanId;import com.example.platform.studio.scope.StudioScope;
public record CameraPlan(CameraPlanId id,StudioScope scope){public CameraPlan{if(id==null||scope==null)throw new IllegalArgumentException("camera plan identity and scope are required");}}
