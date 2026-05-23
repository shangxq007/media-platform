package com.example.platform.render.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class EffectPackCatalogSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EffectPackCatalogSeeder.class);

    private final EffectPackCatalogService catalogService;

    public EffectPackCatalogSeeder(EffectPackCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Override
    public void run(String... args) {
        try {
            catalogService.seedBuiltinPackIfAbsent();
        } catch (Exception e) {
            log.warn("Effect pack catalog seed skipped (tables may not exist yet): {}", e.getMessage());
        }
    }
}
