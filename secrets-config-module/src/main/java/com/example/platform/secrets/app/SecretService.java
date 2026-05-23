package com.example.platform.secrets.app;

import com.example.platform.secrets.api.SecretRef;
import com.example.platform.secrets.api.port.SecretResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Legacy facade; prefer {@link SecretResolver} in new code.
 */
@Service
public class SecretService {

    private static final Logger log = LoggerFactory.getLogger(SecretService.class);

    private final SecretResolver secretResolver;

    public SecretService(SecretResolver secretResolver) {
        this.secretResolver = secretResolver;
    }

    public String resolve(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        if (!ref.contains(":") && !ref.startsWith("${")) {
            return ref;
        }
        try {
            return secretResolver.resolve(SecretRef.parse(ref)).orElse(null);
        } catch (IllegalArgumentException e) {
            log.debug("Treating ref as literal value");
            return ref;
        }
    }

    public String resolve(String ref, String defaultValue) {
        String value = resolve(ref);
        return value != null ? value : defaultValue;
    }
}
