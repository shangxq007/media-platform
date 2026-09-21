package com.example.platform.federation.graphql;
import com.example.platform.federation.graphql.context.GraphQLReadScope;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.TenantContext;
import java.util.*;
public final class OwnerQueryFixtures {
 public static final CanonicalActor ACTOR=CanonicalActor.user("user-1","tenant-1",Set.of("ADMIN"),"test");
 public static GraphQLReadScope scope(){TenantContext.set("tenant-1");return new GraphQLReadScope(()->Optional.of(ACTOR),org.mockito.Mockito.mock(com.example.platform.identity.api.workspace.WorkspaceQueries.class));}
 public static com.example.platform.identity.api.reads.TenantReadQuery tenants(com.example.platform.identity.app.TenantRepository r){return new com.example.platform.identity.app.TenantReadProjection(r,()->Optional.of(ACTOR));}
 public static com.example.platform.identity.api.reads.UserReadQuery users(com.example.platform.identity.app.UserRepository r){return new com.example.platform.identity.app.UserReadProjection(r,()->Optional.of(ACTOR));}
 public static com.example.platform.identity.api.project.ProjectReadQuery projects(com.example.platform.identity.app.ProjectRepository r){return new com.example.platform.identity.api.project.ProjectReadQuery(){
  public java.util.List<com.example.platform.identity.api.dto.ProjectResponse> listProjects(String tenant){throw new UnsupportedOperationException();}
  public com.example.platform.identity.api.dto.ProjectResponse getProject(String tenant,String id){return r.findById(id).map(com.example.platform.identity.api.dto.ProjectResponse::from).orElse(null);}
 };}
}
