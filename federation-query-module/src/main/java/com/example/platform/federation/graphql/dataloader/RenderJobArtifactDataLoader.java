package com.example.platform.federation.graphql.dataloader;
import com.example.platform.render.api.RenderReadQuery;
import java.util.*;
import java.util.concurrent.*;
import org.dataloader.MappedBatchLoader;
import org.springframework.stereotype.Component;
@Component
public class RenderJobArtifactDataLoader implements MappedBatchLoader<String, Map<String,Object>> {
 private final RenderReadQuery query;
 public RenderJobArtifactDataLoader(RenderReadQuery query){this.query=query;}
 public CompletionStage<Map<String,Map<String,Object>>> load(Set<String> keys) {
  // Owner reads execute in the dispatching request context. No common-pool ThreadLocal loss.
  try {
   Map<String,Map<String,Object>> result=new LinkedHashMap<>();
   for(String id:keys){var v=query.job(id);result.put(id,Map.of("id",v.id(),"projectId",v.projectId(),"timelineSnapshotId",v.timelineSnapshotId(),"profile",v.profile(),"status",v.status()));}
   return CompletableFuture.completedFuture(Collections.unmodifiableMap(result));
  } catch(RuntimeException failure){return CompletableFuture.failedFuture(failure);}
 }
}
