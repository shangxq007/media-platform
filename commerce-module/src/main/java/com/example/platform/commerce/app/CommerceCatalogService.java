package com.example.platform.commerce.app;

import com.example.platform.commerce.domain.CanonicalProduct;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommerceCatalogService {
    public List<CanonicalProduct> listProducts() {
        return List.of(
                new CanonicalProduct("pro_monthly", "subscription", "default_features", "pro_quota"),
                new CanonicalProduct("basic_monthly", "subscription", "basic_features", "basic_quota"),
                new CanonicalProduct("enterprise_monthly", "subscription", "enterprise_features", "enterprise_quota")
        );
    }
}
