package com.example.platform.compositeresource.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.shared.digest.ContentDigest;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompositeResourceIdentityTest {

    @Test
    void opaqueIdentitiesRejectEmptyValuesAndRemainDifferentApiTypes() {
        assertThatThrownBy(() -> new CompositeResourceId(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompositeResourceVersionId(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticFacetId("\t"))
                .isInstanceOf(IllegalArgumentException.class);

        List<Class<?>> identityTypes = List.of(
                CompositeResourceId.class,
                CompositeResourceVersionId.class,
                CompositeResourceKindId.class,
                SemanticFacetId.class,
                SemanticFacetTypeId.class,
                CompositeComponentId.class,
                SemanticOwnerId.class,
                SemanticObjectTypeId.class,
                SemanticObjectId.class,
                SemanticObjectVersionId.class);

        assertThat(identityTypes).doesNotHaveDuplicates();
        assertThat(identityTypes).allMatch(Class::isRecord);
        assertThat(identityTypes)
                .allMatch(type -> Arrays.stream(type.getRecordComponents())
                        .map(RecordComponent::getType)
                        .toList()
                        .equals(List.of(String.class)));

        CompositeResourceId resourceId = new CompositeResourceId("resource-7");
        CompositeResourceVersionId versionId = new CompositeResourceVersionId("version-7");
        ContentDigest digest = ContentDigest.sha256("a".repeat(64));
        assertThat(resourceId.value()).isNotEqualTo(versionId.value());
        assertThat(versionId.value()).isNotEqualTo(digest.canonicalValue());

        SemanticFacetId facetId = new SemanticFacetId("visual-description");
        SemanticFacetTypeId facetType = new SemanticFacetTypeId("platform:visual-description");
        assertThat(facetId.value()).isNotEqualTo(facetType.value());
    }

    @Test
    void namespacedIdentitiesAcceptCanonicalPlatformAndReverseDnsVendorSyntax() {
        assertThat(new CompositeResourceKindId("platform:scene").value()).isEqualTo("platform:scene");
        assertThat(new SemanticFacetTypeId("com.acme.media:color-grade").value())
                .isEqualTo("com.acme.media:color-grade");
        assertThat(new SemanticOwnerId("io.vendor:catalog").value()).isEqualTo("io.vendor:catalog");
        assertThat(new SemanticObjectTypeId("dev.example.product:object_v2").value())
                .isEqualTo("dev.example.product:object_v2");
    }

    @Test
    void namespacedIdentitiesRejectMalformedNonCanonicalAndReservedSquatting() {
        List<String> invalid = List.of(
                "platform",
                "Platform:scene",
                " platform:scene",
                "com:scene",
                "com..acme:scene",
                "com.acme:",
                "platform.example:scene",
                "com.platform:scene",
                "example.platform:scene");

        invalid.forEach(value -> {
            assertThatThrownBy(() -> new CompositeResourceKindId(value))
                    .as("kind %s", value)
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new SemanticOwnerId(value))
                    .as("owner %s", value)
                    .isInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    void componentIdentityIsStableAcrossBindingChanges() {
        CompositeComponentId componentId = new CompositeComponentId("primary-image");
        assertThat(new CompositeComponentId("primary-image")).isEqualTo(componentId);
        assertThat(componentId.value()).isNotEqualTo("0");
    }
}
