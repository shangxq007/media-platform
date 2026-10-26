package com.example.platform.runtime.mediatask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import java.time.Clock;
import org.junit.jupiter.api.Test;

/** Retains original scope regression cases using the accepted tenant/job/project argument order. */
class MediaTaskPublicationSettingsTest {
    private MediaTaskPublicationSettings settings() {
        return new MediaTaskPublicationSettings("", "provider-a", "local",
                ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER, Clock.systemUTC());
    }
    @Test
    void usesTheAuthoritativeProjectWhenNoDeploymentExpectationIsSet() {
        var context=settings().contextFor("tenant-a", "job-a", "project-persisted");
        assertThat(context.tenantId()).isEqualTo("tenant-a");
        assertThat(context.projectId()).isEqualTo("project-persisted");
        assertThat(context.renderJobId()).isEqualTo("job-a");
    }
    @Test
    void rejectsABlankAuthoritativeProject() {
        assertThatThrownBy(()->settings().contextFor("tenant-a", "job-a", " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("projectId");
    }
}
