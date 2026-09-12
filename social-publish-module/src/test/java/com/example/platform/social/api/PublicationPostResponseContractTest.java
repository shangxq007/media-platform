package com.example.platform.social.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.platform.social.api.dto.PublicationPostResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class PublicationPostResponseContractTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesOnlyAuthorizedContentAndExplicitLocalVersusGlobalAccess() {
        PublicationPostResponse response = new PublicationPostResponse(
                "post-1", "project-1", "account-1", 7L,
                "source text", PublicationPostResponse.ContentAvailability.AVAILABLE,
                PublicationPostResponse.ContentVersionRelationState.NOT_PROVIDED,
                "artifact-1", PublicationPostResponse.ArtifactRelationState.AVAILABLE,
                "YOUTUBE", Instant.parse("2026-01-01T00:00:00Z"),
                PublicationPostResponse.TimeMeaning.PLANNED_PUBLISH_TIME,
                PublicationPostResponse.TimePrecision.EXACT_INSTANT,
                PublicationPostResponse.SourceVerification.VERIFIED_LOCAL_RECORD,
                PublicationPostResponse.EndpointAccess.AUTHORIZED_LOCAL_PROJECT_ACCOUNT_READ,
                PublicationPostResponse.GlobalEffectiveAccess.UNKNOWN_FAIL_CLOSED);

        JsonNode json = mapper.valueToTree(response);

        assertEquals(Set.of(
                "id", "projectId", "connectedAccountId", "bindingVersion",
                "contentText", "contentAvailability", "contentVersionRelationState",
                "artifactId", "artifactRelationState", "platformType", "scheduledAt",
                "timeMeaning", "timePrecision", "sourceVerification", "endpointAccess",
                "globalEffectiveAccess"), fields(json));
        assertSensitiveFieldsPruned(json);
        assertEquals("AUTHORIZED_LOCAL_PROJECT_ACCOUNT_READ", json.get("endpointAccess").asText());
        assertEquals("UNKNOWN_FAIL_CLOSED", json.get("globalEffectiveAccess").asText());
    }

    @Test
    void omitsMissingOptionalContentAndTimeValueWithoutFabricatingCreationAsSchedule() {
        PublicationPostResponse response = new PublicationPostResponse(
                "post-1", "project-1", "account-1", 7L,
                null, PublicationPostResponse.ContentAvailability.NOT_PROVIDED,
                PublicationPostResponse.ContentVersionRelationState.NOT_PROVIDED,
                null, PublicationPostResponse.ArtifactRelationState.NOT_PROVIDED,
                "YOUTUBE", null,
                PublicationPostResponse.TimeMeaning.NOT_PROVIDED,
                PublicationPostResponse.TimePrecision.UNKNOWN,
                PublicationPostResponse.SourceVerification.VERIFIED_LOCAL_RECORD,
                PublicationPostResponse.EndpointAccess.AUTHORIZED_LOCAL_PROJECT_ACCOUNT_READ,
                PublicationPostResponse.GlobalEffectiveAccess.UNKNOWN_FAIL_CLOSED);

        JsonNode json = mapper.valueToTree(response);

        assertFalse(json.has("contentText"));
        assertFalse(json.has("scheduledAt"));
        assertFalse(json.has("createdAt"));
        assertEquals("NOT_PROVIDED", json.get("contentAvailability").asText());
        assertEquals("NOT_PROVIDED", json.get("contentVersionRelationState").asText());
        assertEquals("NOT_PROVIDED", json.get("artifactRelationState").asText());
        assertEquals("NOT_PROVIDED", json.get("timeMeaning").asText());
        assertSensitiveFieldsPruned(json);
    }

    @Test
    void deniedContentAndArtifactHaveSameStateOnlyShapeForPresentAndAbsentFacts() {
        PublicationPostResponse present = restricted("present");
        PublicationPostResponse absent = restricted("absent");

        JsonNode presentJson = mapper.valueToTree(present);
        JsonNode absentJson = mapper.valueToTree(absent);

        assertEquals(fields(presentJson), fields(absentJson));
        assertFalse(presentJson.has("contentText"));
        assertFalse(presentJson.has("artifactId"));
        assertFalse(absentJson.has("contentText"));
        assertFalse(absentJson.has("artifactId"));
        assertEquals("RESTRICTED", presentJson.get("contentAvailability").asText());
        assertEquals("RESTRICTED", presentJson.get("contentVersionRelationState").asText());
        assertEquals("RESTRICTED", presentJson.get("artifactRelationState").asText());
    }

    private static PublicationPostResponse restricted(String id) {
        return new PublicationPostResponse(
                id, "project-1", "account-1", 7L,
                null, PublicationPostResponse.ContentAvailability.RESTRICTED,
                PublicationPostResponse.ContentVersionRelationState.RESTRICTED,
                null, PublicationPostResponse.ArtifactRelationState.RESTRICTED,
                "YOUTUBE", Instant.parse("2026-01-01T00:00:00Z"),
                PublicationPostResponse.TimeMeaning.PLANNED_PUBLISH_TIME,
                PublicationPostResponse.TimePrecision.EXACT_INSTANT,
                PublicationPostResponse.SourceVerification.VERIFIED_LOCAL_RECORD,
                PublicationPostResponse.EndpointAccess.AUTHORIZED_LOCAL_PROJECT_ACCOUNT_READ,
                PublicationPostResponse.GlobalEffectiveAccess.UNKNOWN_FAIL_CLOSED);
    }

    private static void assertSensitiveFieldsPruned(JsonNode json) {
        assertFalse(json.has("tenantId"));
        assertFalse(json.has("userId"));
        assertFalse(json.has("artifactAttached"));
        assertFalse(json.has("platformPostId"));
        assertFalse(json.has("platformPostUrl"));
        assertFalse(json.has("publicationOutcome"));
        assertFalse(json.has("errorMessage"));
        assertFalse(json.has("attemptCount"));
        assertFalse(json.has("publishedAt"));
        assertFalse(json.has("total"));
    }

    private static Set<String> fields(JsonNode json) {
        return StreamSupport.stream(
                        ((Iterable<String>) () -> json.fieldNames()).spliterator(), false)
                .collect(Collectors.toSet());
    }
}
