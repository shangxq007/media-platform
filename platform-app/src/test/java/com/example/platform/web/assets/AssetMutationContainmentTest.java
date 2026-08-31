package com.example.platform.web.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.platform.shared.authorization.AuthorizationDeniedException;
import com.example.platform.web.media.AssetIntegrityScanController;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

class AssetMutationContainmentTest {

    @Test
    void enrichmentRoutesDenyBeforeCallerUriProviderOrStatusMutation() throws Exception {
        assertMappedMethodsUnavailable(AssetEnrichmentController.class, Set.of(
                "enrich", "status", "listProviders"));
    }

    @Test
    void projectIntegrityScanDeniesBeforeProjectAuditMutation() throws Exception {
        assertMappedMethodsUnavailable(AssetIntegrityScanController.class, Set.of("scan"));
    }

    @Test
    void assetLifecycleMutationsDenyBeforeRequestActorOrIdOnlyMutation() throws Exception {
        assertMappedMethodsUnavailable(AssetPublishController.class, Set.of(
                "submitReview", "approve", "reject", "publish", "archive"));
    }

    private static void assertMappedMethodsUnavailable(
            Class<?> controllerType, Set<String> expectedMethods) throws Exception {
        Constructor<?> constructor = controllerType.getConstructors()[0];
        Object[] dependencies = Arrays.stream(constructor.getParameterTypes())
                .map(type -> org.mockito.Mockito.mock(type))
                .toArray();
        Object controller = constructor.newInstance(dependencies);
        List<Method> mappings = Arrays.stream(controllerType.getDeclaredMethods())
                .filter(method -> expectedMethods.contains(method.getName()))
                .filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class))
                .toList();

        assertEquals(expectedMethods.size(), mappings.size(),
                "expected contained mappings changed for " + controllerType.getSimpleName());
        for (Method mapping : mappings) {
            InvocationTargetException invocation = assertThrows(
                    InvocationTargetException.class,
                    () -> mapping.invoke(controller, nullArguments(mapping)));
            AuthorizationDeniedException failure = assertInstanceOf(
                    AuthorizationDeniedException.class, invocation.getCause(), mapping.toGenericString());
            assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
        }
        verifyNoInteractions(dependencies);
    }

    private static Object[] nullArguments(Method method) {
        return Arrays.stream(method.getParameterTypes())
                .map(AssetMutationContainmentTest::defaultValue)
                .toArray();
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }
}
