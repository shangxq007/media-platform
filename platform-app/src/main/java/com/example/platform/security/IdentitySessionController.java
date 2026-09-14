package com.example.platform.security;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.*;
import org.springframework.web.bind.annotation.*;
/** Current validated membership; not a client-controlled scope selection authority. */
@RestController
public class IdentitySessionController {
    private final CanonicalActorResolver actors;
    public IdentitySessionController(CanonicalActorResolver actors){this.actors=actors;}
    @GetMapping("/api/identity/session")
    public CanonicalActor current(){return actors.resolveCurrentActor().orElseThrow(()->new PlatformException(CommonErrorCode.AUTHENTICATION_REQUIRED,"Authenticated membership required"));}
}
