package com.example.platform.contract.media;

/** One capability declared by a provider, with the capability contract version it implements. */
public record FrameExtractCapabilityDeclaration(String capabilityId, String contractVersion) {

    public FrameExtractCapabilityDeclaration {
        require(capabilityId, "capabilityId");
        require(contractVersion, "contractVersion");
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
