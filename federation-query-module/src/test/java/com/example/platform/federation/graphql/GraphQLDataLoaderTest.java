package com.example.platform.federation.graphql;
import com.example.platform.federation.graphql.dataloader.UserDataLoader;
import com.example.platform.identity.api.reads.UserReadQuery;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GraphQLDataLoaderTest {
 @Test void distinctKeysAndMissingRecordsKeepIdentity(){
  var loader=new UserDataLoader(id->id.equals("missing")?Optional.empty():Optional.of(new UserReadQuery.View(id,"tenant",id+"-name","ACTIVE")));
  var result=loader.load(Set.of("a","b","missing")).toCompletableFuture().join();
  assertEquals(Set.of("a","b"),result.keySet());assertEquals("a",result.get("a").get("id"));assertEquals("b-name",result.get("b").get("username"));
 }
 @Test void mixedDeniedBatchFailsAndRetryDoesNotCacheFailure(){
  var fail=new AtomicBoolean(true);
  var loader=new UserDataLoader(id->{if(fail.get()&&id.equals("denied"))throw new SecurityException();return Optional.of(new UserReadQuery.View(id,"t",id,"ACTIVE"));});
  assertThrows(CompletionException.class,()->loader.load(new LinkedHashSet<>(List.of("valid","denied"))).toCompletableFuture().join());
  fail.set(false);assertEquals(Set.of("valid","denied"),loader.load(Set.of("valid","denied")).toCompletableFuture().join().keySet());
 }
 @Test void requestLocalCachesDoNotShareSameResourceId(){
  var a=org.dataloader.DataLoaderFactory.newMappedDataLoader(new UserDataLoader(id->Optional.of(new UserReadQuery.View(id,"a","A","ACTIVE")))) ;
  var b=org.dataloader.DataLoaderFactory.newMappedDataLoader(new UserDataLoader(id->Optional.of(new UserReadQuery.View(id,"b","B","ACTIVE")))) ;
  var av=a.load("same");var bv=b.load("same");a.dispatch();b.dispatch();assertEquals("A",av.join().get("username"));assertEquals("B",bv.join().get("username"));
 }
 @Test void emptyBatchMakesNoQuery(){assertEquals(Map.of(),new UserDataLoader(id->{throw new AssertionError();}).load(Set.of()).toCompletableFuture().join());}
}
