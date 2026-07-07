package com.example.platform;

import com.example.platform.datasource.DataSourceConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.example.platform.app",
    "com.example.platform.security",
    "com.example.platform.production",
    "com.example.platform.render",
    "com.example.platform.shared",
    "com.example.platform.storage",
    "com.example.platform.audit",
    "com.example.platform.notification",
    "com.example.platform.workflow",
    "com.example.platform.identity",
    "com.example.platform.artifact",
    "com.example.platform.billing",
    "com.example.platform.entitlement",
    "com.example.platform.policy",
    "com.example.platform.ai",
    "com.example.platform.datasource",
    "com.example.platform.config",
    "com.example.platform.openapi",
    "com.example.platform.commerce",
    "com.example.platform.delivery",
    "com.example.platform.payment",
    "com.example.platform.extension",
    "com.example.platform.observability",
    "com.example.platform.outbox",
    "com.example.platform.scheduler",
    "com.example.platform.prompt",
    "com.example.platform.federation",
    "com.example.platform.secrets",
    "com.example.platform.quota",
    "com.example.platform.web"
})
@EnableScheduling
@Import({DslContextConfiguration.class, DataSourceConfiguration.class, PlatformBeanConfiguration.class, FlywayConfiguration.class})
public class PlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
