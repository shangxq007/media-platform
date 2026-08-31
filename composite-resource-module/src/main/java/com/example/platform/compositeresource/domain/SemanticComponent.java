package com.example.platform.compositeresource.domain;

import com.example.platform.shared.digest.ContentDigest;

public sealed interface SemanticComponent
        permits ExternalSemanticComponent, NestedCompositeResourceComponent {
    CompositeComponentId componentId();

    default ContentDigest componentDigest() {
        return CompositeResourceCanonicalSerializerV1.digestComponent(this);
    }
}
