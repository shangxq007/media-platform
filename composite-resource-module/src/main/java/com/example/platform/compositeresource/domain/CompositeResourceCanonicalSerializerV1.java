package com.example.platform.compositeresource.domain;

import com.example.platform.shared.digest.ContentDigest;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

public final class CompositeResourceCanonicalSerializerV1 {
    public static final int SERIALIZATION_VERSION = 1;

    private CompositeResourceCanonicalSerializerV1() {}

    public static int serializationVersion() {
        return SERIALIZATION_VERSION;
    }

    public static byte[] serialize(CompositeResourceVersion resource) {
        return serializeSemanticContent(resource);
    }

    public static byte[] serializeSemanticContent(CompositeResourceVersion resource) {
        return encode(out -> {
            text(out, "COMPOSITE_RESOURCE");
            out.writeInt(SERIALIZATION_VERSION);
            text(out, resource.resourceId().value());
            out.writeBoolean(resource.parentVersionId().isPresent());
            if (resource.parentVersionId().isPresent()) {
                text(out, resource.parentVersionId().orElseThrow().value());
            }
            text(out, resource.kind().value());
            out.writeInt(resource.schemaVersion());
            text(out, resource.scope().authority().value());
            text(out, resource.scope().externalScopeId().value());
            List<SemanticFacet> facets = resource.facets().stream()
                    .sorted(Comparator.comparing(facet -> facet.facetId().value()))
                    .toList();
            out.writeInt(facets.size());
            for (SemanticFacet facet : facets) {
                bytes(out, serializeFacet(facet));
            }
        });
    }

    public static byte[] serializeFacet(SemanticFacet facet) {
        return encode(out -> {
            text(out, "SEMANTIC_FACET");
            out.writeInt(SERIALIZATION_VERSION);
            text(out, facet.facetId().value());
            text(out, facet.facetType().value());
            text(out, facet.collectionSemantics().name());
            List<SemanticComponent> components = facet.components();
            if (facet.collectionSemantics() == ComponentCollectionSemantics.UNORDERED) {
                components = components.stream()
                        .sorted(Comparator.comparing(SemanticComponent::componentId))
                        .toList();
            }
            out.writeInt(components.size());
            for (SemanticComponent component : components) {
                bytes(out, serializeComponent(component));
            }
        });
    }

    public static byte[] serializeComponent(SemanticComponent component) {
        return encode(out -> {
            text(out, "SEMANTIC_COMPONENT");
            out.writeInt(SERIALIZATION_VERSION);
            text(out, component.componentId().value());
            if (component instanceof ExternalSemanticComponent external) {
                text(out, "EXTERNAL_EXACT_PIN");
                ExactSemanticObjectPin pin = external.pin();
                text(out, pin.owner().value());
                text(out, pin.type().value());
                text(out, pin.objectId().value());
                text(out, pin.versionId().value());
                digest(out, pin.contentDigest());
            } else if (component instanceof NestedCompositeResourceComponent nested) {
                text(out, "NESTED_COMPOSITE_EXACT_PIN");
                CompositeResourceVersionPin pin = nested.pin();
                text(out, pin.resourceId().value());
                text(out, pin.versionId().value());
                digest(out, pin.contentDigest());
            } else {
                throw new IllegalArgumentException("Unknown semantic component variant");
            }
        });
    }

    public static ContentDigest digestResource(CompositeResourceVersion resource) {
        return sha256(serializeSemanticContent(resource));
    }

    public static ContentDigest digestFacet(SemanticFacet facet) {
        return sha256(serializeFacet(facet));
    }

    public static ContentDigest digestComponent(SemanticComponent component) {
        return sha256(serializeComponent(component));
    }

    private static void digest(DataOutputStream out, ContentDigest digest) throws IOException {
        text(out, digest.algorithm().name());
        text(out, digest.canonicalValue());
    }

    private static void text(DataOutputStream out, String value) throws IOException {
        bytes(out, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void bytes(DataOutputStream out, byte[] value) throws IOException {
        out.writeInt(value.length);
        out.write(value);
    }

    private static byte[] encode(Encoder encoder) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                encoder.write(out);
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Canonical in-memory serialization failed", exception);
        }
    }

    private static ContentDigest sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return ContentDigest.sha256(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }

    @FunctionalInterface
    private interface Encoder {
        void write(DataOutputStream out) throws IOException;
    }
}
