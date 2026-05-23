package com.example.platform.federation.graphql.resolver;

import com.example.platform.billing.app.BillingDecisionService;
import com.example.platform.billing.domain.BillingDecision;
import com.example.platform.entitlement.app.EntitlementDecisionService;
import com.example.platform.entitlement.domain.AccessCheckRequest;
import com.example.platform.entitlement.domain.EntitlementDecision;
import com.example.platform.federation.graphql.context.GraphQLRequestContext;
import com.example.platform.federation.graphql.dto.MeOverview;
import com.example.platform.identity.app.TenantRepository;
import com.example.platform.identity.app.UserRepository;
import com.example.platform.identity.domain.Tenant;
import com.example.platform.identity.domain.User;
import com.example.platform.identity.domain.User.UserRole;
import com.example.platform.identity.domain.User.UserStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeOverviewGraphQLResolverTest {

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void returnsMeOverviewWithUserInfo() throws Exception {
        EntitlementDecisionService entitlementService = mock(EntitlementDecisionService.class);
        BillingDecisionService billingService = mock(BillingDecisionService.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        UserRepository userRepository = mock(UserRepository.class);

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", "ws-1", "user-1",
                List.of("MEMBER"), List.of("render"),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        User user = new User("user-1", "tenant-1", "testuser", "test@example.com", UserRole.MEMBER, UserStatus.ACTIVE, Instant.now());
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        Tenant tenant = new Tenant("tenant-1", "Test Tenant", Tenant.TenantStatus.ACTIVE, Instant.now());
        when(tenantRepository.findById("tenant-1")).thenReturn(Optional.of(tenant));

        EntitlementDecision decision = new EntitlementDecision(
                true, "ALLOW", "TIER", "Access granted", "PRO",
                List.of("tier:PRO"), null, null, null, null,
                null, List.of(), null, false);
        when(entitlementService.evaluate(any(AccessCheckRequest.class))).thenReturn(decision);

        when(billingService.decideBilling(any(), any())).thenReturn(new BillingDecision(
                "dec-1", "summary", "tenant-1", "user-1",
                "USAGE_BASED", 0, "USD", true, Map.of(), "APPROVED"));

        MeOverviewGraphQLResolver resolver = new MeOverviewGraphQLResolver(entitlementService, billingService, tenantRepository, userRepository);

        MeOverview result = resolver.meOverview(ctx);

        assertNotNull(result);
        assertEquals("user-1", result.id());
        assertEquals("testuser", result.displayName());
        assertNotNull(result.currentTenant());
        assertEquals("tenant-1", result.currentTenant().id());
        assertNotNull(result.currentWorkspace());
        assertEquals("ws-1", result.currentWorkspace().id());
        assertFalse(result.capabilities().isEmpty());
        assertFalse(result.navigation().isEmpty());
        assertNotNull(result.billing());
    }

    @Test
    void handlesNullUser() throws Exception {
        EntitlementDecisionService entitlementService = mock(EntitlementDecisionService.class);
        BillingDecisionService billingService = mock(BillingDecisionService.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        UserRepository userRepository = mock(UserRepository.class);

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                null, null, null,
                List.of(), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        when(userRepository.findById(null)).thenReturn(Optional.empty());
        when(entitlementService.evaluate(any())).thenReturn(new EntitlementDecision(
                true, "ALLOW", "DEFAULT", "ok", "FREE",
                List.of(), null, null, null, null,
                null, List.of(), null, false));
        when(billingService.decideBilling(any(), any())).thenReturn(new BillingDecision(
                "dec-1", "summary", null, null,
                "USAGE_BASED", 0, "USD", true, Map.of(), "APPROVED"));

        MeOverviewGraphQLResolver resolver = new MeOverviewGraphQLResolver(entitlementService, billingService, tenantRepository, userRepository);

        MeOverview result = resolver.meOverview(ctx);

        assertNotNull(result);
        assertEquals("Anonymous", result.displayName());
        assertNull(result.currentTenant());
        assertNull(result.currentWorkspace());
    }
}
