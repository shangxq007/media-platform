package com.example.platform.render.api.rawmedia;

/** Command for registering an uploaded raw-media Product through the render API boundary. */
public record RawMediaProductRegistrationCommand(
        String tenantId,
        String projectId,
        String assetId,
        String storageReferenceUri,
        String mimeType
) {
}
