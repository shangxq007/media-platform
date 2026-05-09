package com.example.platform.commerce.infrastructure;

import org.springframework.stereotype.Component;

@Component
public class NoopMedusaCatalogAdapter {
    public String provider() {
        return "medusa-disabled";
    }
}
