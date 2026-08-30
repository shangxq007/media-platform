package com.example.platform.datasource;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Truthful unavailable boundary until a federated query provider is composed. */
@Component
public final class UnavailableFederatedQueryGateway implements FederatedQueryGateway {
    @Override
    public List<Map<String, Object>> query(String sql) {
        throw new UnsupportedOperationException(
                "Federated queries are unavailable until a real provider is configured");
    }
}
