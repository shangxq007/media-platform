package com.example.platform.render.infrastructure;

import com.example.platform.artifact.app.*;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.TenantGuard;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class GraphAcceptedOutputQuery implements AcceptedRenderOutputQuery {
    private final GraphRenderExecutionRepository executions;
    public GraphAcceptedOutputQuery(GraphRenderExecutionRepository executions){this.executions=executions;}
    public Optional<ArtifactId> find(ArtifactScope scope) {
        TenantGuard.assertSameTenant(scope.tenantId());
        return executions.find(scope.tenantId(),scope.projectId(),scope.renderJobId())
                .filter(r->"COMPLETED".equals(r.get("status",String.class)))
                .map(r->r.get("final_artifact_id",String.class)).map(ArtifactId::new);
    }
}
