package com.example.platform.render.app;
import com.example.platform.render.api.event.*;
import com.example.platform.render.app.event.RenderLifecyclePublisher;
import com.example.platform.render.domain.*;
import com.example.platform.render.infrastructure.*;
import com.example.platform.artifact.app.*;
import com.example.platform.shared.events.ArtifactCreatedEvent;
import com.example.platform.shared.web.TenantGuard;
import com.example.platform.entitlement.api.commercial.*;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transaction boundaries for the existing Render state machine, history and typed facts. */
@Service
public class RenderJobLifecycleService {
 private final RenderJobRepository jobs;
 private final RenderJobStatusHistoryRepository history;
 private final RenderLifecyclePublisher events;
 private final RenderArtifactStorageService output;
 private final ArtifactOutputRead outputs;
 private final QuotaConsumptionPort quota;
 private final RenderJobStateMachine stateMachine=new RenderJobStateMachine();
 public RenderJobLifecycleService(RenderJobRepository jobs,RenderJobStatusHistoryRepository history,
        RenderLifecyclePublisher events,RenderArtifactStorageService output,ArtifactOutputRead outputs,QuotaConsumptionPort quota){
    this.jobs=jobs;this.history=history;this.events=events;this.output=output;this.outputs=outputs;this.quota=quota;
 }
 @Transactional
 public void transition(String jobId,String projectId,RenderJobStatus from,RenderJobStatus to,String errorCode){
    stateMachine.validateTransition(from,to);
    var job=jobs.requireJobRecord(jobId);var initiator=RenderJobRepository.initiatorFrom(job);
    if(!projectId.equals(job.get("project_id",String.class)))throw new IllegalArgumentException("Render project mismatch");
    if(jobs.compareAndSetStatus(jobId,projectId,initiator.tenantId(),from.name(),to.name())!=1)
        throw new IllegalStateException("stale Render transition");
    history.record(jobId,from.name(),to.name(),null,errorCode);
    events.publishEvent(new RenderJobStatusChangedEvent(jobId,projectId,from,to,Instant.now(),initiator));
 }
 @Transactional
 public ArtifactOutputReference complete(String tenantId,String jobId,String relativePath,String contentType){
    return completeInternal(tenantId,jobId,relativePath,contentType,java.util.Optional.empty());
 }
 @Transactional
 public ArtifactOutputReference completeReportedOutput(String tenantId,String jobId,String outputUri,String checksum){
    String prefix="localFsStorageProvider://";
    if(outputUri==null||!outputUri.startsWith(prefix)||!outputUri.endsWith(".mp4"))throw new IllegalArgumentException("unsupported reported output location/format");
    var expected=com.example.platform.shared.digest.ContentDigest.sha256(java.util.Objects.requireNonNull(checksum,"reported output checksum required"));
    return completeInternal(tenantId,jobId,outputUri.substring(prefix.length()),"video/mp4",java.util.Optional.of(expected));
 }
 private ArtifactOutputReference completeInternal(String tenantId,String jobId,String relativePath,String contentType,
        java.util.Optional<com.example.platform.shared.digest.ContentDigest> expected){
    TenantGuard.assertSameTenant(tenantId);
    var job=jobs.requireScopedJobForUpdate(tenantId,jobId);
    String projectId=job.get("project_id",String.class);
    var scope=new ArtifactScope(tenantId,projectId,jobId);
    var state=RenderJobStatus.valueOf(job.get("status",String.class));
    if(state==RenderJobStatus.COMPLETED){var accepted=outputs.find(scope).orElseThrow(()->new IllegalStateException("completed job lacks accepted output"));verifyExpected(accepted,expected);return accepted;}
    if(state!=RenderJobStatus.EXECUTING && state!=RenderJobStatus.COMPLETING)throw new IllegalStateException("Render job cannot accept output in "+state);
    if(state==RenderJobStatus.EXECUTING)transition(jobId,projectId,state,RenderJobStatus.COMPLETING,null);
    var accepted=output.uploadJobOutput(jobId,projectId,relativePath,contentType);
    if(!scope.equals(accepted.scope()))throw new IllegalArgumentException("accepted Artifact output scope mismatch");
    verifyExpected(accepted,expected);
    Instant now=Instant.now();YearMonth month=YearMonth.from(now.atZone(ZoneOffset.UTC));
    quota.consume(new QuotaConsumptionRequest(PrincipalRef.tenantScoped(tenantId,PrincipalType.ORGANIZATION,tenantId),
        "render.job.create",1,month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(),month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
        "render-job:"+jobId+":completion","render-job:"+jobId,"render completion "+jobId,now));
    transition(jobId,projectId,RenderJobStatus.COMPLETING,RenderJobStatus.COMPLETED,null);
    events.publishEvent(new ArtifactCreatedEvent(accepted.artifactId().value(),jobId,projectId,now));
    events.publishEvent(new RenderJobCompletedEvent(accepted,now,RenderJobRepository.initiatorFrom(job)));
    return accepted;
 }
 private void verifyExpected(ArtifactOutputReference accepted,java.util.Optional<com.example.platform.shared.digest.ContentDigest> expected){
    if(expected.isEmpty())return;
    try{
        byte[] bytes=outputs.read(accepted).bytes();
        String actual=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        if(!expected.get().matches(com.example.platform.shared.digest.ContentDigest.sha256(actual)))throw new IllegalArgumentException("reported output checksum mismatch");
    }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }

}
