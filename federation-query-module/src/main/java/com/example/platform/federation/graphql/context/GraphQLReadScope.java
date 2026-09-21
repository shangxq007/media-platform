package com.example.platform.federation.graphql.context;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.*;
import org.springframework.stereotype.Component;
@Component
public class GraphQLReadScope {
 private final CanonicalActorResolver actors;
 public GraphQLReadScope(CanonicalActorResolver actors){this.actors=actors;}
 public CanonicalActor actor(){var a=actors.resolveCurrentActor().orElseThrow(()->new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION,"Authenticated actor required"));
  if(a.tenantId()==null || !a.tenantId().equals(TenantContext.get()))throw new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION,"Read scope mismatch");return a;}
}
