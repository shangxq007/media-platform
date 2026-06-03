# OpenAPI 与 MCP 集成

> **模块：** `platform-app`
> **最后更新：** 2026-05-18

## OpenAPI 文档

平台使用 **springdoc OpenAPI 3** 进行 API 文档管理。

| 端点 | 用途 |
|------|------|
| `/swagger-ui.html` | Swagger UI |
| `/v3/api-docs` | OpenAPI JSON 规范 |
| `/v3/api-docs.yaml` | OpenAPI YAML 规范 |

## MCP（Model Context Protocol）集成

OpenAPI 规范可与 MCP 兼容工具一起使用，实现 AI 辅助开发：

1. 从 `/v3/api-docs` 导出 OpenAPI 规范
2. 使用规范配置 MCP 客户端
3. AI 工具可以发现并调用平台 API

## API 分组

| 分组 | 标签 | 模块 |
|------|------|------|
| 渲染 | `render` | render-module |
| 项目 | `projects` | identity-access-module |
| 权益 | `entitlements` | entitlement-module |
| Feature Flags | `feature-flags` | policy-governance-module |
| 提示词 | `prompts` | prompt-module |
| 扩展 | `extensions` | extension-module |
| 分析 | `analytics` | federation-query-module |
| 通知 | `notifications` | notification-module |
| 账单 | `billing` | billing-module |
| 管理 | `admin` | 多个模块 |
