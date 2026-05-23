package com.example.platform.workflow.temporal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

class TemporalWorkflowStarterTest {

    @Test
    void starterIsAnnotatedWithConfiguration() {
        assertNotNull(TemporalWorkflowStarter.class.getAnnotation(Configuration.class),
                "TemporalWorkflowStarter must be annotated with @Configuration");
    }

    @Test
    void starterEnablesAppTemporalProperties() {
        EnableConfigurationProperties annotation = TemporalWorkflowStarter.class.getAnnotation(EnableConfigurationProperties.class);
        assertNotNull(annotation, "TemporalWorkflowStarter must have @EnableConfigurationProperties");
        boolean hasAppTemporalProperties = false;
        for (Class<?> clazz : annotation.value()) {
            if (clazz.equals(AppTemporalProperties.class)) {
                hasAppTemporalProperties = true;
                break;
            }
        }
        assertTrue(hasAppTemporalProperties,
                "@EnableConfigurationProperties must include AppTemporalProperties");
    }

    @Test
    void propertiesClassHasEnabledAccessor() {
        Method enabledMethod = assertDoesNotThrow(() -> AppTemporalProperties.class.getMethod("isEnabled"));
        assertNotNull(enabledMethod);
        Class<?> returnType = enabledMethod.getReturnType();
        assertTrue(boolean.class.equals(returnType) || Boolean.class.equals(returnType),
                "isEnabled() must return boolean");
    }

    private static Method assertDoesNotThrow(Callable<Method> action) {
        try {
            return action.call();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @FunctionalInterface
    interface Callable<T> {
        T call() throws Exception;
    }
}
