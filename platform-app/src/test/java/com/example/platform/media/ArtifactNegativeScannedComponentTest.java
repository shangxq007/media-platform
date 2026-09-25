package com.example.platform.media;

import com.example.platform.PlatformApplication;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Regression proof: a realistic legacy component reintroduced through normal
 * component scanning is rejected by the same runtime bean-graph assertion.
 * The only active profile is the explicit negative fixture profile; it does not
 * activate the positive {@code legacy-media-disabled} profile.
 */
@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("v26-negative")
class ArtifactNegativeScannedComponentTest extends PostgresTestContainerSupport {
    @Autowired
    private ConfigurableApplicationContext context;

    @Test
    void realScannedNegativeFixture_isRejectedByRuntimeValidation() {
        ArtifactDefaultProfileBeanGraphTest.assertRejected(context);
    }
}
