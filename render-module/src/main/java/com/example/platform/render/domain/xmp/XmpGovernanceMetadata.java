package com.example.platform.render.domain.xmp;

import java.util.List;

/**
 * XMP {@code governance:*} namespace metadata.
 */
public record XmpGovernanceMetadata(
        String classification,
        String license,
        String rightsHolder,
        List<String> usageRights,
        String retentionPolicy,
        String securityLevel,
        boolean containsPii,
        boolean aiGenerated,
        boolean requiresReview,
        String approvedBy) {
}
