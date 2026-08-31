package com.example.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.Modulith;
import org.springframework.modulith.core.ApplicationModules;

@Modulith
class StudioModulithTest {
    @Test
    void studioIsAnExplicitBoundedModuleWithOnlySharedKernelAllowed() throws Exception {
        var metadata = Class.forName("com.example.platform.studio.package-info").getAnnotation(ApplicationModule.class);
        assertThat(metadata).isNotNull();
        assertThat(Arrays.asList(metadata.allowedDependencies())).containsExactly("shared");

        var modules = ApplicationModules.of(StudioModulithTest.class);
        assertThat(modules.getModuleByName("studio")).isPresent();
        modules.verify();
    }
}
