package com.example.platform.billing.usage;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.shared.usage.ObservedRuntimeUsage;
import com.example.platform.usage.app.ObservedRuntimeUsageEmissionService;
import com.example.platform.usage.app.ObservedRuntimeUsageOutboxPublisher;
import org.junit.jupiter.api.Test;

class UsageRecordEmissionServiceTest {

    @Test
    void neutralEmissionServiceDelegatesToSingleDurablePublisher() {
        ObservedRuntimeUsageOutboxPublisher publisher = mock(ObservedRuntimeUsageOutboxPublisher.class);
        ObservedRuntimeUsage observation = mock(ObservedRuntimeUsage.class);
        when(publisher.appendWithOutbox(observation)).thenReturn(observation);
        ObservedRuntimeUsageEmissionService service =
                new ObservedRuntimeUsageEmissionService(publisher);

        assertSame(observation, service.emit(observation));
        verify(publisher).appendWithOutbox(observation);
    }
}
