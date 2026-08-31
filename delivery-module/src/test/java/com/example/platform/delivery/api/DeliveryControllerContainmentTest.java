package com.example.platform.delivery.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.platform.delivery.app.DeliveryDestinationCredentialService;
import com.example.platform.delivery.app.DeliveryJobService;
import com.example.platform.secrets.api.port.CredentialBundlePort;
import com.example.platform.shared.authorization.AuthorizationDeniedException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

class DeliveryControllerContainmentTest {

    @Test
    void everyMappedDeliveryRouteDeniesBeforePersistenceCredentialsOrProviderExecution() {
        DSLContext dsl = mock(DSLContext.class);
        DeliveryJobService jobs = mock(DeliveryJobService.class);
        DeliveryDestinationCredentialService credentials = mock(DeliveryDestinationCredentialService.class);
        CredentialBundlePort credentialBundles = mock(CredentialBundlePort.class);
        DeliveryController controller = new DeliveryController(dsl, jobs, credentials, credentialBundles);

        List<Method> mappings = Arrays.stream(DeliveryController.class.getDeclaredMethods())
                .filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class))
                .toList();

        assertEquals(12, mappings.size(), "all delivery mappings must remain explicitly contained");
        for (Method mapping : mappings) {
            InvocationTargetException invocation = assertThrows(
                    InvocationTargetException.class,
                    () -> mapping.invoke(controller, nullArguments(mapping)));
            AuthorizationDeniedException failure = assertInstanceOf(
                    AuthorizationDeniedException.class, invocation.getCause(), mapping.toGenericString());
            assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
        }

        verifyNoInteractions(dsl, jobs, credentials, credentialBundles);
    }

    private static Object[] nullArguments(Method method) {
        return Arrays.stream(method.getParameterTypes())
                .map(DeliveryControllerContainmentTest::defaultValue)
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
