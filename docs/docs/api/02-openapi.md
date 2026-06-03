# OpenAPI & MCP Integration

> **Module:** `platform-app`
> **Last Updated:** 2026-05-18

## OpenAPI Documentation

The platform uses **springdoc OpenAPI 3** for API documentation.

| Endpoint | Purpose |
|----------|---------|
| `/swagger-ui.html` | Swagger UI |
| `/v3/api-docs` | OpenAPI JSON spec |
| `/v3/api-docs.yaml` | OpenAPI YAML spec |

## MCP (Model Context Protocol) Integration

The OpenAPI spec can be used with MCP-compatible tools for AI-assisted development:

1. Export the OpenAPI spec from `/v3/api-docs`
2. Configure MCP client with the spec
3. AI tools can discover and call platform APIs

## API Groups

| Group | Tag | Modules |
|-------|-----|---------|
| Render | `render` | render-module |
| Projects | `projects` | identity-access-module |
| Entitlements | `entitlements` | entitlement-module |
| Feature Flags | `feature-flags` | policy-governance-module |
| Prompts | `prompts` | prompt-module |
| Extensions | `extensions` | extension-module |
| Analytics | `analytics` | federation-query-module |
| Notifications | `notifications` | notification-module |
| Billing | `billing` | billing-module |
| Admin | `admin` | multiple modules |
