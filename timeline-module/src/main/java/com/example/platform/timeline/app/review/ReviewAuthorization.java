package com.example.platform.timeline.app.review;
import com.example.platform.shared.authorization.*;
import com.example.platform.identity.api.authorization.*;
import com.example.platform.shared.web.TenantGuard;
import java.util.Map;
@org.springframework.stereotype.Component
public class ReviewAuthorization {
 private final CanonicalActorResolver actors;private final AuthorizationDecisionPort decisions;
 public ReviewAuthorization(CanonicalActorResolver actors,AuthorizationDecisionPort decisions){this.actors=actors;this.decisions=decisions;}
 public CanonicalActor require(String project,boolean write){
  String tenant=TenantGuard.requireTenantId();
  if(project==null||project.isBlank())throw new IllegalArgumentException("project required");
  var actor=actors.resolveCurrentActor().orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,"authenticated actor required"));
  if(!tenant.equals(actor.tenantId()))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"actor tenant mismatch");
  decisions.requireAuthorized(new AuthorizationRequest(actor,new AuthorizationAction(write?"WRITE":"READ",AuthorizationResourceType.PROJECT,"Timeline review"),
    new AuthorizableResourceRef(AuthorizationResourceType.PROJECT,project,tenant,project,null),new AuthorizationContext("timeline-review",project,Map.of())));
  return actor;
 }
}
