package com.example.platform.thumbnail;

import com.example.platform.providerplugin.execution.RuntimeExecutionBackends;
import com.example.platform.sandbox.execution.ExecutionBackend;
import com.example.platform.sandbox.execution.ExecutionBackendRegistry;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Worker-role execution composition, mirroring {@code CoverImageWorkerRuntimeConfiguration}.
 *
 * <p>The thumbnail provider resolves its sandbox backend through {@link ExecutionBackendRegistry} for
 * {@code TaskCapability.THUMBNAIL}. The API-level {@code ProviderPluginManagerConfiguration} composes a
 * fixed LocalProcess/BMF pair that contains no thumbnail backend, and it is not part of the worker
 * role, so the worker composes the canonical {@link RuntimeExecutionBackends} from the Spring-managed
 * {@link ExecutionBackend} beans of this context instead. Duplicate bindings still fail closed inside
 * {@code RuntimeExecutionBackends}, and a capability with no backend simply has no resolution — no
 * fallback backend is registered.
 */
@Configuration
@ConditionalOnProperty(name = "platform.runtime.role", havingValue = "WORKER")
public class ThumbnailWorkerRuntimeConfiguration {

    @Bean
    @ConditionalOnMissingBean(ExecutionBackendRegistry.class)
    ExecutionBackendRegistry thumbnailExecutionBackendRegistry(List<ExecutionBackend> backends) {
        return new RuntimeExecutionBackends(backends);
    }
}
