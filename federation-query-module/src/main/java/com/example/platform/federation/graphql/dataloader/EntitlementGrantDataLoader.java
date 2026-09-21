package com.example.platform.federation.graphql.dataloader;
import com.example.platform.entitlement.api.EntitlementDecisionQuery;
import com.example.platform.entitlement.domain.AccessCheckRequest;
import com.example.platform.federation.graphql.context.GraphQLReadScope;
import java.util.*;
import java.util.concurrent.*;
import org.dataloader.MappedBatchLoader;
import org.springframework.stereotype.Component;
@Component
public class EntitlementGrantDataLoader implements MappedBatchLoader<String, Map<String,Object>> {
 private final EntitlementDecisionQuery query; private final GraphQLReadScope scope;
 public EntitlementGrantDataLoader(EntitlementDecisionQuery query,GraphQLReadScope scope){this.query=query;this.scope=scope;}
 public CompletionStage<Map<String,Map<String,Object>>> load(Set<String> keys) {
  // Owner reads execute in the dispatching request context. No common-pool ThreadLocal loss.
  try {
   Map<String,Map<String,Object>> result=new LinkedHashMap<>();
   if(!keys.isEmpty()){var actor=scope.actor();for(String id:keys){var d=query.evaluate(new AccessCheckRequest(actor.tenantId(),scope.workspaceId(),actor.actorId(),actor.actorType().name(),actor.actorId(),"check","FEATURE",id,id,null,null,"GRAPHQL",0L,Map.of()));result.put(id,Map.of("allowed",d.allowed(),"reasonCode",d.reasonCode()==null?"":d.reasonCode(),"tier",d.currentTier()==null?"":d.currentTier()));}}
   return CompletableFuture.completedFuture(Collections.unmodifiableMap(result));
  } catch(RuntimeException failure){return CompletableFuture.failedFuture(failure);}
 }
}
