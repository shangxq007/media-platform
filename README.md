# Media Platform

AI 视频生产与渲染编排平台（Spring Modulith 模块化单体 + Vue 3 前端）。

## 快速开始

```bash
docker compose up -d db
./gradlew :platform-app:bootRun   # 默认 spring.profiles.active=dev（本地免 JWT）
cd frontend && npm install && npm run dev
```

生产环境请使用 **`SPRING_PROFILES_ACTIVE=prod`**（勿带 `dev`），配置 PostgreSQL 与 OIDC。  
启动门禁见 [docs/production-safety.md](docs/production-safety.md)。

## 模块结构

31 个 Gradle 子模块，入口为 `platform-app`。详见工作区 `docs/modules/`。

## 数据库（Flyway）

Schema 统一在：

`platform-app/src/main/resources/db/migration/`

| 脚本 | 内容 |
|------|------|
| V1 | 核心基础设施、扩展定义、发件箱 |
| V2 | 商业、身份、渲染、产物 |
| V3 | Prompt、扩展平台、工作空间 RBAC |
| V4 | 权益、导航、计费、通知、社交、Feature Flag |
| V5–V19 | 索引、交付、Temporal、租户 AI 密钥等 |
| V20–V22 | 时间线领域版控（`timeline_revision`、`labels_json`） |

当前 **22** 个中央 Flyway 脚本；模块内 Bootstrap 仅作 JDBC→内存 **hydrate**，不替代 DDL。  
Modulith 违规预算见 [docs/modulith-debt-register.md](docs/modulith-debt-register.md)。

## 插件扩展

见 [docs/extension-plugins.md](docs/extension-plugins.md)。

## 文档

- 中文平台指南（分卷）：`../docs/zh/platform-guide/README.md`
- 工作区文档中心：`../docs/README.md`
- 架构评估：`../docs/review/05-architecture-evaluation.md`

## 测试

```bash
./gradlew test
./gradlew :platform-app:test --tests "com.example.platform.FlywaySchemaIntegrationTest"  # 需 Docker
cd frontend && npx vitest run
```
