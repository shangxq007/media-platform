package com.example.platform.execution.binding;

import com.example.platform.execution.planning.CanonicalWriter;
import com.example.platform.execution.compatibility.*;
import com.example.platform.execution.taskgraph.ExecutableTaskGraphDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Durable inputs only; opaque kernel proofs are always re-derived by the existing #22 binder. */
public final class BoundGraphInputsCanonicalCodec {
    private BoundGraphInputsCanonicalCodec() {}
    public static byte[] encode(BoundGraphInputs inputs) {
        var writer=new CanonicalWriter().tag("bound-graph-inputs-v1")
                .field("plan",Base64.getEncoder().encodeToString(PhysicalExecutionPlanCanonicalCodec.encode(inputs.physicalPlan())))
                .list(inputs.candidates().stream().map(c->Base64.getEncoder().encodeToString(ProviderCandidateCanonicalCodec.encode(c))).toList())
                .list(inputs.transitionDeclarations().stream().map(d->Base64.getEncoder().encodeToString(ProviderBoundaryCompatibilityDeclarationCanonicalCodec.encode(d))).toList())
                .field("graphDigest",inputs.expectedExecutableTaskGraphDigest().sha256Hex());
        return writer.build().getBytes(StandardCharsets.UTF_8);
    }
    public static BoundGraphInputs decode(byte[] bytes) {
        var reader=FrameReader.of(bytes);reader.requireTag("bound-graph-inputs-v1");
        var plan=PhysicalExecutionPlanCanonicalCodec.decode(Base64.getDecoder().decode(reader.field("plan")));
        var candidates=new ArrayList<ProviderCandidate>();int count=reader.listCount();
        for(int i=0;i<count;i++)candidates.add(ProviderCandidateCanonicalCodec.decode(Base64.getDecoder().decode(reader.text())));
        var declarations=new ArrayList<ProviderBoundaryCompatibilityDeclaration>();count=reader.listCount();
        for(int i=0;i<count;i++)declarations.add(ProviderBoundaryCompatibilityDeclarationCanonicalCodec.decode(Base64.getDecoder().decode(reader.text())));
        var digest=new ExecutableTaskGraphDigest(reader.field("graphDigest"));
        if(reader.hasRemaining())throw new UnsupportedPersistedConstructException("trailing bound graph inputs");
        var result=new BoundGraphInputs(plan,candidates,declarations,digest);
        var graph=new ProviderBindingEntryService().bind(plan,candidates,declarations).executableTaskGraph();
        if(!graph.digest().equals(digest))throw new BoundGraphDigestMismatchException(digest.sha256Hex(),graph.digest().sha256Hex());
        return result;
    }
}
