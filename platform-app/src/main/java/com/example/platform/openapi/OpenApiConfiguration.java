package com.example.platform.openapi;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Media Platform API",
                version = "v1",
                description = "Media Platform API - Render Pipeline, Prompt Engineering, Cost Control, Monitoring. "
                        + "Web APIs use JWT/Session auth; MCP APIs use API Key/OAuth2 auth.",
                contact = @Contact(name = "Platform Team", email="platform@example.com"),
                license = @io.swagger.v3.oas.annotations.info.License(name = "Apache 2.0", url = "https://www.apache.org/licenses/LICENSE-2.0")
        ),
        servers = {
                @Server(url = "https://api.media-platform.example.com", description = "Production"),
                @Server(url = "https://staging.media-platform.example.com", description = "Staging"),
                @Server(url = "http://localhost:8080", description = "Local Development")
        },
        security = {
                @SecurityRequirement(name = "bearerAuth"),
                @SecurityRequirement(name = "apiKeyAuth")
        }
)
@SecuritySchemes({
        @SecurityScheme(
                name = "bearerAuth",
                type = SecuritySchemeType.HTTP,
                scheme = "bearer",
                bearerFormat = "JWT",
                description = "JWT token for Web API authentication. Obtain from /auth/token endpoint."
        ),
        @SecurityScheme(
                name = "apiKeyAuth",
                type = SecuritySchemeType.APIKEY,
                in = io.swagger.v3.oas.annotations.enums.SecuritySchemeIn.HEADER,
                paramName = "X-API-Key",
                description = "API key for MCP/OpenAPI authentication. Pass in X-API-Key header."
        )
})
public class OpenApiConfiguration {

    @Value("${app.api-versioning.public-default-major:v1}")
    private String defaultApiVersion;

    @Bean
    GroupedOpenApi webApi() {
        return GroupedOpenApi.builder()
                .group("web-api")
                .displayName("Web API (JWT/Session)")
                .pathsToMatch("/api/v1/render/jobs/**", "/api/v1/prompts/**", "/api/v1/tenants/**", "/api/v1/artifacts/**")
                .build();
    }

    @Bean
    GroupedOpenApi mcpApi() {
        return GroupedOpenApi.builder()
                .group("mcp-api")
                .displayName("MCP / OpenAPI API (API Key/OAuth2)")
                .pathsToMatch("/api/v1/mcp/**")
                .build();
    }

    @Bean
    GroupedOpenApi publicApiV1() {
        return GroupedOpenApi.builder()
                .group("public-v1")
                .displayName("Public API v1")
                .pathsToMatch("/api/v1/extensions/**", "/api/v1/audit/**", "/api/v1/internal/**")
                .build();
    }

    @Bean
    GroupedOpenApi renderApi() {
        return GroupedOpenApi.builder()
                .group("render")
                .displayName("Render Pipeline")
                .pathsToMatch("/api/v1/tenants/**/render-jobs/**", "/api/v1/artifacts/**")
                .build();
    }

    @Bean
    GroupedOpenApi promptApi() {
        return GroupedOpenApi.builder()
                .group("prompt")
                .displayName("Prompt Engineering")
                .pathsToMatch("/api/v1/prompts/**")
                .build();
    }

    @Bean
    GroupedOpenApi costApi() {
        return GroupedOpenApi.builder()
                .group("cost")
                .displayName("Cost & Entitlement")
                .pathsToMatch("/api/v1/billing/**", "/api/v1/entitlements/**")
                .build();
    }

    @Bean
    GroupedOpenApi monitoringApi() {
        return GroupedOpenApi.builder()
                .group("monitoring")
                .displayName("Monitoring & Feedback")
                .pathsToMatch("/api/v1/audit/**", "/api/v1/feedback/**")
                .build();
    }

    @Bean
    GroupedOpenApi workerApi() {
        return GroupedOpenApi.builder()
                .group("worker")
                .displayName("Remote Worker")
                .pathsToMatch("/api/v1/remote-worker/**")
                .build();
    }

    @Bean
    GroupedOpenApi internalApi() {
        return GroupedOpenApi.builder()
                .group("internal")
                .displayName("Internal")
                .pathsToMatch("/api/v1/internal/**")
                .build();
    }

    @Bean
    GroupedOpenApi actuatorApi() {
        return GroupedOpenApi.builder()
                .group("actuator")
                .displayName("Actuator")
                .pathsToMatch("/actuator/**")
                .build();
    }

    @Bean
    OpenAPI platformOpenApi() {
        return new OpenAPI()
                .info(new io.swagger.v3.oas.models.info.Info()
                        .title("Media Platform API")
                        .version(defaultApiVersion)
                        .description("Media Platform - Render Pipeline, Prompt Engineering, Cost Control, Monitoring & Feedback. "
                                + "Web APIs (JWT) at /api/v1/render/jobs, /api/v1/prompts; MCP APIs (API Key) at /api/v1/mcp/*")
                        .contact(new io.swagger.v3.oas.models.info.Contact()
                                .name("Platform Team")
                                .email("platform@example.com"))
                        .license(new io.swagger.v3.oas.models.info.License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
