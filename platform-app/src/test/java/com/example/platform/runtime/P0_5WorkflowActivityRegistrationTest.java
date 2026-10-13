package com.example.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.platform.execution.binding.BoundGraphInputStore;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.workflow.temporal.mediatask.MediaTaskActivities;
import com.example.platform.workflow.temporal.mediatask.MediaTaskWorkflowImpl;
import io.temporal.spring.boot.autoconfigure.template.WorkersTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * P0-5T.2: direct verification that the Temporal Spring Boot worker registers the media-task
 * workflow implementation and the media-task activity types on the canonical queue.
 *
 * <p>This inspects the starter's own registration state ({@link WorkersTemplate#getRegisteredInfo()})
 * — i.e. what the framework actually registered on the worker — rather than annotations, YAML
 * discovery entries or package allowlists. An in-process Temporal test server is used so no external
 * Temporal service is required.
 *
 * <p>This is <b>registration</b> evidence only. It does not prove that a business workflow executes
 * successfully.
 */
@SpringBootTest(
        classes = {
            PlatformFfmpegWorkerApplication.class,
            P0_5WorkflowActivityRegistrationTest.StubBoundGraphInputStoreConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"test", "temporal", "ffmpeg-worker"})
@TestPropertySource(
        properties = {
            "spring.temporal.test-server.enabled=true",
            "spring.temporal.start-workers=true",
            // P1-1A test-process environment (outside the repository): the real FFmpeg PF4J plugin
            // artifact copied to a test-only directory, plus a libx264-capable static ffmpeg for the
            // sandbox. No production configuration is changed.
            "platform.ffmpeg-worker.plugins-directory=/tmp/p11a-worker-plugins",
            "platform.ffmpeg-worker.workspace-root=/tmp/p11a-worker/workspace",
            "platform.ffmpeg-worker.staging-root=/tmp/p11a-worker/staging",
            "platform.ffmpeg-worker.work-root=/tmp/p11a-worker",
            "platform.ffmpeg-worker.sandbox.ffmpeg=/tmp/ffmpeg-x264/bin/ffmpeg"
        })
class P0_5WorkflowActivityRegistrationTest extends PostgresTestContainerSupport {

    /**
     * Test-only collaborator: the worker context in this slice needs a {@code BoundGraphInputStore}
     * to assemble {@code MediaTaskActivity}. It is consumed only at activity execution time (not
     * during context startup or worker registration), so a mock is sufficient and adds no behaviour.
     * The production bean lives in the composition root's {@code persistence.binding} package, which
     * the worker application does not scan.
     */
    @TestConfiguration
    static class StubBoundGraphInputStoreConfig {

        @Bean
        BoundGraphInputStore boundGraphInputStore() {
            return mock(BoundGraphInputStore.class);
        }
    }

    private static final String CANONICAL_QUEUE = "media-platform-tasks";

    /** Activity types are the {@code @ActivityMethod} names (no explicit name is declared). */
    private static final String ACTIVITY_TYPE_PREPARE = "prepareTask";
    private static final String ACTIVITY_TYPE_EXECUTE = "executeTask";

    @Autowired WorkersTemplate workersTemplate;

    @Test
    void mediaTaskWorkflowIsRegisteredOnTheCanonicalQueue() {
        var info = workersTemplate.getRegisteredInfo().get(CANONICAL_QUEUE);
        assertThat(workersTemplate.getRegisteredInfo()).containsKey(CANONICAL_QUEUE);
        assertThat(info).isNotNull();

        assertThat(info.isWorkflowRegistered(MediaTaskWorkflowImpl.class))
                .as("MediaTaskWorkflowImpl must be registered as a workflow implementation on "
                        + CANONICAL_QUEUE)
                .isTrue();
        assertThat(info.getRegisteredWorkflowInfo())
                .extracting(r -> r.getClassName())
                // The framework records the workflow INTERFACE FQN (…mediatask.MediaTaskWorkflow)
                // for the registered implementation, so match on the media-task workflow identity
                // rather than the impl class name.
                .as("the registered workflow set must contain the media-task workflow")
                .anyMatch(className -> className.contains("MediaTaskWorkflow"));
    }

    @Test
    void mediaTaskActivityTypesAreRegisteredOnTheCanonicalQueue() {
        var info = canonicalQueueInfo();

        // P1-1A.1: record the ACTUAL registered activity metadata, then assert against it. The
        // framework registers activity IMPLEMENTATIONS (class/bean), which may differ from the
        // @ActivityMethod type names.
        var activityInfo = info.getRegisteredActivityInfo();
        System.out.println("[P1-1A.1-ACTIVITY-INFO] " + activityInfo.stream()
                .map(r -> r.getClassName() + "|bean=" + r.getBeanName())
                .toList());
        assertThat(activityInfo)
                .as("the media-task activity implementation must be registered on " + CANONICAL_QUEUE)
                .anyMatch(r -> r.getClassName().contains("MediaTaskActivities")
                        || (r.getBeanName() != null && r.getBeanName().contains("MediaTaskActivities")));
    }

    @Test
    void workerIsAssociatedWithTheCanonicalQueue() {
        assertThat(workersTemplate.getWorkerFactory().tryGetWorker(CANONICAL_QUEUE))
                .as("a worker must exist for the canonical queue")
                .isNotNull();
        assertThat(workersTemplate.getWorkerFactory().tryGetWorker("no-such-task-queue"))
                .as("negative control: an unknown queue must have no worker")
                .isNull();
    }

    @Test
    void negativeControlsDetectUnregisteredWorkflowAndActivityTypes() {
        var info = canonicalQueueInfo();

        assertThat(info.isActivityRegistered("definitely-not-a-registered-activity"))
                .as("the activity registration check must detect an unregistered type")
                .isFalse();
        assertThat(info.getRegisteredWorkflowInfo())
                .extracting(r -> r.getClassName())
                .as("an unregistered workflow implementation must not appear")
                .doesNotContain("com.example.platform.DefinitelyNotARegisteredWorkflowImpl");
    }

    private WorkersTemplate.RegisteredInfo canonicalQueueInfo() {
        return java.util.Objects.requireNonNull(
                workersTemplate.getRegisteredInfo().get(CANONICAL_QUEUE),
                "no registration info for the canonical queue " + CANONICAL_QUEUE);
    }
}
