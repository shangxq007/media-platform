package com.example.platform.social.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Authorized account identity for one Project-scoped publication read endpoint. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicationAccountResponse(
        String id,
        String displayName,
        DisplayNameAvailability displayNameAvailability,
        String platformType,
        String projectId,
        long bindingVersion,
        EndpointAccess endpointAccess,
        GlobalEffectiveAccess globalEffectiveAccess) {

    public enum DisplayNameAvailability { AVAILABLE, NOT_PROVIDED }
    public enum EndpointAccess { AUTHORIZED_LOCAL_PROJECT_ACCOUNT_READ }
    public enum GlobalEffectiveAccess { UNKNOWN_FAIL_CLOSED }
}
