package com.example.platform.identity.app;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class ProjectImportExecuteServiceTransactionTest {

    @Test
    void executeShellImportShouldBeTransactional() throws NoSuchMethodException {
        // Given
        Method method = ProjectImportExecuteService.class.getMethod(
                "executeShellImport", String.class, String.class,
                com.example.platform.identity.api.dto.ProjectExportPackageDto.class);

        // When
        Transactional transactional = method.getAnnotation(Transactional.class);

        // Then
        assertNotNull(transactional, "executeShellImport must be annotated with @Transactional");
    }

    @Test
    void serviceShouldHaveTransactionalAnnotationOnMethod() {
        // Verify that the service uses programmatic transaction management
        // This is a design verification test
        assertTrue(
                java.util.Arrays.stream(ProjectImportExecuteService.class.getDeclaredMethods())
                        .anyMatch(m -> m.isAnnotationPresent(Transactional.class)),
                "Service must have at least one @Transactional method"
        );
    }

    @Test
    void transactionalAnnotationShouldExistOnClassOrMethod() {
        // Check if @Transactional is on the class level
        Transactional classAnnotation = ProjectImportExecuteService.class.getAnnotation(Transactional.class);
        // If not on class, must be on executeShellImport method
        if (classAnnotation == null) {
            try {
                Method method = ProjectImportExecuteService.class.getMethod(
                        "executeShellImport", String.class, String.class,
                        com.example.platform.identity.api.dto.ProjectExportPackageDto.class);
                assertNotNull(method.getAnnotation(Transactional.class),
                        "@Transactional must be on class or executeShellImport method");
            } catch (NoSuchMethodException e) {
                fail("executeShellImport method not found");
            }
        }
    }
}
