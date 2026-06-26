package com.example.platform.render.app.environment;

import com.example.platform.render.domain.environment.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Environment Runtime — discovers environments and compilers via Spring.
 * No registry table. No execution.
 */
@Service
public class EnvironmentRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentRuntimeService.class);
    private final Map<String, ExecutionEnvironment> environments = new LinkedHashMap<>();
    private final Map<String, EnvironmentCompiler> compilers = new LinkedHashMap<>();

    public EnvironmentRuntimeService(List<ExecutionEnvironment> allEnvs,
                                       List<EnvironmentCompiler> allCompilers) {
        for (ExecutionEnvironment e : allEnvs) {
            environments.put(e.environmentType(), e);
            log.info("Environment registered: type={}", e.environmentType());
        }
        for (EnvironmentCompiler c : allCompilers) {
            compilers.put(c.environmentType(), c);
            log.info("Environment compiler registered: type={}", c.environmentType());
        }
    }

    public Optional<ExecutionEnvironment> resolve(String environmentType) {
        return Optional.ofNullable(environments.get(environmentType));
    }

    public Optional<EnvironmentCompiler> resolveCompiler(String environmentType) {
        return Optional.ofNullable(compilers.get(environmentType));
    }

    public List<String> listEnvironments() {
        return new ArrayList<>(environments.keySet());
    }

    public List<String> listCompilers() {
        return new ArrayList<>(compilers.keySet());
    }
}
