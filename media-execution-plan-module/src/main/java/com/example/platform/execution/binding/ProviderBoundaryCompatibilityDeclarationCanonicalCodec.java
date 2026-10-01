package com.example.platform.execution.binding;

import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.BoundaryContractId;
import com.example.platform.execution.planning.CanonicalWriter;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Bounded V1 canonical codec for {@link ProviderBoundaryCompatibilityDeclaration} —
 * deterministic bytes plus their exact inverse.
 *
 * <p>The dependency edge and the two binding pins reuse the plan/candidate codecs'
 * framings, so the persisted declaration is structurally identical to the pieces it
 * references; unknown variants fail closed.
 */
public final class ProviderBoundaryCompatibilityDeclarationCanonicalCodec {

    public static final String FORMAT = "p25a.provider-boundary-declaration.v1";

    private ProviderBoundaryCompatibilityDeclarationCanonicalCodec() {
    }

    public static byte[] encode(ProviderBoundaryCompatibilityDeclaration declaration) {
        Objects.requireNonNull(declaration, "declaration");
        String canonical = new CanonicalWriter()
                .tag(FORMAT)
                .field("sourceDependency", PhysicalExecutionPlanCanonicalCodec.edge(declaration.sourceDependency()))
                .field("producerBindingPin",
                        ProviderCandidateCanonicalCodec.bindingPin(declaration.producerBindingPin()))
                .field("consumerBindingPin",
                        ProviderCandidateCanonicalCodec.bindingPin(declaration.consumerBindingPin()))
                .field("boundaryContractId", declaration.boundaryContractId().value())
                .field("declaration", declaration.declaration().name())
                .build();
        return canonical.getBytes(StandardCharsets.UTF_8);
    }

    public static ProviderBoundaryCompatibilityDeclaration decode(byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        FrameReader reader = FrameReader.of(payload);
        reader.requireTag(FORMAT);
        LogicalDependencyEdge dependency =
                PhysicalExecutionPlanCanonicalCodec.edge(reader.fieldNested("sourceDependency"));
        var producer = ProviderCandidateCanonicalCodec.bindingPin(reader.fieldNested("producerBindingPin"));
        var consumer = ProviderCandidateCanonicalCodec.bindingPin(reader.fieldNested("consumerBindingPin"));
        BoundaryContractId contractId = BoundaryContractId.of(reader.field("boundaryContractId"));
        ProviderBoundaryCompatibilityDeclaration.Declaration declaration = enumValue(
                ProviderBoundaryCompatibilityDeclaration.Declaration.class, reader.field("declaration"));
        if (reader.hasRemaining()) {
            throw new UnsupportedPersistedConstructException(
                    "trailing bytes after provider boundary declaration");
        }
        return new ProviderBoundaryCompatibilityDeclaration(
                dependency, producer, consumer, contractId, declaration);
    }

    public static String digestHex(ProviderBoundaryCompatibilityDeclaration declaration) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(encode(declaration)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException failure) {
            throw new UnsupportedPersistedConstructException(type.getSimpleName() + " value " + name);
        }
    }
}
