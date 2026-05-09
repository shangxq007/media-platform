package com.example.platform.entitlement.infrastructure;

import com.example.platform.entitlement.domain.EntitlementSnapshot;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryEntitlementCache {
    private final Map<String, EntitlementSnapshot> cache = new ConcurrentHashMap<>();

    public EntitlementSnapshot get(String subjectId) {
        return cache.get(subjectId);
    }

    public void put(EntitlementSnapshot snapshot) {
        cache.put(snapshot.subjectId(), snapshot);
    }
}
