package com.example.platform.render.testsupport;

import com.example.platform.render.app.RenderAcceptanceContextService;
import com.example.platform.render.api.context.AcceptedExecutionContext;
import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.identity.api.project.ProjectScope;
import com.example.platform.entitlement.api.commercial.*;
import com.example.platform.shared.authorization.*;
import com.example.platform.shared.commercial.*;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.mock;

/** Explicit accepted-fact fixture for isolated pipeline tests, NOT auth/entitlement acceptance evidence. */
public final class AcceptedContextFixture extends RenderAcceptanceContextService {
    private final RenderJobRepository jobs;private final CommercialAdmissionPort commercial;
    private final Map<String,AcceptedExecutionContext> contexts=new HashMap<>();
    public AcceptedContextFixture(RenderJobRepository jobs,CommercialAdmissionPort commercial) {
        super(mock(com.example.platform.identity.api.project.ProjectScopeQueries.class),mock(com.example.platform.identity.api.authorization.CanonicalActorResolver.class),
                mock(com.example.platform.identity.api.authorization.AuthorizationDecisionPort.class),commercial,mock(org.springframework.jdbc.core.JdbcTemplate.class),new com.fasterxml.jackson.databind.ObjectMapper(),mock(EntitlementBasisQueries.class));
        this.jobs=jobs;this.commercial=commercial;
    }
    public static AcceptedContextFixture lifecycleOnly(RenderJobRepository jobs) {
        return new AcceptedContextFixture(jobs,r->new CommercialDecision(r.principal(),r.action(),true,CommercialDecisionReason.ALLOWED,List.of(new CommercialEvidenceRef("Fixture","CONTROL","explicit")),"fixture",r.traceId(),r.decidedAt()));
    }
    @Override public Prepared prepare(String tenant,String project,RenderInitiator initiator) {
        if(!jobs.findProjectTenantId(project).filter(tenant::equals).isPresent())throw new IllegalArgumentException("Project not found for tenant");
        var now=Instant.now();var month=YearMonth.from(now.atZone(ZoneOffset.UTC));
        var decision=commercial.decide(new CommercialAdmissionRequest(PrincipalRef.tenantScoped(tenant,PrincipalType.ORGANIZATION,tenant),"render.submit","render.job.create","render.job.create",1,
                month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(),month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(),"render-submit:"+project,now));
        if(!decision.allowed())throw new IllegalStateException(decision.reason()==CommercialDecisionReason.QUOTA_EXCEEDED?"Quota exceeded":"Commercial admission denied: "+decision.reason());
        return new Prepared(new CanonicalActor(initiator.actorId(),initiator.actorType(),tenant,Set.of(),"fixture",initiator.actorType()==ActorType.USER?"fixture-account":null),new ProjectScope(tenant,"fixture-workspace",project),decision);
    }
    @Override public void persist(String job,String snapshot,Prepared p) {
        contexts.put(job,new AcceptedExecutionContext(1,job,p.actor().actorId(),p.actor().actorType(),p.actor().accountId(),p.actor().actorType()==ActorType.USER?p.actor().actorId():null,
                p.scope().tenantId(),p.scope().workspaceId(),p.scope().projectId(),"ORGANIZATION",p.scope().tenantId(),"TENANT_ORGANIZATION",null,"fixture","fixture-grant",0,p.decision().evidence(),snapshot,p.decision().decidedAt()));
    }
    public void seed(String job,String tenant,String project,String snapshot) {
        persist(job,snapshot,new Prepared(new CanonicalActor("test-principal-p1",ActorType.USER,tenant,Set.of(),"fixture","fixture-account"),new ProjectScope(tenant,"fixture-workspace",project),
                new CommercialDecision(PrincipalRef.tenantScoped(tenant,PrincipalType.ORGANIZATION,tenant),"render.submit",true,CommercialDecisionReason.ALLOWED,List.of(),"fixture","seed",Instant.now())));
    }
    @Override public AcceptedExecutionContext get(String tenant,String project,String job) {
        var context=contexts.get(job);
        if(context==null||!tenant.equals(context.tenantId())||!project.equals(context.projectId()))throw new IllegalStateException("Explicit accepted fixture missing");
        return context;
    }
}
