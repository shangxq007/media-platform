package com.example.platform.render.app.preview;

import com.example.platform.identity.api.authorization.*;
import com.example.platform.shared.authorization.*;
import com.example.platform.shared.web.TenantGuard;
import com.example.platform.storage.api.*;
import com.example.platform.render.api.request.PreviewUploadKey;
import com.example.platform.render.app.product.ProductRuntimeService;
import java.util.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Coordinates existing Storage and Product owners. A preview handle is not canonical Media/Artifact acceptance. */
@Service
public class PreviewMediaUploadService {
    private final CanonicalActorResolver actors;
    private final AuthorizationDecisionPort authorization;
    private final StorageFilePort storage;
    private final ProductRuntimeService products;
    public PreviewMediaUploadService(CanonicalActorResolver actors, AuthorizationDecisionPort authorization,
            StorageFilePort storage, ProductRuntimeService products) {
        this.actors=actors; this.authorization=authorization; this.storage=storage; this.products=products;
    }
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public Result upload(PreviewUploadKey key, byte[] bytes, String contentType) {
        Objects.requireNonNull(key);
        String tenant=TenantGuard.requireTenantId();
        var actor=actors.resolveCurrentActor().orElseThrow(()->new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,"authenticated preview actor required"));
        if(!tenant.equals(actor.tenantId()))throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,"preview actor tenant mismatch");
        authorization.requireAuthorized(new AuthorizationRequest(actor,
                new AuthorizationAction("WRITE",AuthorizationResourceType.TENANT,"Upload preview media"),
                new AuthorizableResourceRef(AuthorizationResourceType.TENANT,tenant,tenant,null,null),
                new AuthorizationContext("preview-upload",null,Map.of())));
        String identity=requestIdentity(tenant,actor.actorId(),key.value());
        var reference=storage.uploadPreview(StorageOwnershipScope.tenant(tenant),
                new IssuanceIdempotencyKey("preview:"+identity),bytes,contentType);
        var accepted=products.registerPreview(tenant,identity,reference);
        return new Result(accepted.ownerAssetId(),reference.fileSize());
    }
    private static String requestIdentity(String... values) {
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            for(String value:values) {
                byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public record Result(String mediaId,long size) {}
}
