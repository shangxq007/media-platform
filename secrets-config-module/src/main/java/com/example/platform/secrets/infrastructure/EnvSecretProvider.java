package com.example.platform.secrets.infrastructure;

import com.example.platform.secrets.api.SecretRef;
import com.example.platform.secrets.api.port.SecretProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class EnvSecretProvider implements SecretProvider {

    private static final Pattern ENV_WITH_DEFAULT = Pattern.compile("^([^:]+):?(.*)$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String backend() {
        return SecretRef.BACKEND_ENV;
    }

    @Override
    public boolean supports(SecretRef ref) {
        return SecretRef.BACKEND_ENV.equals(ref.backend());
    }

    @Override
    public Optional<String> resolveScalar(SecretRef ref) {
        if (ref.field() != null && !ref.field().isBlank()) {
            return Optional.ofNullable(resolveMap(ref).get(ref.field()));
        }
        return Optional.ofNullable(resolveEnvValue(ref.path()));
    }

    @Override
    public Map<String, String> resolveMap(SecretRef ref) {
        String raw = resolveEnvValue(ref.path());
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        if (ref.field() != null && !ref.field().isBlank()) {
            return Map.of(ref.field(), raw);
        }
        if (raw.trim().startsWith("{")) {
            try {
                Map<String, String> parsed = MAPPER.readValue(raw, new TypeReference<>() {});
                return parsed != null ? parsed : Map.of();
            } catch (Exception e) {
                throw new IllegalArgumentException("Env var " + ref.path() + " is not valid JSON map", e);
            }
        }
        return Map.of("value", raw);
    }

    private static String resolveEnvValue(String pathSpec) {
        Matcher matcher = ENV_WITH_DEFAULT.matcher(pathSpec);
        if (!matcher.matches()) {
            return System.getenv(pathSpec);
        }
        String varName = matcher.group(1).trim();
        String defaultVal = matcher.group(2);
        String env = System.getenv(varName);
        if (env != null && !env.isBlank()) {
            return env;
        }
        return (defaultVal != null && !defaultVal.isBlank()) ? defaultVal : null;
    }
}
