package com.example.platform.render.app.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.platform.render.infrastructure.RenderCacheProperties;
import com.example.platform.render.api.event.RenderCacheHashInvalidatedEvent;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.example.platform.render.app.event.RenderLifecyclePublisher;

class RenderCacheHashInvalidationNotifierTest {

    @Test
    void publishesEventWhenTasksInvalidated() {
        RenderLifecyclePublisher publisher = mock(RenderLifecyclePublisher.class);
        RenderCacheProperties props = new RenderCacheProperties();
        RenderCacheHashInvalidationNotifier notifier =
                new RenderCacheHashInvalidationNotifier(publisher, props);

        notifier.notifyIfNeeded("ten", "proj", "rj_new", "rj_base", Set.of("seg_0", "final_compose"));

        ArgumentCaptor<RenderCacheHashInvalidatedEvent> captor =
                ArgumentCaptor.forClass(RenderCacheHashInvalidatedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertEquals(2, captor.getValue().invalidatedCount());
        assertEquals("rj_base", captor.getValue().baseJobId());
    }

    @Test
    void skipsWhenEmpty() {
        RenderLifecyclePublisher publisher = mock(RenderLifecyclePublisher.class);
        RenderCacheHashInvalidationNotifier notifier =
                new RenderCacheHashInvalidationNotifier(publisher, new RenderCacheProperties());
        notifier.notifyIfNeeded("ten", "proj", "rj", null, Set.of());
        verify(publisher, never()).publishEvent(any(com.example.platform.render.api.event.RenderCacheHashInvalidatedEvent.class));
    }

    @Test
    void parsesMetadataTaskIds() {
        Map<String, String> meta = Map.of("hashInvalidatedTaskIds", "seg_0,seg_1");
        assertEquals(Set.of("seg_0", "seg_1"),
                RenderCacheHashInvalidationNotifier.parseInvalidatedTaskIds(meta));
    }
}
