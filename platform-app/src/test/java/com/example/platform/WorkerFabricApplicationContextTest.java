package com.example.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.workerfabric.infrastructure.JooqAtomicAssignmentGrantBoundary;
import com.example.platform.workerfabric.infrastructure.JooqExecutionBackendSelectionAuthority;
import com.example.platform.workerfabric.infrastructure.JooqExecutionAuthorityBoundary;
import com.example.platform.workerfabric.infrastructure.JooqExecutionLifecycleBoundary;
import com.example.platform.workerfabric.infrastructure.JooqWorkerFabricRegistrationBoundary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** Verifies platform-app boots with worker-fabric production persistence beans on its classpath. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"test", "preview"})
@TestPropertySource(properties = {
    "app.security.enabled=false",
    "app.identity.api-key-auth-enabled=false"
})
class WorkerFabricApplicationContextTest extends PostgresTestContainerSupport {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void workerFabricPersistenceBeansAreLoadedByPlatformApplication() {
        assertThat(applicationContext.getBean(JooqAtomicAssignmentGrantBoundary.class))
                .isNotNull();
        assertThat(applicationContext.getBean(JooqWorkerFabricRegistrationBoundary.class))
                .isNotNull();
        assertThat(applicationContext.getBean(JooqExecutionBackendSelectionAuthority.class))
                .isNotNull();
        assertThat(applicationContext.getBean(JooqExecutionLifecycleBoundary.class))
                .isNotNull();
        assertThat(applicationContext.getBean(JooqExecutionAuthorityBoundary.class))
                .isNotNull();
    }
}
