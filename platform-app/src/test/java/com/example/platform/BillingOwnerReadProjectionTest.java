package com.example.platform;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.billing.api.reads.BillingReadQuery;
import com.example.platform.billing.infrastructure.BillableUsageJdbcRepository;
import com.example.platform.billing.usage.*;
import com.example.platform.usage.api.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"app.outbox.dispatcher-enabled=false","storage.s3.enabled=false"})
@ActiveProfiles({"test","preview"})
class BillingOwnerReadProjectionTest extends PostgresTestContainerSupport {
 @Autowired ObservedRuntimeUsageEmissionPort observations;
 @Autowired BillingReadQuery query;@Autowired BillableUsageJdbcRepository store;@Autowired JdbcTemplate db;@Autowired PlatformTransactionManager tm;
 @AfterEach void clear(){TenantContext.clear();}
 @Test void durableRowsPreserveIdentityQuantityOrderAndCannotCrossTenant(){
  String tenant="br-"+UUID.randomUUID();var actor=CanonicalActor.user("u",tenant,Set.of(),"test");TenantContext.set(tenant);
  assertTrue(query.usage(actor,tenant).isEmpty());var now=Instant.now();
  for(int i:List.of(2,1)){
   String id=tenant+"-"+i;var qty=new UsageQuantity(i,UsageUnit.COUNT);
   observations.emit(new ObservedRuntimeUsage("o-"+id,tenant,null,new CanonicalActorRef("u","USER"),new OperationRef("op-"+id,"attempt"),null,new ProviderRef("test"),"test",UsageDimension.REQUEST,qty,RuntimeOutcome.SUCCEEDED,now,now,now,UsageProvenance.REPORTED,"test",id,"trace","obs-"+id));
   store.append(new BillableUsage(id,tenant,new CanonicalActorRef("u","USER"),"o-"+id,UsageDimension.REQUEST,qty,"m",UsageDimension.REQUEST,qty,"r","v1",MeteringTransformationKind.IDENTITY,"identity",now,now.plusSeconds(i),"i-"+id,"trace","source"));
  }
  var before=db.queryForList("select billable_usage_id from billable_usage where tenant_id=? order by metered_at",String.class,tenant);
  assertEquals(List.of(tenant+"-1",tenant+"-2"),query.usage(actor,tenant).stream().map(BillingReadQuery.Usage::recordId).toList());
  assertEquals(List.of(1L,2L),query.usage(actor,tenant).stream().map(r->r.quantity().baseUnits()).toList());
  assertThrows(com.example.platform.shared.web.PlatformException.class,()->query.usage(actor,"foreign"));
  new TransactionTemplate(tm).executeWithoutResult(s->{db.execute("alter table billable_usage rename to ep22_hidden_billable");assertThrows(org.springframework.dao.DataAccessException.class,()->query.usage(actor,tenant));s.setRollbackOnly();});
  assertEquals(before,query.usage(actor,tenant).stream().map(BillingReadQuery.Usage::recordId).toList());
  assertEquals(before,db.queryForList("select billable_usage_id from billable_usage where tenant_id=? order by metered_at",String.class,tenant));
 }
}
