package com.example.platform.delivery.app;
import com.example.platform.security.*;

import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.platform.JwtTestHelper;
import com.example.platform.delivery.api.*;
import com.example.platform.delivery.app.*;
import com.example.platform.identity.app.PermissionService;
import com.example.platform.identity.app.RbacAuthorizationDecisionPort;
import com.example.platform.secrets.api.port.CredentialBundlePort;
import com.example.platform.shared.audit.AdminAuditPublisher;
import com.example.platform.shared.authorization.*;
import java.util.*;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** Real JWT signature/filter chain, canonical actor, RBAC adapter, controller and application access checks. */
@SpringJUnitConfig(DeliverySecurityChainTest.Config.class)
@WebAppConfiguration
@org.springframework.test.context.ActiveProfiles("ep14-security-chain")
class DeliverySecurityChainTest {
    static final JwtProperties JWT = new JwtProperties("test-only-delivery-chain-key-with-at-least-256-bits!", 3600000);
    @Autowired WebApplicationContext context;
    @Autowired DeliveryJobService jobs;
    MockMvc mvc;

    @BeforeEach void setup() {
        reset(jobs);
        when(jobs.retryDelivery(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
    }
    String token(String user, String tenant, String role) {
        return "Bearer " + new JwtTestHelper(JWT).createToken(user, tenant, List.of(role));
    }
    String retry(String tenant, String project) {
        return "/api/tenants/" + tenant + "/projects/" + project + "/render-jobs/render-a/deliveries/delivery-a/retry";
    }
    @Test void authenticatedAuthorizedManagementReachesApplication() throws Exception {
        mvc.perform(post(retry("tenant-a", "project-a")).header("Authorization", token("editor", "tenant-a", "EDITOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        verify(jobs).retryDelivery("tenant-a", "project-a", "render-a", "delivery-a");
    }
    @Test void missingAuthenticationIsRejected() throws Exception {
        mvc.perform(post(retry("tenant-a", "project-a"))).andExpect(status().isUnauthorized());
        verifyNoInteractions(jobs);
    }
    @Test void invalidSignatureIsRejected() throws Exception {
        mvc.perform(post(retry("tenant-a", "project-a")).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        verifyNoInteractions(jobs);
    }
    @Test void authenticatedWithoutPermissionCannotMutate() throws Exception {
        mvc.perform(post(retry("tenant-a", "project-a")).header("Authorization", token("viewer", "tenant-a", "VIEWER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(jobs);
    }
    @Test void pathTenantCannotOverrideAuthenticatedTenant() throws Exception {
        mvc.perform(post(retry("tenant-b", "project-a")).header("Authorization", token("editor", "tenant-a", "EDITOR"))
                .header("X-Tenant-ID", "tenant-b")).andExpect(status().isForbidden());
        verifyNoInteractions(jobs);
    }
    @Test void projectPermissionIsScoped() throws Exception {
        mvc.perform(post(retry("tenant-a", "project-b")).header("Authorization", token("editor", "tenant-a", "EDITOR")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(jobs);
    }
    @Test void administrativeCrossTenantPathUsesExistingAdminRole() throws Exception {
        String path = "/api/admin/delivery/destination-uri-prefixes";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", token("editor", "tenant-a", "EDITOR"))).andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization", token("admin", "tenant-z", "ADMIN"))).andExpect(status().isOk());
    }

    @org.springframework.boot.test.context.TestConfiguration
    @Profile("ep14-security-chain") @EnableWebMvc @EnableWebSecurity
    static class Config {
        @Bean JwtAuthFilter jwt() { return new JwtAuthFilter(JWT); }
        @Bean SecurityFilterChain chain(HttpSecurity http, JwtAuthFilter jwt) throws Exception {
            return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(SecurityHttpRules::applyApiAuthorization)
                    .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class).build();
        }
        @Bean CanonicalActorResolver actor() { return new RequestAttributesCanonicalActorResolver(); }
        @Bean AuthorizationDecisionPort rbac() {
            PermissionService permissions = mock(PermissionService.class);
            when(permissions.hasPermission("editor", "project-a", "delivery.manage")).thenReturn(true);
            return new RbacAuthorizationDecisionPort(permissions);
        }
        @Bean DeliveryAccess access(CanonicalActorResolver actor, AuthorizationDecisionPort authorization) {
            return new DeliveryAccess(actor, authorization, (tenant, project) -> "tenant-a".equals(tenant) && "project-a".equals(project));
        }
        @Bean DeliveryJobService jobs() { return mock(DeliveryJobService.class); }
        @Bean DeliveryAdministrationService application(DeliveryJobService jobs, DeliveryAccess access) {
            return new DeliveryAdministrationService(mock(DSLContext.class), jobs,
                    mock(DeliveryDestinationCredentialService.class), mock(CredentialBundlePort.class), access);
        }
        @Bean DeliveryController controller(DeliveryAdministrationService app) { return new DeliveryController(app); }
        @Bean DeliveryAdminController admin(DeliveryAdministrationService app) {
            return new DeliveryAdminController(app, mock(DeliveryCredentialMigrationService.class),
                    mock(DeliveryRemoteUriIndexService.class), mock(DeliveryDestinationUriIndexService.class), mock(AdminAuditPublisher.class));
        }
    }
}
