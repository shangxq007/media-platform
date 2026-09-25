package com.example.platform.coverimage;

import com.example.platform.providerplugin.execution.RuntimeExecutionBackends;
import com.example.platform.sandbox.execution.ExecutionBackend;
import com.example.platform.sandbox.execution.ExecutionBackendRegistry;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Worker-role execution composition (COVER-PROVIDER-001 defect 1).
 *
 * <p>The provider resolves the sandbox backend through {@link ExecutionBackendRegistry} for
 * {@code TaskCapability.COVER_IMAGE}. The API-side
 * {@code ProviderPluginManagerConfiguration.executionBackendRegistry()} composes a fixed
 * {@code LocalProcessExecutionBackend}/{@code BmfExecutionBackend} pair, which contains no cover
 * backend, so the worker role composes the canonical {@link RuntimeExecutionBackends} from the
 * Spring-managed {@link ExecutionBackend} beans of this context instead. Duplicate bindings still
 * fail closed inside {@code RuntimeExecutionBackends}, and a capability with no backend simply has no
 * resolution — no fallback backend is registered.
 */
@Configuration
@ConditionalOnProperty(name = "platform.runtime.role", havingValue = "WORKER")
public class CoverImageWorkerRuntimeConfiguration {

    @Bean
    @ConditionalOnMissingBean(ExecutionBackendRegistry.class)
    ExecutionBackendRegistry coverImageExecutionBackendRegistry(List<ExecutionBackend> backends) {
        return new RuntimeExecutionBackends(backends);
    }
}
