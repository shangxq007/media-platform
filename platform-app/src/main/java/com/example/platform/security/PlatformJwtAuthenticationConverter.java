package com.example.platform.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Converts OIDC JWT to Spring authorities (roles/groups + scope).
 */
public class PlatformJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final OAuth2SecurityProperties oauth2Properties;
    private final JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

    public PlatformJwtAuthenticationConverter(OAuth2SecurityProperties oauth2Properties) {
        this.oauth2Properties = oauth2Properties;
        scopeConverter.setAuthorityPrefix("SCOPE_");
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        Collection<GrantedAuthority> scopes = scopeConverter.convert(jwt);
        if (scopes != null) {
            authorities.addAll(scopes);
        }
        for (String role : JwtClaimSupport.roles(jwt, oauth2Properties.rolesClaim())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + normalizeRole(role)));
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private static String normalizeRole(String role) {
        String trimmed = role.trim();
        if (trimmed.startsWith("ROLE_")) {
            return trimmed.substring("ROLE_".length());
        }
        return trimmed;
    }
}
