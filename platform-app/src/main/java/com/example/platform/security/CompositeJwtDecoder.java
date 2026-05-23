package com.example.platform.security;

import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Tries multiple decoders (OIDC issuer JWKS, then optional legacy HMAC) for gradual migration.
 */
public class CompositeJwtDecoder implements JwtDecoder {

    private final List<JwtDecoder> decoders;

    public CompositeJwtDecoder(List<JwtDecoder> decoders) {
        if (decoders == null || decoders.isEmpty()) {
            throw new IllegalArgumentException("At least one JwtDecoder is required");
        }
        this.decoders = List.copyOf(decoders);
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        JwtException last = null;
        for (JwtDecoder decoder : decoders) {
            try {
                return decoder.decode(token);
            } catch (JwtException ex) {
                last = ex;
            }
        }
        if (last != null) {
            throw last;
        }
        throw new JwtException("Unable to decode JWT");
    }
}
