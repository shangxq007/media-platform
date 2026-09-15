package com.example.platform.render.app;

import com.example.platform.render.api.context.*;
import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.identity.api.project.*;
import com.example.platform.identity.api.authorization.*;
import com.example.platform.entitlement.api.commercial.*;
import com.example.platform.shared.authorization.*;
import com.example.platform.shared.commercial.*;
import com.example.platform.shared.web.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RenderAcceptanceContextService implements ExecutionContextQueries {
    private final ProjectScopeQueries projects;private final CanonicalActorResolver actors;
    private final AuthorizationDecisionPort authorization;private final CommercialAdmissionPort admission;
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    public RenderAcceptanceContextService(ProjectScopeQueries projects,CanonicalActorResolver actors,AuthorizationDecisionPort authorization,
            CommercialAdmissionPort admission,JdbcTemplate jdbc,ObjectMapper json) {
        this.projects=projects;this.actors=actors;this.authorization=authorization;this.admission=admission;this.jdbc=jdbc;this.json=json;
    }
    public record Prepared(CanonicalActor actor,ProjectScope scope,CommercialDecision decision) {}
    public Prepared prepare(String tenant,String project,RenderInitiator initiator,String workspaceHint,String mode) {
        if(mode!=null&&!"TENANT_ORGANIZATION".equals(mode))throw new PlatformException(CommonErrorCode.INVALID_REQUEST,"Unsupported allocation mode");
        var prepared=prepare(tenant,project,initiator);
        if(workspaceHint!=null&&!workspaceHint.equals(prepared.scope().workspaceId()))throw new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION,"Workspace hint mismatch");
        return prepared;
    }
    public Prepared prepare(String tenant,String project,RenderInitiator initiator) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Acceptance transaction required");
        var actor=initiator.actorType()==ActorType.SYSTEM?CanonicalActor.system(initiator.actorId(),initiator.tenantId()):actors.resolveCurrentActor().orElseThrow(()->new PlatformException(CommonErrorCode.AUTHENTICATION_REQUIRED,"Actor required"));
        if(!tenant.equals(actor.tenantId())||!initiator.actorId().equals(actor.actorId())||initiator.actorType()!=actor.actorType())throw new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION,"Initiator scope mismatch");
        if(actor.actorType()==ActorType.USER&&actor.accountId()==null)throw new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION,"Account mapping required");
        var scope=projects.resolveForAcceptance(tenant,project);
        authorization.requireAuthorized(new AuthorizationRequest(actor,
                new AuthorizationAction(actor.isSystem()?"system.render.submit":"WRITE",AuthorizationResourceType.PROJECT,"Submit project task"),
                new AuthorizableResourceRef(AuthorizationResourceType.PROJECT,project,tenant,project,null),
                new AuthorizationContext("render-acceptance",scope.workspaceId(),Map.of())));
        Instant now=Instant.now();YearMonth month=YearMonth.from(now.atZone(ZoneOffset.UTC));
        var decision=admission.decide(new CommercialAdmissionRequest(PrincipalRef.tenantScoped(tenant,PrincipalType.ORGANIZATION,tenant),
                "render.submit","render.job.create","render.job.create",1,month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(),"render-submit:"+project,now));
        if(!decision.allowed())throw new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION,"Commercial admission: "+decision.reason());
        if(!decision.principal().equals(PrincipalRef.tenantScoped(tenant,PrincipalType.ORGANIZATION,tenant)))throw new IllegalStateException("Consumption subject changed");
        return new Prepared(actor,scope,decision);
    }
    public void persist(String job,String snapshot,Prepared prepared) {
        var a=prepared.actor();var s=prepared.scope();
        // Final admission is repeated by its owner under grant locks, held until this task commits.
        Instant now=Instant.now();YearMonth month=YearMonth.from(now.atZone(ZoneOffset.UTC));
        var d=admission.decideForAcceptance(new CommercialAdmissionRequest(prepared.decision().principal(),
                "render.submit","render.job.create","render.job.create",1,
                month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(),"render-submit:"+s.projectId(),now));
        if(!d.allowed())throw new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION,"Commercial admission: "+d.reason());
        var grant=java.util.Objects.requireNonNull(d.grantFacts(),"Admission grant facts required");
        String grantId=grant.grantId();
        var context=new AcceptedExecutionContext(1,job,a.actorId(),a.actorType(),a.accountId(),a.actorType()==ActorType.USER?a.actorId():null,
                s.tenantId(),s.workspaceId(),s.projectId(),"ORGANIZATION",s.tenantId(),"TENANT_ORGANIZATION",null,d.authorityVersion(),grantId,grant.version(),d.evidence(),snapshot,d.decidedAt());
        try {jdbc.update("insert into render_execution_context(job_id,tenant_id,workspace_id,project_id,context_json,created_at) values (?,?,?,?,?::jsonb,now())",job,s.tenantId(),s.workspaceId(),s.projectId(),json.writeValueAsString(context));}
        catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException("Cannot persist execution facts",ex);}
    }
    public AcceptedExecutionContext get(String tenant,String project,String job) {
        var rows=jdbc.queryForList("select context_json::text from render_execution_context where job_id=? and tenant_id=? and project_id=?",String.class,job,tenant,project);
        if(rows.size()!=1)throw new PlatformException(CommonErrorCode.CONFLICT,"Historical task context unresolved");
        String value=rows.getFirst();
        try {
            var restored=json.readValue(value,AcceptedExecutionContext.class);
            if(!tenant.equals(restored.tenantId())||!project.equals(restored.projectId())||!job.equals(restored.jobId()))
                throw new IllegalStateException("Persisted context key/scope mismatch");
            return restored;
        }
        catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException("Invalid persisted execution facts",ex);}
    }
}
