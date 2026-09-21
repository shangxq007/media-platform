package com.example.platform.identity.app;
import com.example.platform.identity.api.reads.TenantReadQuery;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.shared.web.*;
import java.util.Optional;
import org.springframework.stereotype.Service;
@Service
public class TenantReadProjection implements TenantReadQuery {
 private final TenantRepository repository; private final CanonicalActorResolver actors;
 public TenantReadProjection(TenantRepository repository,CanonicalActorResolver actors){this.repository=repository;this.actors=actors;}
 public Optional<View> findById(String id){
  var actor=actors.resolveCurrentActor().orElseThrow(()->new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION,"Authenticated actor required"));
  if(actor.tenantId()==null || !actor.tenantId().equals(TenantContext.get())) throw new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION,"Tenant scope mismatch");
 if(!actor.tenantId().equals(id)) throw new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION,"Tenant scope mismatch");
  return repository.findById(id).map(r->new View(r.id(),r.name(),r.status().name()));
 }
}
