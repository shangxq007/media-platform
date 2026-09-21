package com.example.platform.billing.app;
import com.example.platform.billing.api.reads.BillingReadQuery;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.*;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
public class BillingReadProjection implements BillingReadQuery {
 private final UsageMeteringService metering;
 public BillingReadProjection(UsageMeteringService metering){this.metering=metering;}
 private void scope(CanonicalActor actor,String tenant){
  if(actor==null || actor.tenantId()==null || actor.actorId().isBlank() || !actor.tenantId().equals(tenant) || !tenant.equals(TenantContext.get()))
   throw new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION,"Billing read scope mismatch");
 }
 public List<Usage> usage(CanonicalActor actor,String tenant){scope(actor,tenant);return metering.getUsageByTenant(tenant).stream().map(r->new Usage(r.recordId(),r.dimension(),r.quantity(),r.recordedAt())).toList();}
 public Summary summary(CanonicalActor actor){scope(actor,actor==null?null:actor.tenantId());return new Summary("FREE","USD",0.0);}
}
