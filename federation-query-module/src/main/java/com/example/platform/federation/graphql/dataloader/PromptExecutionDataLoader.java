package com.example.platform.federation.graphql.dataloader;
import com.example.platform.prompt.api.reads.PromptReadQuery;
import com.example.platform.federation.graphql.context.GraphQLReadScope;
import java.util.*;
import java.util.concurrent.*;
import org.dataloader.MappedBatchLoader;
import org.springframework.stereotype.Component;
@Component
public class PromptExecutionDataLoader implements MappedBatchLoader<String, List<Map<String,Object>>> {
 private final PromptReadQuery query; private final GraphQLReadScope scope;
 public PromptExecutionDataLoader(PromptReadQuery query,GraphQLReadScope scope){this.query=query;this.scope=scope;}
 public CompletionStage<Map<String,List<Map<String,Object>>>> load(Set<String> keys) {
  // Owner reads execute in the dispatching request context. No common-pool ThreadLocal loss.
  try {
   Map<String,List<Map<String,Object>>> result=new LinkedHashMap<>();
   if(!keys.isEmpty()){var actor=scope.actor();for(String id:keys)result.put(id,query.executions(actor,id).stream().map(v->Map.<String,Object>of("executionId",v.executionId(),"status",v.status()==null?"UNKNOWN":v.status(),"riskLevel",v.riskLevel()==null?"LOW":v.riskLevel(),"costEstimate",v.costEstimate(),"startedAt",v.startedAt()==null?"":v.startedAt().toString())).toList());}
   return CompletableFuture.completedFuture(Collections.unmodifiableMap(result));
  } catch(RuntimeException failure){return CompletableFuture.failedFuture(failure);}
 }
}
