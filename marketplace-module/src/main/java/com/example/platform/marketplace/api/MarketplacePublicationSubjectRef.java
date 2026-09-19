package com.example.platform.marketplace.api;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** A reference to an owning domain's exact subject, never a new asset identity. */
@JsonTypeInfo(use=JsonTypeInfo.Id.NAME, property="kind")
@JsonSubTypes(@JsonSubTypes.Type(value=MarketplacePublicationSubjectRef.MediaAssetSubject.class, name="MEDIA_ASSET"))
public sealed interface MarketplacePublicationSubjectRef permits MarketplacePublicationSubjectRef.MediaAssetSubject {
    record MediaAssetSubject(MediaAssetId assetId, String version) implements MarketplacePublicationSubjectRef {
        public MediaAssetSubject {
            java.util.Objects.requireNonNull(assetId);
            if(version==null || version.isBlank() || version.length()>64) throw new IllegalArgumentException("Exact Media version required");
        }
    }
}
