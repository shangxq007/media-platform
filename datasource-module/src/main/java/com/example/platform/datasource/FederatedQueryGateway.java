package com.example.platform.datasource;

import java.util.List;
import java.util.Map;

public interface FederatedQueryGateway {
    List<Map<String, Object>> query(String sql);
}
