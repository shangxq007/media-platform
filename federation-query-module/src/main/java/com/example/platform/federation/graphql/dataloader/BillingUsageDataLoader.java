package com.example.platform.federation.graphql.dataloader;
import com.example.platform.billing.api.reads.BillingReadQuery;
import com.example.platform.federation.graphql.context.GraphQLReadScope;
import java.util.*;
import java.util.concurrent.*;
import org.dataloader.MappedBatchLoader;
import org.springframework.stereotype.Component;
@Component
public class BillingUsageDataLoader implements MappedBatchLoader<String, List<Map<String,Object>>> {
 private final BillingReadQuery query; private final GraphQLReadScope scope;
 public BillingUsageDataLoader(BillingReadQuery query,GraphQLReadScope scope){this.query=query;this.scope=scope;}
 public CompletionStage<Map<String,List<Map<String,Object>>>> load(Set<String> keys) {
  // Owner reads execute in the dispatching request context. No common-pool ThreadLocal loss.
  try {
   Map<String,List<Map<String,Object>>> result=new LinkedHashMap<>();
   if(!keys.isEmpty()){var actor=scope.actor();for(String id:keys){var rows=query.usage(actor,id).stream().map(v->Map.<String,Object>of("id",v.recordId(),"meterKey",v.dimension().name(),"quantity",v.quantity().baseUnits(),"unit",v.quantity().unit().name(),"recordedAt",v.recordedAt()==null?"":v.recordedAt().toString())).toList();result.put(id,rows);}}
   return CompletableFuture.completedFuture(Collections.unmodifiableMap(result));
  } catch(RuntimeException failure){return CompletableFuture.failedFuture(failure);}
 }
}
