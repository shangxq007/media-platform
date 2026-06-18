package com.example.platform.security;

import com.example.platform.identity.app.ApiKeyAuthFilter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({OAuth2SecurityProperties.class, JwtProperties.class})
@ConditionalOnProperty(name = "app.security.enabled", havingValue = "true")
@ConditionalOnProperty(name = "app.security.oauth2.enabled", havingValue = "true")
public class OAuth2ResourceServerSecurityConfiguration {

    private final ObjectProvider<ApiKeyAuthFilter> apiKeyAuthFilterProvider;
    private final OAuth2SecurityProperties oauth2Properties;
    private final JwtProperties jwtProperties;

    public OAuth2ResourceServerSecurityConfiguration(
            ObjectProvider<ApiKeyAuthFilter> apiKeyAuthFilterProvider,
            OAuth2SecurityProperties oauth2Properties,
            JwtProperties jwtProperties) {
        this.apiKeyAuthFilterProvider = apiKeyAuthFilterProvider;
        this.oauth2Properties = oauth2Properties;
        this.jwtProperties = jwtProperties;
    }

    @Bean
    @Order(1)
    FilterRegistrationBean<ApiKeyAuthFilter> mcpApiKeyAuthFilterRegistration() {
        ApiKeyAuthFilter apiKeyAuthFilter = apiKeyAuthFilterProvider.getIfAvailable();
        if (apiKeyAuthFilter == null) {
            return null;
        }
        FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>(apiKeyAuthFilter);
        registration.addUrlPatterns("/api/v1/mcp/*");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    JwtDecoder platformJwtDecoder() {
        if (!oauth2Properties.hasIssuer()) {
            throw new IllegalStateException(
                    "app.security.oauth2.enabled=true requires app.security.oauth2.issuer-uri "
                            + "(or SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI)");
        }

        List<JwtDecoder> decoders = new ArrayList<>();
        NimbusJwtDecoder oidcDecoder = JwtDecoders.fromIssuerLocation(oauth2Properties.issuerUri());
        oidcDecoder.setJwtValidator(jwtValidator());
        decoders.add(oidcDecoder);

        if (oauth2Properties.legacyHmacJwtEnabled()) {
            decoders.add(new LegacyHmacJwtDecoder(jwtProperties));
        }

        if (decoders.size() == 1) {
            return decoders.getFirst();
        }
        return new CompositeJwtDecoder(decoders);
    }

    private OAuth2TokenValidator<Jwt> jwtValidator() {
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefaultWithIssuer(oauth2Properties.issuerUri()));
        if (StringUtils.hasText(oauth2Properties.audience())) {
            validators.add(new org.springframework.security.oauth2.jwt.JwtClaimValidator<>(
                    "aud", aud -> audienceMatches(aud, oauth2Properties.audience())));
        }
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    @SuppressWarnings("unchecked")
    private static boolean audienceMatches(Object audClaim, String expected) {
        if (audClaim == null) {
            return false;
        }
        if (audClaim instanceof String s) {
            return expected.equals(s);
        }
        if (audClaim instanceof List<?> list) {
            return list.stream().anyMatch(v -> expected.equals(String.valueOf(v)));
        }
        return false;
    }

    @Bean
    @Order(2)
    SecurityFilterChain oauth2SecurityFilterChain(
            HttpSecurity http,
            JwtDecoder platformJwtDecoder,
            PlatformJwtAuthenticationConverter jwtAuthenticationConverter,
            OAuth2RequestContextFilter requestContextFilter,
            TenantHeaderGuardFilter tenantHeaderGuardFilter,
            OidcUserProvisioningFilter oidcUserProvisioningFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                    .contentSecurityPolicy(csp -> csp
                        .policyDirectives(SecurityFilterChainConfig.buildCspDirectives()))
                    .contentTypeOptions(contentTypeOptions -> {})
                    .referrerPolicy(referrer -> referrer
                        .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                    .frameOptions(frame -> frame.deny())
                    .permissionsPolicyHeader(permissions -> permissions
                        .policy("camera=(), microphone=(), geolocation=(), payment=(), usb=(), magnetometer=(), gyroscope=(), accelerometer=()")))
                .authorizeHttpRequests(auth -> SecurityHttpRules.applyApiAuthorization(auth))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(platformJwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .addFilterAfter(requestContextFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(tenantHeaderGuardFilter, OAuth2RequestContextFilter.class)
                .addFilterAfter(oidcUserProvisioningFilter, TenantHeaderGuardFilter.class);

        return http.build();
    }

    @Bean
    PlatformJwtAuthenticationConverter platformJwtAuthenticationConverter() {
        return new PlatformJwtAuthenticationConverter(oauth2Properties);
    }

    @Bean
    OAuth2RequestContextFilter oauth2RequestContextFilter() {
        return new OAuth2RequestContextFilter(oauth2Properties);
    }

    @Bean
    TenantHeaderGuardFilter tenantHeaderGuardFilter() {
        return new TenantHeaderGuardFilter(oauth2Properties);
    }

    @Bean
    OidcUserProvisioningFilter oidcUserProvisioningFilter(
            OidcIdentityProvisioningService provisioningService) {
        return new OidcUserProvisioningFilter(provisioningService);
    }
}
