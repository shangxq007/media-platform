package com.example.platform;

import com.example.platform.render.api.RenderController;
import com.example.platform.render.app.RenderOrchestratorService;
import com.example.platform.render.app.RenderJobExecutionService;
import com.example.platform.render.app.RenderJobClaimService;
import com.example.platform.render.app.RenderJobFailureService;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Diagnostic test: prove which RenderController instance handles /start
 * and whether orchestratorPort is null.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.ActiveProfiles({"test", "preview"})
@org.springframework.test.context.TestPropertySource(properties = {
        "app.security.enabled=false",
        "app.identity.api-key-auth-enabled=false",
        "render.providers.ffmpeg.enabled=true",
        "render.execution.mode=local",
        "render.synthetic.enabled=true"
})
public class RenderJobInstanceProvenanceTest extends PostgresTestContainerSupport {

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    RenderController renderController;

    @Autowired(required = false)
    RenderOrchestratorService orchestratorService;

    @Autowired(required = false)
    RenderJobExecutionService executionService;

    @Autowired(required = false)
    RenderJobClaimService claimService;

    @Autowired(required = false)
    RenderJobFailureService failureService;

    @Test
    void beanGraph_allRequiredBeansExist() {
        assertNotNull(renderController, "RenderController must be a Spring Bean");
        assertNotNull(orchestratorService, "RenderOrchestratorService must be a Spring Bean");
        assertNotNull(executionService, "RenderJobExecutionService must be a Spring Bean");
        assertNotNull(claimService, "RenderJobClaimService must be a Spring Bean");
        assertNotNull(failureService, "RenderJobFailureService must be a Spring Bean");

        System.out.println("=== BEAN GRAPH ===");
        System.out.println("RenderController: " + renderController.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(renderController)));
        System.out.println("OrchestratorService: " + orchestratorService.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(orchestratorService)));
        System.out.println("ExecutionService: " + executionService.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(executionService)));
        System.out.println("ClaimService: " + claimService.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(claimService)));
        System.out.println("FailureService: " + failureService.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(failureService)));
    }

    @Test
    void controllerOrchestratorPort_isNotNull() throws Exception {
        // Use reflection to check orchestratorPort field
        Field field = RenderController.class.getDeclaredField("orchestratorPort");
        field.setAccessible(true);
        Object orchestratorPort = field.get(renderController);

        System.out.println("=== ORCHESTRATOR PORT ===");
        System.out.println("orchestratorPort: " + orchestratorPort);
        System.out.println("orchestratorPort class: " + (orchestratorPort != null ? orchestratorPort.getClass().getName() : "NULL"));
        System.out.println("orchestratorPort identity: " + (orchestratorPort != null ? Integer.toHexString(System.identityHashCode(orchestratorPort)) : "NULL"));

        assertNotNull(orchestratorPort, "orchestratorPort must not be null in the Spring-managed RenderController");

        // Compare with the ApplicationContext orchestrator
        if (orchestratorService != null) {
            System.out.println("orchestratorService identity: " + Integer.toHexString(System.identityHashCode(orchestratorService)));
            System.out.println("orchestratorPort is same as orchestratorService: " + (orchestratorPort == orchestratorService));
        }
    }

    @Test
    void controllerConstructor_isFullAutowiredConstructor() {
        // The Spring-managed Controller should have orchestratorPort non-null
        // This proves the full @Autowired constructor was used, not the simple one
        System.out.println("=== CONSTRUCTOR PROVENANCE ===");
        System.out.println("Controller runtime class: " + renderController.getClass().getName());
        System.out.println("Controller identity: " + Integer.toHexString(System.identityHashCode(renderController)));

        // If orchestratorPort is null, the simple constructor was used
        try {
            Field field = RenderController.class.getDeclaredField("orchestratorPort");
            field.setAccessible(true);
            Object port = field.get(renderController);
            if (port == null) {
                fail("orchestratorPort is NULL — the simple constructor RenderController(RenderJobService) was used instead of the @Autowired full constructor!");
            }
        } catch (Exception e) {
            fail("Failed to read orchestratorPort: " + e.getMessage());
        }
    }
}
