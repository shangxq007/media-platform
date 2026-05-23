# Media Platform

AI 视频生产与渲染编排平台（Spring Modulith 模块化单体 + Vue 3 前端）。

## 快速开始

```bash
docker compose up -d db
./gradlew :platform-app:bootRun   # 默认 spring.profiles.active=dev（本地免 JWT）
cd frontend && npm install && npm run dev
```

生产环境请使用 `prod` profile，并配置 `APP_JWT_SECRET` 与 PostgreSQL。

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
| V5 | 索引与约束 |

绿色field 项目已合并原 V1–V17 迁移，无需 baseline 历史版本。

## 插件扩展

见 [docs/extension-plugins.md](docs/extension-plugins.md)。

## 文档

- 中文平台指南（分卷）：`../docs/zh/platform-guide/README.md`
- 工作区文档中心：`../docs/README.md`
- 架构评估：`../docs/review/05-architecture-evaluation.md`

## 测试

```bash
./gradlew test
cd frontend && npx vitest run
```
