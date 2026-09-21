package com.example.platform.federation.graphql.dataloader;
import com.example.platform.identity.api.reads.TenantReadQuery;
import com.example.platform.identity.app.*;
import com.example.platform.identity.domain.Tenant;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.TenantContext;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TenantDataLoaderTest {
 @AfterEach void clear(){TenantContext.clear();}
 @Test void ownerRejectsForeignTenantBeforeRepositoryAndMissingCurrentTenantIsAbsent(){
  var repo=mock(TenantRepository.class);var actor=CanonicalActor.user("u","t",Set.of(),"test");TenantContext.set("t");
  var loader=new TenantDataLoader(new TenantReadProjection(repo,()->Optional.of(actor)));
  assertThrows(CompletionException.class,()->loader.load(Set.of("foreign")).toCompletableFuture().join());verifyNoInteractions(repo);
  when(repo.findById("t")).thenReturn(Optional.empty());assertTrue(loader.load(Set.of("t")).toCompletableFuture().join().isEmpty());
  when(repo.findById("t")).thenReturn(Optional.of(new Tenant("t","Tenant",Tenant.TenantStatus.ACTIVE,java.time.Instant.now())));
  assertEquals("Tenant",loader.load(Set.of("t")).toCompletableFuture().join().get("t").get("name"));
 }
}
