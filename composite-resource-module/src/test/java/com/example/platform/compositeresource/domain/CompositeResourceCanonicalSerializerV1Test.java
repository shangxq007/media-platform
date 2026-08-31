package com.example.platform.compositeresource.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class CompositeResourceCanonicalSerializerV1Test {

    @Test
    void exposesStableVersionedTypeTaggedCanonicalBytesAndDigest() {
        CompositeResourceVersion first = unorderedVersion(List.of(component("b", "object-b", "2"),
                component("a", "object-a", "1")));
        CompositeResourceVersion rebuilt = unorderedVersion(List.of(component("a", "object-a", "1"),
                component("b", "object-b", "2")));

        assertThat(CompositeResourceCanonicalSerializerV1.serializationVersion()).isEqualTo(1);
        assertThat(CompositeResourceCanonicalSerializerV1.serialize(first))
                .isEqualTo(CompositeResourceCanonicalSerializerV1.serialize(rebuilt));
        assertThat(first.semanticContentDigest()).isEqualTo(rebuilt.semanticContentDigest());
        assertThat(first.semanticallyEquals(rebuilt)).isTrue();

        byte[] bytes = CompositeResourceCanonicalSerializerV1.serialize(first);
        int firstTokenLength = ByteBuffer.wrap(bytes).getInt();
        assertThat(new String(bytes, Integer.BYTES, firstTokenLength, StandardCharsets.UTF_8))
                .isEqualTo("COMPOSITE_RESOURCE");
    }

    @Test
    void orderedReorderingChangesCanonicalBytesFacetDigestAndResourceDigest() {
        ExternalSemanticComponent a = component("a", "object-a", "1");
        ExternalSemanticComponent b = component("b", "object-b", "2");
        CompositeResourceVersion left = orderedVersion(List.of(a, b));
        CompositeResourceVersion right = orderedVersion(List.of(b, a));

        assertThat(CompositeResourceCanonicalSerializerV1.serialize(left))
                .isNotEqualTo(CompositeResourceCanonicalSerializerV1.serialize(right));
        assertThat(left.facets().getFirst().facetDigest()).isNotEqualTo(right.facets().getFirst().facetDigest());
        assertThat(left.semanticContentDigest()).isNotEqualTo(right.semanticContentDigest());
        assertThat(left.semanticallyEquals(right)).isFalse();
    }

    @Test
    void unorderedHashMapConstructionOrderDoesNotChangeCanonicalMeaning() {
        HashMap<String, SemanticComponent> firstMap = new HashMap<>();
        firstMap.put("second", component("b", "object-b", "2"));
        firstMap.put("first", component("a", "object-a", "1"));
        HashMap<String, SemanticComponent> secondMap = new HashMap<>();
        secondMap.put("first", component("a", "object-a", "1"));
        secondMap.put("second", component("b", "object-b", "2"));

        CompositeResourceVersion first = unorderedVersion(List.copyOf(firstMap.values()));
        CompositeResourceVersion second = unorderedVersion(List.copyOf(secondMap.values()));

        assertThat(first.semanticContentDigest()).isEqualTo(second.semanticContentDigest());
        assertThat(first.facets().getFirst().facetDigest()).isEqualTo(second.facets().getFirst().facetDigest());
    }

    @Test
    void serializationIsLocaleAndTimezoneIndependent() {
        Locale priorLocale = Locale.getDefault();
        TimeZone priorZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            CompositeResourceVersion first = unorderedVersion(List.of(component("a", "object-a", "a")));

            Locale.setDefault(Locale.JAPAN);
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            CompositeResourceVersion second = unorderedVersion(List.of(component("a", "object-a", "a")));

            assertThat(CompositeResourceCanonicalSerializerV1.serialize(first))
                    .isEqualTo(CompositeResourceCanonicalSerializerV1.serialize(second));
            assertThat(first.semanticContentDigest()).isEqualTo(second.semanticContentDigest());
        } finally {
            Locale.setDefault(priorLocale);
            TimeZone.setDefault(priorZone);
        }
    }

    @Test
    void versionIdentityIsEncodedAndNeverDerivedFromDigest() {
        CompositeResourceVersion v1 = resource("version-one", ComponentCollectionSemantics.UNORDERED,
                List.of(component("a", "object-a", "a")));
        CompositeResourceVersion v2 = resource("version-two", ComponentCollectionSemantics.UNORDERED,
                List.of(component("a", "object-a", "a")));

        assertThat(v1.versionId()).isNotEqualTo(v2.versionId());
        assertThat(v1.semanticContentDigest()).isNotEqualTo(v2.semanticContentDigest());
        assertThat(v1.versionId().value()).isNotEqualTo(v1.semanticContentDigest().canonicalValue());
    }

    private static CompositeResourceVersion orderedVersion(List<SemanticComponent> components) {
        return resource("version-1", ComponentCollectionSemantics.ORDERED, components);
    }

    private static CompositeResourceVersion unorderedVersion(List<SemanticComponent> components) {
        return resource("version-1", ComponentCollectionSemantics.UNORDERED, components);
    }

    private static CompositeResourceVersion resource(
            String version, ComponentCollectionSemantics semantics, List<SemanticComponent> components) {
        SemanticFacet facet = new SemanticFacet(
                new SemanticFacetId("visual"),
                new SemanticFacetTypeId("platform:visual"),
                semantics,
                components);
        return CompositeResourceModelTest.version("resource-1", version, Optional.empty(), List.of(facet));
    }

    private static ExternalSemanticComponent component(String id, String objectId, String digestDigit) {
        return new ExternalSemanticComponent(
                new CompositeComponentId(id),
                new ExactSemanticObjectPin(
                        new SemanticOwnerId("platform:catalog"),
                        new SemanticObjectTypeId("platform:image"),
                        new SemanticObjectId(objectId),
                        new SemanticObjectVersionId(objectId + "-v1"),
                        com.example.platform.shared.digest.ContentDigest.sha256(digestDigit.repeat(64))));
    }
}
