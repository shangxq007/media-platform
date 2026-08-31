package com.example.platform.web.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.TenantContext;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TimelineProjectAuthorizationServiceTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void readAndWriteBindActorTenantProjectAndReturnServerPrincipal() {
        TenantContext.set("tenant-1");
        AtomicReference<String> action = new AtomicReference<>();
        var service = new TimelineProjectAuthorizationService(
                request -> {
                    action.set(request.action().permissionKey());
                    assertEquals("project-1", request.resource().projectId());
                    assertEquals("tenant-1", request.resource().tenantId());
                    return AuthorizationDecision.allow("test");
                },
                () -> Optional.of(CanonicalActor.user(
                        "server-user", "tenant-1", Set.of("EDITOR"), "test")));

        assertEquals("server-user", service.requireRead("tenant-1", "project-1").actorId());
        assertEquals("READ", action.get());
        assertEquals("server-user", service.requireWrite("tenant-1", "project-1").actorId());
        assertEquals("WRITE", action.get());
    }

    @Test
    void tenantMismatchFailsBeforeAuthorizationDecision() {
        TenantContext.set("tenant-ambient");
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        var service = new TimelineProjectAuthorizationService(
                request -> {
                    called.set(true);
                    return AuthorizationDecision.allow("test");
                },
                () -> Optional.of(CanonicalActor.user(
                        "server-user", "tenant-explicit", Set.of(), "test")));

        assertThrows(ResponseStatusException.class,
                () -> service.requireRead("tenant-explicit", "project-1"));
        assertEquals(false, called.get());
    }

    @Test
    void missingActorFailsUnauthenticatedBeforeDisclosure() {
        TenantContext.set("tenant-1");
        var service = new TimelineProjectAuthorizationService(
                request -> AuthorizationDecision.allow("test"), Optional::empty);

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.requireRead("tenant-1", "project-1"));
        assertEquals(401, failure.getStatusCode().value());
    }
}
