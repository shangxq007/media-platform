package com.example.platform.outbox.api.event;
import java.util.List;
/** Domain-owned registrations; Outbox does not enumerate business event meanings. */
public interface OutboxEventCatalog { List<OutboxEventType<?>> types(); }
