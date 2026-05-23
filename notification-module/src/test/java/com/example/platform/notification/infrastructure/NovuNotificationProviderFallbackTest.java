package com.example.platform.notification.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.notification.domain.DeliveryCommand;
import com.example.platform.notification.domain.DeliveryResult;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class NovuNotificationProviderFallbackTest {

    private RestClient.Builder restClientBuilder;
    private RestClient restClient;
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    private RestClient.RequestBodySpec requestBodySpec;
    private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        restClientBuilder = mock(RestClient.Builder.class);
        restClient = mock(RestClient.class);
        requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        requestBodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);
    }

    @Test
    void notConfiguredWhenApiKeyMissing() {
        NovuNotificationProvider provider = new NovuNotificationProvider("", "https://api.novu.co/v1", restClientBuilder);
        assertFalse(provider.isEnabled(), "Novu should be disabled when API key is empty");
    }

    @Test
    void notConfiguredWhenApiKeyNull() {
        NovuNotificationProvider provider = new NovuNotificationProvider(null, "https://api.novu.co/v1", restClientBuilder);
        assertFalse(provider.isEnabled(), "Novu should be disabled when API key is null");
    }

    @Test
    void configuredWhenApiKeyPresent() {
        NovuNotificationProvider provider = new NovuNotificationProvider(
                "test-api-key-123", "https://api.novu.co/v1", restClientBuilder);
        assertTrue(provider.isEnabled(), "Novu should be enabled when API key is present");
    }

    @Test
    void channelIsNovu() {
        NovuNotificationProvider provider = new NovuNotificationProvider(
                "key", "https://api.novu.co/v1", restClientBuilder);
        assertEquals("NOVU", provider.channel());
    }

    @Test
    void providerCodeIsNovu() {
        NovuNotificationProvider provider = new NovuNotificationProvider(
                "key", "https://api.novu.co/v1", restClientBuilder);
        assertEquals("novu", provider.providerCode());
    }

    @Test
    void sendThrowsWhenNotConfigured() {
        NovuNotificationProvider provider = new NovuNotificationProvider(
                "", "https://api.novu.co/v1", restClientBuilder);

        DeliveryCommand command = new DeliveryCommand(
                "evt-1", "NOVU", "Subject", "Body",
                Map.of("novuWorkflowId", "workflow-123"));

        PlatformException ex = assertThrows(PlatformException.class, () -> provider.send(command));
        assertEquals("NOTIFICATION-NOVU-503-001", ex.getErrorCode().code());
    }

    @Test
    void sendThrowsWhenMissingWorkflowId() {
        NovuNotificationProvider provider = new NovuNotificationProvider(
                "key", "https://api.novu.co/v1", restClientBuilder);

        DeliveryCommand command = new DeliveryCommand(
                "evt-1", "NOVU", "Subject", "Body", Map.of());

        PlatformException ex = assertThrows(PlatformException.class, () -> provider.send(command));
        assertEquals("NOTIFICATION-NOVU-400-001", ex.getErrorCode().code());
    }

    @Test
    void sendReturnsSuccessOnValidRequest() {
        NovuNotificationProvider provider = new NovuNotificationProvider(
                "key", "https://api.novu.co/v1", restClientBuilder);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/events/trigger")).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Map.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn("{\"data\":{\"triggered\":true}}");

        DeliveryCommand command = new DeliveryCommand(
                "evt-1", "NOVU", "Subject", "Body",
                Map.of("novuWorkflowId", "workflow-123", "subscriberId", "sub-456"));

        DeliveryResult result = provider.send(command);
        assertEquals("SENT", result.status());
        assertTrue(result.responsePayload().contains("triggered"));
    }

    @Test
    void sendReturnsFailedOnRestClientException() {
        NovuNotificationProvider provider = new NovuNotificationProvider(
                "key", "https://api.novu.co/v1", restClientBuilder);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/events/trigger")).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Map.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenThrow(new RestClientException("Connection refused"));

        DeliveryCommand command = new DeliveryCommand(
                "evt-1", "NOVU", "Subject", "Body",
                Map.of("novuWorkflowId", "workflow-123"));

        DeliveryResult result = provider.send(command);
        assertEquals("FAILED", result.status());
        assertTrue(result.responsePayload().contains("Connection refused"));
    }

    @Test
    void sendDoesNotThrowOnFailure() {
        NovuNotificationProvider provider = new NovuNotificationProvider(
                "key", "https://api.novu.co/v1", restClientBuilder);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/events/trigger")).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Map.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenThrow(new RestClientException("Timeout"));

        DeliveryCommand command = new DeliveryCommand(
                "evt-1", "NOVU", "Subject", "Body",
                Map.of("novuWorkflowId", "workflow-123"));

        assertDoesNotThrow(() -> provider.send(command),
                "Novu failure should return result, not throw");
    }

    @Test
    void sendUsesSubjectAsSubscriberIdWhenMissing() {
        NovuNotificationProvider provider = new NovuNotificationProvider(
                "key", "https://api.novu.co/v1", restClientBuilder);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/events/trigger")).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Map.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn("{\"ok\":true}");

        DeliveryCommand command = new DeliveryCommand(
                "evt-1", "NOVU", "user-789", "Body",
                Map.of("novuWorkflowId", "workflow-123"));

        DeliveryResult result = provider.send(command);
        assertEquals("SENT", result.status());
    }

    @Test
    void failureResultContainsErrorMessage() {
        NovuNotificationProvider provider = new NovuNotificationProvider(
                "key", "https://api.novu.co/v1", restClientBuilder);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/events/trigger")).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Map.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenThrow(new RestClientException("502 Bad Gateway"));

        DeliveryCommand command = new DeliveryCommand(
                "evt-1", "NOVU", "Subject", "Body",
                Map.of("novuWorkflowId", "workflow-123"));

        DeliveryResult result = provider.send(command);
        assertTrue(result.responsePayload().contains("502 Bad Gateway"),
                "Failure payload should contain the original error message");
    }
}
