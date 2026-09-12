package com.example.platform.social.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.platform.social.api.dto.PublicationAccountResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class PublicationAccountResponseContractTest {

    @Test
    void exposesExactFrontendIdentityDisplayScopeAndVersionWithoutProviderMetadata() {
        PublicationAccountResponse response = new PublicationAccountResponse(
                "account-1", "channel", PublicationAccountResponse.DisplayNameAvailability.AVAILABLE,
                "YOUTUBE", "project-1", 4L,
                PublicationAccountResponse.EndpointAccess.AUTHORIZED_LOCAL_PROJECT_ACCOUNT_READ,
                PublicationAccountResponse.GlobalEffectiveAccess.UNKNOWN_FAIL_CLOSED);

        JsonNode json = new ObjectMapper().valueToTree(response);
        Set<String> fields = StreamSupport.stream(
                        ((Iterable<String>) () -> json.fieldNames()).spliterator(), false)
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                "id", "displayName", "displayNameAvailability", "platformType", "projectId",
                "bindingVersion", "endpointAccess", "globalEffectiveAccess"), fields);
        assertFalse(json.has("tenantId"));
        assertFalse(json.has("userId"));
        assertFalse(json.has("platformUserId"));
        assertFalse(json.has("status"));
        assertFalse(json.has("tokenExpiresAt"));
    }
}
