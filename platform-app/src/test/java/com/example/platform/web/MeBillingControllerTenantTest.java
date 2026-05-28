package com.example.platform.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.billing.app.BillingLedgerService;
import com.example.platform.billing.app.CreditWalletService;
import com.example.platform.billing.app.SubscriptionBillingService;
import com.example.platform.commerce.app.CommerceCatalogService;
import com.example.platform.entitlement.app.EntitlementPolicyService;
import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class MeBillingControllerTenantTest {

    @Mock
    private SubscriptionBillingService subscriptionBillingService;

    @Mock
    private CreditWalletService creditWalletService;

    @Mock
    private BillingLedgerService billingLedgerService;

    @Mock
    private EntitlementPolicyService entitlementPolicyService;

    @Mock
    private CommerceCatalogService commerceCatalogService;

    private MeBillingController controller;

    @BeforeEach
    void setUp() {
        controller = new MeBillingController(subscriptionBillingService, creditWalletService,
                billingLedgerService, entitlementPolicyService, commerceCatalogService);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveSubjectUsesTenantContext() {
        TenantContext.set("tenant-a");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.subject", "user-1");
        request.setAttribute("jwt.tenantId", "tenant-a");

        when(subscriptionBillingService.getCurrentSubscription("tenant-a", "user-1")).thenReturn(null);
        when(entitlementPolicyService.getTier("tenant-a")).thenReturn("FREE");

        var response = controller.getCurrentPlan(request);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        verify(subscriptionBillingService).getCurrentSubscription("tenant-a", "user-1");
        verify(subscriptionBillingService, never()).getCurrentSubscription(eq("tenant-1"), any());
    }

    @Test
    void resolveSubjectUsesJwtTenantIdWhenTenantContextNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.subject", "user-1");
        request.setAttribute("jwt.tenantId", "tenant-b");

        when(subscriptionBillingService.getCurrentSubscription("tenant-b", "user-1")).thenReturn(null);
        when(entitlementPolicyService.getTier("tenant-b")).thenReturn("FREE");

        var response = controller.getCurrentPlan(request);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        verify(subscriptionBillingService).getCurrentSubscription("tenant-b", "user-1");
    }

    @Test
    void resolveSubjectThrowsWithoutAnyTenant() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.subject", "user-1");

        assertThrows(IllegalArgumentException.class,
                () -> controller.getCurrentPlan(request));
    }

    @Test
    void resolveSubjectThrowsWithoutIdentityAndTenant() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThrows(IllegalArgumentException.class,
                () -> controller.getCurrentPlan(request));
    }

    @Test
    void resolveSubjectDoesNotFallbackToTenant1() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.subject", "user-1");

        assertThrows(IllegalArgumentException.class,
                () -> controller.getCurrentPlan(request));

        verify(subscriptionBillingService, never()).getCurrentSubscription(eq("tenant-1"), any());
    }

    @Test
    void resolveSubjectIgnoresFakeTenantIdParameter() {
        TenantContext.set("real-tenant");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.subject", "user-1");
        request.setAttribute("jwt.tenantId", "real-tenant");

        when(subscriptionBillingService.getCurrentSubscription("real-tenant", "user-1")).thenReturn(null);
        when(entitlementPolicyService.getTier("real-tenant")).thenReturn("FREE");

        var response = controller.getCurrentPlan(request);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        verify(subscriptionBillingService).getCurrentSubscription("real-tenant", "user-1");
        verify(subscriptionBillingService, never()).getCurrentSubscription(eq("fake-tenant"), any());
    }
}
