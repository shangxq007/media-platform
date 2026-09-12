package com.example.platform.social.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/** Safe, local-only projection for the publication read slice. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicationPostResponse(
        String id,
        String projectId,
        String connectedAccountId,
        long bindingVersion,
        String contentText,
        ContentAvailability contentAvailability,
        ContentVersionRelationState contentVersionRelationState,
        String artifactId,
        ArtifactRelationState artifactRelationState,
        String platformType,
        Instant scheduledAt,
        TimeMeaning timeMeaning,
        TimePrecision timePrecision,
        SourceVerification sourceVerification,
        EndpointAccess endpointAccess,
        GlobalEffectiveAccess globalEffectiveAccess) {

    public enum ContentAvailability { AVAILABLE, NOT_PROVIDED, RESTRICTED }
    public enum ContentVersionRelationState { NOT_PROVIDED, RESTRICTED }
    public enum ArtifactRelationState { AVAILABLE, NOT_PROVIDED, RESTRICTED }
    public enum TimeMeaning { PLANNED_PUBLISH_TIME, NOT_PROVIDED }
    public enum TimePrecision { EXACT_INSTANT, UNKNOWN }
    public enum SourceVerification { VERIFIED_LOCAL_RECORD }
    public enum EndpointAccess { AUTHORIZED_LOCAL_PROJECT_ACCOUNT_READ }
    public enum GlobalEffectiveAccess { UNKNOWN_FAIL_CLOSED }
}
