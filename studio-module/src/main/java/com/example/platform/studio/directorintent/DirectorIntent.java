package com.example.platform.studio.directorintent;
import com.example.platform.studio.identity.DirectorIntentId;
import com.example.platform.studio.scope.StudioScope;
public record DirectorIntent(DirectorIntentId id,StudioScope scope){public DirectorIntent{if(id==null||scope==null)throw new IllegalArgumentException("director intent identity and scope are required");}}
