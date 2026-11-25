package com.example.platform.execution.binding;

import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.planning.CanonicalWriter;
import com.example.platform.execution.taskgraph.ExecutableTaskGraphDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Durable inputs only. Both database layouts use the same versioned selection frame. */
public final class BoundGraphInputsCanonicalCodec {
    private BoundGraphInputsCanonicalCodec() {}

    public static byte[] encode(BoundGraphInputs inputs) {
        return new CanonicalWriter().tag("bound-graph-inputs-v2")
                .field("plan", base64(PhysicalExecutionPlanCanonicalCodec.encode(inputs.physicalPlan())))
                .field("selection", base64(encodeSelection(inputs)))
                .list(inputs.transitionDeclarations().stream()
                        .map(d -> base64(ProviderBoundaryCompatibilityDeclarationCanonicalCodec.encode(d)))
                        .sorted().toList())
                .field("graphDigest", inputs.expectedExecutableTaskGraphDigest().sha256Hex())
                .build().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] encodeSelection(BoundGraphInputs inputs) {
        return new CanonicalWriter().tag("binding-selection-v1")
                .field("policy", inputs.selectionPolicy().canonical())
                .list(inputs.candidates().stream().map(c -> base64(ProviderCandidateCanonicalCodec.encode(c)))
                        .sorted().toList())
                .build().getBytes(StandardCharsets.UTF_8);
    }

    public record Selection(ProviderSelectionPolicy policy, List<ProviderCandidate> candidates) {
        public Selection {
            candidates = List.copyOf(candidates);
        }
    }

    public static Selection decodeSelection(byte[] bytes) {
        var reader = FrameReader.of(bytes);
        reader.requireTag("binding-selection-v1");
        var policy = ProviderSelectionPolicy.read(reader.fieldNested("policy"));
        var candidates = new ArrayList<ProviderCandidate>();
        for (int n = reader.listCount(); n > 0; n--) {
            candidates.add(ProviderCandidateCanonicalCodec.decode(unbase64(reader.text())));
        }
        if (reader.hasRemaining()) throw new UnsupportedPersistedConstructException("trailing binding candidates");
        return new Selection(policy, candidates);
    }

    public static BoundGraphInputs decode(byte[] bytes) {
        var reader = FrameReader.of(bytes);
        reader.requireTag("bound-graph-inputs-v2");
        var plan = PhysicalExecutionPlanCanonicalCodec.decode(unbase64(reader.field("plan")));
        var selection = decodeSelection(unbase64(reader.field("selection")));
        var declarations = new ArrayList<ProviderBoundaryCompatibilityDeclaration>();
        for (int n = reader.listCount(); n > 0; n--) {
            declarations.add(ProviderBoundaryCompatibilityDeclarationCanonicalCodec.decode(unbase64(reader.text())));
        }
        var digest = new ExecutableTaskGraphDigest(reader.field("graphDigest"));
        if (reader.hasRemaining()) throw new UnsupportedPersistedConstructException("trailing bound graph inputs");
        var inputs = new BoundGraphInputs(plan, selection.candidates(), declarations, digest, selection.policy());
        if (!java.util.Arrays.equals(bytes, encode(inputs))) {
            throw new UnsupportedPersistedConstructException("noncanonical bound graph inputs");
        }
        BoundGraphRederivation.rederive(inputs);
        return inputs;
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] unbase64(String text) {
        return Base64.getDecoder().decode(text);
    }
}
