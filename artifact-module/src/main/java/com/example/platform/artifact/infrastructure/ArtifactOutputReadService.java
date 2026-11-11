package com.example.platform.artifact.infrastructure;

import com.example.platform.artifact.app.*;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.artifact.domain.*;
import com.example.platform.shared.web.TenantGuard;
import java.util.*;
import org.springframework.stereotype.Service;

/** Reads only the output accepted by Render's bound-graph completion authority. */
@Service
public class ArtifactOutputReadService implements ArtifactOutputRead {
    private final ArtifactApplicationQuery scoped;
    private final List<AcceptedRenderOutputQuery> accepted;
    private final ArtifactContentRead content;

    public ArtifactOutputReadService(ArtifactApplicationQuery scoped,
            List<AcceptedRenderOutputQuery> accepted, ArtifactContentRead content) {
        this.scoped = scoped;
        this.accepted = List.copyOf(accepted);
        this.content = content;
    }

    public Optional<ArtifactOutputReference> find(ArtifactScope scope) {
        TenantGuard.assertSameTenant(scope.tenantId());
        return acceptedOutput(scope).map(id -> new ArtifactOutputReference(scope, id));
    }

    public Content read(ArtifactOutputReference reference) {
        var scope = reference.scope();
        TenantGuard.assertSameTenant(scope.tenantId());
        if (!acceptedOutput(scope).filter(reference.artifactId()::equals).isPresent())
            throw new IllegalArgumentException("not the accepted Render output");
        var artifact = scoped.findArtifact(scope, reference.artifactId())
                .orElseThrow(() -> new IllegalArgumentException("output not found in scope"));
        if (artifact.state() != ArtifactState.AVAILABLE || artifact.artifactKind() != ArtifactKind.RENDER_MASTER)
            throw new IllegalStateException("output is not available");
        var verified = content.read(scope, reference.artifactId());
        return new Content(verified.bytes(), artifact.mediaType(), verified.contentType(), verified.fileName());
    }

    private Optional<ArtifactId> acceptedOutput(ArtifactScope scope) {
        var ids = accepted.stream().map(query -> query.find(scope)).flatMap(Optional::stream).distinct().toList();
        if (ids.size() > 1) throw new IllegalStateException("conflicting accepted Render outputs");
        return ids.stream().findFirst();
    }
}
