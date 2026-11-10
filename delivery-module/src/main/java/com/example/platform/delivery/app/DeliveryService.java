package com.example.platform.delivery.app;

import com.example.platform.artifact.app.*;
import com.example.platform.artifact.domain.*;
import com.example.platform.delivery.infrastructure.DeliveryRepository;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.render.api.GraphRenderLifecycle;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.timeline.RenderJobRevisionPinningService;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Delivery owns immutable selection and request identity, not media execution or Artifact state. */
@Service
public class DeliveryService {
    private final DeliveryAccess access;
    private final ArtifactProjectAuthorizationPort artifactAccess;
    private final CanonicalActorResolver actors;
    private final RenderJobService jobs;
    private final GraphRenderLifecycle lifecycle;
    private final RenderJobRevisionPinningService revisions;
    private final ArtifactApplicationQuery artifacts;
    private final ArtifactOutputRead outputs;
    private final DeliveryRepository records;
    private final TransactionTemplate creation;

    public DeliveryService(DeliveryAccess access, ArtifactProjectAuthorizationPort artifactAccess,
            CanonicalActorResolver actors, RenderJobService jobs, GraphRenderLifecycle lifecycle,
            RenderJobRevisionPinningService revisions, ArtifactApplicationQuery artifacts,
            ArtifactOutputRead outputs, DeliveryRepository records, PlatformTransactionManager transactions) {
        this.access=access; this.artifactAccess=artifactAccess; this.actors=actors; this.jobs=jobs;
        this.lifecycle=lifecycle; this.revisions=revisions; this.artifacts=artifacts; this.outputs=outputs; this.records=records;
        creation=new TransactionTemplate(transactions);
        creation.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        creation.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public record View(String deliveryId,String projectId,String sourceRenderJobId,String timelineRevisionId,
            String artifactId,String status,OffsetDateTime createdAt,String resultPath) {}
    public record Accepted(View delivery,boolean created) {}

    public Accepted create(String project,String job,String requestedArtifact,String key) {
        String tenant=TenantContext.get();
        authorize(tenant,project,true); // Required business authorization precedes any identity lookup.
        if(key==null || key.isBlank() || key.length()>200)throw failure(HttpStatus.BAD_REQUEST,"INVALID_IDEMPOTENCY_KEY");
        String normalized=key.trim();
        for(int retry=0;retry<5;retry++) {
            try { return creation.execute(tx -> {
                authorize(tenant,project,true);
                ArtifactOutputReference selected=select(tenant,project,job,requestedArtifact);
                // Official Artifact+Storage read verifies content, length, state and media representation.
                read(selected);
                String hash=requestHash(job,selected.artifactId().value());
                var existing=records.byKey(tenant,project,normalized);
                if(existing.isPresent())return replay(existing.get(),hash);
                var record=new DeliveryRecord("dlv_"+UUID.randomUUID().toString().replace("-",""),tenant,project,job,
                        selected.artifactId().value(),normalized,hash,actors.resolveCurrentActor().orElseThrow().actorId(),OffsetDateTime.now());
                if(records.insert(record))return new Accepted(view(records.find(tenant,project,record.id()).orElseThrow()),true);
                return replay(records.byKey(tenant,project,normalized).orElseThrow(),hash);
            }); } catch(RuntimeException e) {
                if(!serializationConflict(e))throw e;
            }
        }
        throw failure(HttpStatus.SERVICE_UNAVAILABLE,"DELIVERY_CONCURRENT_RETRY");
    }
    private Accepted replay(DeliveryRecord r,String hash) {
        if(!r.requestHash().equals(hash))throw failure(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT");
        return new Accepted(view(r),false);
    }
    public View get(String project,String id) {return view(require(project,id));}
    public List<View> list(String project,int limit) {
        String tenant=TenantContext.get();authorize(tenant,project,false);
        if(limit<1 || limit>100)throw failure(HttpStatus.BAD_REQUEST,"INVALID_LIMIT");
        return records.list(tenant,project,limit).stream().map(r->{ jobs.getByIdAndProject(tenant,project,r.sourceRenderJobId());return view(r); }).toList();
    }
    public ArtifactOutputRead.Content result(String project,String id) {
        var record=require(project,id);
        var selected=select(record.tenantId(),project,record.sourceRenderJobId(),record.artifactId());
        var content=read(selected);
        authorize(record.tenantId(),project,false); // Recheck permission changes during a potentially slow read.
        jobs.getByIdAndProject(record.tenantId(),project,record.sourceRenderJobId());
        return content;
    }
    private DeliveryRecord require(String project,String id) {
        String tenant=TenantContext.get();authorize(tenant,project,false);
        var r=records.find(tenant,project,id).orElseThrow(()->failure(HttpStatus.NOT_FOUND,"DELIVERY_NOT_FOUND"));
        jobs.getByIdAndProject(tenant,project,r.sourceRenderJobId());return r;
    }
    private void authorize(String tenant,String project,boolean create) {
        access.require(tenant,project,false);
        if(create)access.require(tenant,project,true);
        artifactAccess.requireRead(tenant,project);
    }
    private ArtifactOutputReference select(String tenant,String project,String job,String requested) {
        var source=jobs.getByIdAndProject(tenant,project,job);
        var state=lifecycle.find(tenant,project,job).orElseThrow(()->failure(HttpStatus.CONFLICT,"RENDER_RESULT_NOT_DELIVERABLE"));
        if(!"COMPLETED".equals(source.status()) || !"COMPLETED".equals(state.status()) || state.finalArtifactId()==null)
            throw failure(HttpStatus.CONFLICT,"RENDER_RESULT_NOT_DELIVERABLE");
        if(requested!=null && !requested.equals(state.finalArtifactId()))throw failure(HttpStatus.CONFLICT,"ARTIFACT_NOT_ACCEPTED_RESULT");
        var scope=new ArtifactScope(tenant,project,job);var id=new ArtifactId(state.finalArtifactId());
        var artifact=artifacts.findArtifact(scope,id).orElseThrow(()->failure(HttpStatus.CONFLICT,"ARTIFACT_UNAVAILABLE"));
        if(artifact.state()!=ArtifactState.AVAILABLE || artifact.artifactKind()!=ArtifactKind.RENDER_MASTER)
            throw failure(HttpStatus.CONFLICT,"ARTIFACT_UNAVAILABLE");
        var reference=outputs.find(scope).orElseThrow(()->failure(HttpStatus.CONFLICT,"ARTIFACT_UNAVAILABLE"));
        if(!reference.artifactId().equals(id))throw failure(HttpStatus.CONFLICT,"ARTIFACT_NOT_ACCEPTED_RESULT");
        return reference;
    }
    private ArtifactOutputRead.Content read(ArtifactOutputReference r) {
        try {return outputs.read(r);}
        catch(IllegalArgumentException|IllegalStateException|ArtifactApplicationService.ArtifactAccessException e) {throw failure(HttpStatus.CONFLICT,"ARTIFACT_UNAVAILABLE");}
    }
    private View view(DeliveryRecord r) {
        return new View(r.id(),r.projectId(),r.sourceRenderJobId(),revisions.getPinnedRevisionId(r.sourceRenderJobId()),
                r.artifactId(),"READY",r.createdAt(),"/api/projects/"+org.springframework.web.util.UriUtils.encodePathSegment(r.projectId(),StandardCharsets.UTF_8)+"/deliveries/"+r.id()+"/result");
    }
    public static String requestHash(String job,String artifact) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                ("delivery-v1\n"+job.length()+":"+job+artifact.length()+":"+artifact).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static boolean serializationConflict(Throwable e) {
        for(Throwable c=e;c!=null;c=c.getCause())if(c instanceof java.sql.SQLException sql
                && ("40001".equals(sql.getSQLState()) || "40P01".equals(sql.getSQLState())))return true;
        return false;
    }
    private static DeliveryFailure failure(HttpStatus status,String code){return new DeliveryFailure(status,code);}
}
