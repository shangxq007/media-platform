package com.example.platform.datasource;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class NoopFederatedQueryGateway implements FederatedQueryGateway {
    @Override
    public List<Map<String, Object>> query(String sql) {
        throw new UnsupportedOperationException("Federated queries are disabled. Add Calcite/Trino integration only when truly needed.");
    }
}
