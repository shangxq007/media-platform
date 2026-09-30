package com.example.platform.thumbnail;

import com.example.platform.artifact.app.ArtifactProjectAuthorizationPort;
import com.example.platform.entitlement.api.commercial.*;
import com.example.platform.shared.commercial.*;
import java.time.*;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ThumbnailService {
    private final ThumbnailTaskStore tasks; private final ObjectProvider<WorkflowClient> clients; private final ArtifactProjectAuthorizationPort authorization; private final QuotaConsumptionPort quota;
    public ThumbnailService(ThumbnailTaskStore tasks, ObjectProvider<WorkflowClient> clients, ArtifactProjectAuthorizationPort authorization, QuotaConsumptionPort quota){this.tasks=tasks;this.clients=clients;this.authorization=authorization;this.quota=quota;}
    @Transactional
    public ThumbnailContracts.Result submit(ThumbnailContracts.Request request){
        // Retired media authority replaced by the owner-published Artifact project-authorization
        // boundary: admission still requires an authorized project read before any task row exists.
        authorization.requireRead(request.tenantId(),request.projectId());
        var admission=tasks.admit(request); String id=admission.taskId();
        if (!admission.created()) return tasks.find(request.tenantId(), request.projectId(), id).orElseThrow();
        if (tasks.find(request.tenantId(), request.projectId(), id).map(r -> r.status() == ThumbnailContracts.Status.CANCELLED).orElse(false))
            return tasks.find(request.tenantId(), request.projectId(), id).orElseThrow();
        Instant now=Instant.now(); YearMonth month=YearMonth.from(now.atZone(ZoneOffset.UTC));
        var decision=quota.consume(new QuotaConsumptionRequest(PrincipalRef.tenantScoped(request.tenantId(),PrincipalType.ORGANIZATION,request.tenantId()),"media.thumbnail",1,month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(),month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(),"thumbnail:"+id,"thumbnail:"+id,"thumbnail extraction",now));
        if(!decision.allowed()) {
            tasks.deleteUnchargedAdmission(request.tenantId(), request.projectId(), id);
            throw new IllegalStateException("thumbnail quota denied: "+decision.reason());
        }
        ThumbnailWorkflow wf=client().newWorkflowStub(ThumbnailWorkflow.class,WorkflowOptions.newBuilder().setTaskQueue("media-platform-tasks").setWorkflowId("thumbnail:"+request.tenantId()+":"+id).build());
        try { WorkflowClient.start(wf::run,id,request.tenantId(),request.projectId()); } catch (io.temporal.client.WorkflowExecutionAlreadyStarted e) { }
        return tasks.find(request.tenantId(),request.projectId(),id).orElse(new ThumbnailContracts.Result(id,ThumbnailContracts.Status.ADMITTED,null,null));
    }
    public boolean cancel(String tenant, String project, String id) {
        authorization.requireRead(tenant, project);
        boolean changed = tasks.cancel(tenant, project, id);
        if (changed) {
            ThumbnailWorkflow wf=client().newWorkflowStub(ThumbnailWorkflow.class,WorkflowOptions.newBuilder().setTaskQueue("media-platform-tasks").setWorkflowId("thumbnail:"+tenant+":"+id).build());
            wf.cancel("client-requested");
        }
        return changed;
    }
    public Optional<ThumbnailContracts.Result> status(String tenant,String project,String id){authorization.requireRead(tenant,project);return tasks.find(tenant,project,id);}

    /**
     * Optional, exactly like {@code WorkflowDispatch} / {@code CoverImageService}: the API process may
     * run without a Temporal cluster (the default profile excludes the Temporal client
     * auto-configuration). Admission then fails closed with a clear error instead of preventing the API
     * context from starting.
     */
    private WorkflowClient client(){
        WorkflowClient client = clients.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException("Temporal client unavailable; thumbnail admission requires an enabled Temporal client");
        }
        return client;
    }
}
