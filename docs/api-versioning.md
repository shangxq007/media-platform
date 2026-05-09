# 对外 HTTP API 版本管理

本文说明：**比「仅路径里写死 v1」更灵活**的常见做法，以及如何与 **springdoc-openapi**、网关、灰度发布配合。当前仓库在 `application.yml` 中有 `app.api-versioning` 占位，**尚未**实现统一拦截或路由；落地时可按下文选一主方案并写进代码。

---

## 1. 本仓库现状

- URL 习惯：`/api/v1/...`（各模块 Controller 自行声明）。
- 配置占位：`app.api-versioning.public-default-major`、`also-accept-header`（意图支持 **Header 协商**，但未绑定实现）。

---

## 2. 版本维度：你在「版本」什么？

| 对象 | 典型手段 |
|------|-----------|
| **REST 资源与 JSON 形状** | 路径版本、Header/Accept、或并行 DTO + 映射层 |
| **OpenAPI 文档** | springdoc **分组**（按 version 或 tag 出多份 `/v3/api-docs/{group}`） |
| **行为 / 灰度** | 网关权重、**Feature Flag**（见 `layering-and-open-source.md` 特性开关一节）、独立部署实例 |

「灵活」往往来自 **把「文档版本」「路由版本」「数据迁移节奏」拆开**，而不是单一魔法数字。

---

## 3. 可选方案（由简到繁）

### 3.1 路径版本（当前主风格）

- 形式：`/api/v1/users`、`/api/v2/users`。
- **优点**：简单、可缓存、日志与网关规则直观。
- **缺点**：大版本爆炸时 URL 多；**细粒度字段级**演进成本高。

**改进**：按 **限界上下文** 分前缀，而非全局一个大 v1，例如 `/api/render/v1/...`、`/api/billing/v1/...`，各上下文可 **独立升主版本**。

### 3.2 Header / Accept 协商（更灵活、客户端要配合）

- 形式：`Accept: application/vnd.example.v2+json` 或 `X-API-Version: 2024-10-01`（类 Stripe `Stripe-Version`）。
- **优点**：URL 稳定；同一端点可服务多契约（需明确默认版本与错误码）。
- **缺点**：CDN/缓存要带着 Vary；客户端与调试工具配置成本更高。

与 `app.api-versioning.also-accept-header=true` 的意图一致：可用 **HandlerInterceptor** 或 **自定义 MediaType** 解析版本，再路由到不同 Controller 方法或不同 `@RequestMapping` 映射。

### 3.3 查询参数（一般作补充）

- 形式：`?api-version=2`。
- **优点**：浏览器试调方便。
- **缺点**：易与业务查询参数混淆；缓存与日志规范要统一。**不推荐**作为唯一主方案。

### 3.4 并行包 / 适配器（实现层灵活）

- 对外仍是一个版本标识，对内 `v1`/`v2` **DTO + Assembler** 并存，或 **防腐层** 映射到统一领域模型。
- **优点**：演进快，URL 不必每个字段变更都升主版本。
- **缺点**：代码分支与测试矩阵增加。

### 3.5 网关与多实例（部署维度）

- 不同版本路由到 **不同 Service / Deployment**（或灰度副本），应用内仍可用 **同一代码 + Feature Flag** 控制行为。
- **优点**：强隔离、回滚快。
- **缺点**：运维与成本上升。

---

## 4. 推荐组合（实践上较「灵活」且可维护）

1. **默认**：继续 **路径主版本** `/api/v1`，按 **业务域分子路径**（或子网关路由），避免全站一把梭式 v2。
2. **需要兼容老客户端时**：增加 **Accept 或专用 Header** 指定次要契约（与 `app.api-versioning` 对齐），**默认**未带 Header 时走 `public-default-major`。
3. **文档**：springdoc **多 Group**（例如 `public-v1`、`public-v2`），CI 上做 **破坏性 diff**（如 OpenAPI Diff）。
4. **下线**：响应头 **`Sunset`** + **`Deprecation`**（RFC 8594）、文档与邮件公告；与 **Entitlement / 租户** 维度可结合「仅对部分租户开 v2」。

---

## 5. 与 Spring / springdoc 的落地提示

- **路由**：`@RequestMapping("/api/v1/...")` 与 `/api/v2/...` 可并存；或单一入口 + `VersionedRequestMappingHandlerMapping` 类方案（自研或使用社区 starter 前评估维护成本）。
- **OpenAPI**：`GroupedOpenApi.builder().group("v1").pathsToMatch("/api/v1/**").build()` 等为 v2 再建一组。
- **测试**：契约测试（如 Spring Cloud Contract）或 **Schemathesis** 对多版本各跑一遍。

---

## 6. 小结

- **更灵活**通常指：**路径主版本 +（可选）Header/Accept 细版本 + 域级前缀 + 网关/Flag 灰度 + 多份 OpenAPI** 的组合，而不是单一机制。
- 当前仓库以 **路径 v1** 为主；若你希望 **真正实现** `app.api-versioning` 与多 Group OpenAPI，可在后续迭代中单开任务实现拦截器与 springdoc 分组。
