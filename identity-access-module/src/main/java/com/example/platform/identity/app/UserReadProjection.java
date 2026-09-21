package com.example.platform.identity.app;
import com.example.platform.identity.api.reads.UserReadQuery;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.shared.web.*;
import java.util.Optional;
import org.springframework.stereotype.Service;
@Service
public class UserReadProjection implements UserReadQuery {
 private final UserRepository repository; private final CanonicalActorResolver actors;
 public UserReadProjection(UserRepository repository,CanonicalActorResolver actors){this.repository=repository;this.actors=actors;}
 public Optional<View> findById(String id){
  var actor=actors.resolveCurrentActor().orElseThrow(()->new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION,"Authenticated actor required"));
  if(actor.tenantId()==null || !actor.tenantId().equals(TenantContext.get())) throw new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION,"Tenant scope mismatch");
 return repository.findById(id).filter(r->actor.tenantId().equals(r.tenantId())).map(r->new View(r.id(),r.tenantId(),r.username(),r.status().name()));
 }
}
