package com.example.platform.federation.graphql.dataloader;
import com.example.platform.entitlement.api.EntitlementDecisionQuery;
import com.example.platform.entitlement.domain.*;
import com.example.platform.federation.graphql.context.GraphQLReadScope;
import com.example.platform.identity.api.workspace.*;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.TenantContext;
import java.util.*;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class EntitlementScopeDataLoaderTest {
 @AfterEach void clear(){TenantContext.clear();RequestContextHolder.resetRequestAttributes();}
 @Test void workspaceSelectorRequiresOwnerAuthorizationAndRemainsInDecision(){
  TenantContext.set("tenant");var request=new MockHttpServletRequest();request.addHeader("X-Workspace-Id","workspace");RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  var workspaces=mock(WorkspaceQueries.class);var query=mock(EntitlementDecisionQuery.class);
  var scope=new GraphQLReadScope(()->Optional.of(CanonicalActor.user("member","tenant",Set.of(),"test")),workspaces);
  var loader=new EntitlementGrantDataLoader(query,scope);
  when(workspaces.getWorkspace("workspace")).thenThrow(new SecurityException("denied"));
  assertThrows(CompletionException.class,()->loader.load(Set.of("render")).toCompletableFuture().join());verifyNoInteractions(query);
  doReturn(new WorkspaceResponse("workspace","tenant","W",null,"FREE","ACTIVE",null,null)).when(workspaces).getWorkspace("workspace");
  when(query.evaluate(any())).thenAnswer(call->{AccessCheckRequest r=call.getArgument(0);assertEquals("workspace",r.workspaceId());assertEquals("tenant",r.tenantId());assertEquals("member",r.userId());return new EntitlementDecision(true,"ALLOW","TIER","allowed","PRO",List.of(),null,null,null,null,null,List.of(),null,false);});
  assertEquals(true,loader.load(Set.of("render")).toCompletableFuture().join().get("render").get("allowed"));
 }
}
