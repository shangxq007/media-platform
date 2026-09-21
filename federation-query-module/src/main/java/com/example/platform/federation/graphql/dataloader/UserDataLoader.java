package com.example.platform.federation.graphql.dataloader;
import com.example.platform.identity.api.reads.UserReadQuery;
import java.util.*;
import java.util.concurrent.*;
import org.dataloader.MappedBatchLoader;
import org.springframework.stereotype.Component;
@Component
public class UserDataLoader implements MappedBatchLoader<String, Map<String,Object>> {
 private final UserReadQuery query;
 public UserDataLoader(UserReadQuery query){this.query=query;}
 public CompletionStage<Map<String,Map<String,Object>>> load(Set<String> keys) {
  // Owner reads execute in the dispatching request context. No common-pool ThreadLocal loss.
  try {
   Map<String,Map<String,Object>> result=new LinkedHashMap<>();
   for(String id:keys) query.findById(id).ifPresent(v->result.put(id,Map.of("id",v.id(),"username",v.username(),"status",v.status())));
   return CompletableFuture.completedFuture(Collections.unmodifiableMap(result));
  } catch(RuntimeException failure){return CompletableFuture.failedFuture(failure);}
 }
}
