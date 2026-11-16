package com.example.platform.config.mediaexecution;

import com.example.platform.artifact.app.*;
import com.example.platform.artifact.domain.*;
import com.example.platform.execution.admission.*;
import com.example.platform.execution.admission.PlatformExecutionAdmissionPort.CapabilityExecution;
import com.example.platform.execution.binding.*;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.planning.*;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.identity.api.project.ProjectScopeQueries;
import com.example.platform.providerplugin.*;
import com.example.platform.config.providerinteroperability.ProviderInteroperabilityAuthority;
import com.example.platform.shared.capability.MediaFrameExtractParametersV1;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.TenantGuard;
import io.temporal.client.WorkflowClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

/** Thin authenticated use-case assembly of Artifact, Identity, #21/#22 and Execution owner ports. */
@Service
public class ArtifactCapabilityApplicationService {
    private final ArtifactProjectAuthorizationPort authorization;
    private final CanonicalActorResolver actors;
    private final com.example.platform.identity.api.authorization.AuthorizationDecisionPort decisions;
    private final ProjectScopeQueries projects;
    private final ArtifactApplicationQuery artifacts;
    private final PhysicalPlanningEntryService planning;
    private final ProviderBindingEntryService binding;
    private final ProviderPluginCatalog catalog;
    private final ProviderInteroperabilityAuthority interoperability;
    private final PlatformExecutionAdmissionPort.ArtifactCapabilities executions;
    private final ObjectProvider<WorkflowClient> clients;
    public ArtifactCapabilityApplicationService(ArtifactProjectAuthorizationPort authorization,CanonicalActorResolver actors,
            com.example.platform.identity.api.authorization.AuthorizationDecisionPort decisions,
            ProjectScopeQueries projects,ArtifactApplicationQuery artifacts,PhysicalPlanningEntryService planning,
            ProviderBindingEntryService binding,ProviderPluginCatalog catalog,ProviderInteroperabilityAuthority interoperability,
            PlatformExecutionAdmissionPort.ArtifactCapabilities executions,ObjectProvider<WorkflowClient> clients) {
        this.authorization=authorization;this.actors=actors;this.decisions=decisions;this.projects=projects;this.artifacts=artifacts;this.planning=planning;
        this.binding=binding;this.catalog=catalog;this.interoperability=interoperability;this.executions=executions;this.clients=clients;
    }
    public CapabilityExecution submit(String tenant,String project,String capability,String source,MediaFrameExtractParametersV1 parameters,String key) {
        requireExecutionWrite(tenant,project);TenantGuard.assertSameTenant(tenant);
        String identity=ArtifactCapabilityExecutionPlan.requestIdentity(tenant,project,capability,key);
        var previous=executions.find(tenant,project,"exe_"+identity);
        if(previous.isPresent()) {
            var accepted=previous.orElseThrow();
            var equivalent=new ArtifactCapabilityRequest(identity,capability,accepted.plan().request().source(),parameters);
            if(!source.equals(accepted.plan().request().source().artifactId().value())
                    || !equivalent.parameters().equals(accepted.plan().request().parameters())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,"idempotency key conflicts with accepted parameters");
            }
            return accepted;
        }
        if(clients.getIfAvailable()==null)throw new IllegalStateException("Temporal is required for capability execution");
        var actor=actors.resolveCurrentActor().orElseThrow(()->new IllegalStateException("authenticated actor required"));
        if(!tenant.equals(actor.tenantId()))throw new IllegalArgumentException("actor tenant mismatch");
        var scope=projects.resolveForAcceptance(tenant,project);
        var artifact=artifacts.findProjectArtifact(new ArtifactProjectScope(tenant,project),new ArtifactId(source))
                .filter(a->a.state()==ArtifactState.AVAILABLE && a.mediaType()==ArtifactMediaType.VIDEO)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"source Artifact unavailable"));
        var request=new ArtifactCapabilityRequest(identity,capability,new ArtifactPinService.ArtifactPin(artifact.artifactId(),artifact.contentDigest()),parameters);
        var physical=planning.planArtifactCapability(request,new ExecutionPlanId(UUID.randomUUID().toString()));
        var candidates=ProviderCandidateProjection.project(catalog);
        var declarations=interoperability.declarations();
        var graph=binding.bind(physical,candidates,declarations).executableTaskGraph();
        return executions.admit(new ArtifactCapabilityExecutionPlan(tenant,project,scope.workspaceId(),actor.actorId(),key,
                new BoundGraphInputs(physical,candidates,declarations,graph.digest())));
    }
    public Optional<CapabilityExecution> find(String tenant,String project,String execution,String capability) {
        authorization.requireRead(tenant,project);
        return executions.find(tenant,project,execution).filter(e->capability.equals(e.plan().request().capabilityId()));
    }
    public CapabilityExecution cancel(String tenant,String project,String execution,long generation,String capability) {
        requireExecutionWrite(tenant,project);
        var accepted=require(tenant,project,execution,capability);
        var result=executions.cancel(tenant,project,execution,generation);
        // Publication is already fenced in the database; Temporal cancellation also interrupts work.
        if(result.state()==PlatformExecutionState.CANCELLED) {
            var client=clients.getIfAvailable();
            if(client!=null)try {
                client.newUntypedWorkflowStub(com.example.platform.execution.admission.CapabilityExecutionWorkflowIds.workflowId(tenant,execution,accepted.generation())).cancel();
            } catch(io.temporal.client.WorkflowNotFoundException notDispatched) { /* A later outbox delivery observes CANCELLED and starts nothing. */ }
        }
        return result;
    }
    public CapabilityExecution retry(String tenant,String project,String execution,long generation,String capability) {
        requireExecutionWrite(tenant,project);require(tenant,project,execution,capability);
        return executions.retry(tenant,project,execution,generation);
    }
    /** Source read plus the existing project WRITE action, not Artifact tombstone/GC authority. */
    private void requireExecutionWrite(String tenant,String project) {
        TenantGuard.assertSameTenant(tenant);
        authorization.requireRead(tenant,project);
        var actor=actors.resolveCurrentActor().orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if(!tenant.equals(actor.tenantId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        decisions.requireAuthorized(new com.example.platform.shared.authorization.AuthorizationRequest(actor,
                new com.example.platform.shared.authorization.AuthorizationAction("WRITE",
                        com.example.platform.shared.authorization.AuthorizationResourceType.PROJECT,"Execute project media capability"),
                new com.example.platform.shared.authorization.AuthorizableResourceRef(
                        com.example.platform.shared.authorization.AuthorizationResourceType.PROJECT,project,tenant,project,null),
                new com.example.platform.shared.authorization.AuthorizationContext("artifact-capability-http",null,Map.of())));
    }
    private CapabilityExecution require(String tenant,String project,String execution,String capability) {
        return executions.find(tenant,project,execution).filter(e->capability.equals(e.plan().request().capabilityId()))
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
