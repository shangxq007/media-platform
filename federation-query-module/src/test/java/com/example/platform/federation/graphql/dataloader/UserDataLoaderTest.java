package com.example.platform.federation.graphql.dataloader;
import com.example.platform.identity.api.reads.UserReadQuery;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UserDataLoaderTest {
 @Test void callerThreadScopeIsPreservedAndNeverMovedToCommonPool(){
  Thread caller=Thread.currentThread();
  var loader=new UserDataLoader(id->{assertSame(caller,Thread.currentThread());return Optional.of(new UserReadQuery.View(id,"t","name","ACTIVE"));});
  assertEquals("u",loader.load(Set.of("u")).toCompletableFuture().join().get("u").get("id"));
 }
}
