package com.example.platform.federation.graphql.context;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.*;
import org.springframework.stereotype.Component;
@Component
public class GraphQLReadScope {
 private final CanonicalActorResolver actors;
 private final com.example.platform.identity.api.workspace.WorkspaceQueries workspaces;
 public GraphQLReadScope(CanonicalActorResolver actors,com.example.platform.identity.api.workspace.WorkspaceQueries workspaces){this.actors=actors;this.workspaces=workspaces;}
 public String workspaceId(){
  var attributes=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
  if(!(attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet))return null;
  String selected=servlet.getRequest().getHeader("X-Workspace-Id");
  if(selected==null || selected.isBlank())return null;
  var workspace=workspaces.getWorkspace(selected);
  if(!actor().tenantId().equals(workspace.tenantId()))throw new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION,"Workspace scope mismatch");
  return workspace.id();
 }
 public CanonicalActor actor(){var a=actors.resolveCurrentActor().orElseThrow(()->new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION,"Authenticated actor required"));
  if(a.tenantId()==null || !a.tenantId().equals(TenantContext.get()))throw new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION,"Read scope mismatch");return a;}
}
