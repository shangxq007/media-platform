# 代码风格与命名规范

> **最后更新：** 2026-05-18

## Java 命名规范

### 首字母缩写大小写

| 缩写 | 正确 | 不正确 |
|------|------|--------|
| FFmpeg | `FFmpegRenderProvider` | `FfmpegRenderProvider` |
| GPAC | `GPACRenderProvider` | `GpacRenderProvider` |
| MLT | `MLTCommandFactory` | `MeltCommandFactory` |
| OTIO | `OpenTimelineioAdapter` | — |
| API | `AiGatewayPort`（在 `ai.api` 包中）| — |

**规则：** 3 个字母以上的缩写全部大写。2 个字母的缩写遵循标准 camelCase。

### 构造型注解

| 注解 | 用途 |
|------|------|
| `@Service` | 业务逻辑类 |
| `@Component` | 技术基础设施（注册表、策略、调度器）|
| `@Controller` | REST API 控制器 |
| `@Repository` | 数据访问类 |

**规则：** 业务逻辑使用 `@Service`，技术基础设施使用 `@Component`。

### 包命名

| 包 | 内容 |
|----|------|
| `*.api` | 控制器、DTO |
| `*.app` | 应用服务 |
| `*.domain` | 领域模型、值对象 |
| `*.spi` | 端口接口 |
| `*.infrastructure` | 适配器、外部集成 |

### 类命名

| 类型 | 规范 | 示例 |
|------|------|------|
| Service | `<Domain>Service` | `RenderJobService` |
| Controller | `<Domain>Controller` | `RenderController` |
| Repository | `<Domain>Repository` | `RenderJobRepository` |
| Port | `<Domain>Port` | `AiGatewayPort` |
| Adapter | `<Domain>Adapter` | `JavaCVMediaProbeAdapter` |
| Provider | `<Name>Provider` | `JavaCVRenderProvider` |
| Factory | `<Domain>Factory` | `FFmpegCommandFactory` |
| Registry | `<Domain>Registry` | `RenderProviderRegistry` |
| Policy | `<Domain>Policy` | `RenderProviderSelectionPolicy` |
| Event | `<Domain>Event` | `RenderJobCompletedEvent` |
| Exception | `<Domain>Exception` | `RenderException` |
| Record（DTO） | `<Domain><Type>` | `SubmitRenderJobRequest` |

## 数据库命名规范

| 类别 | 规范 | 示例 |
|------|------|------|
| 表名 | 小写 + 下划线 | `render_job` |
| 列名 | 小写 + 下划线 | `created_at` |
| 主键 | `id varchar(64)` | — |
| 时间戳 | `created_at timestamp not null` | — |
| 状态列 | `status varchar(32) not null` | — |
| 外键 | `<entity>_id varchar(64) not null` | `tenant_id` |
| 索引 | `ix_<table>_<column>` | `ix_render_job_tenant_id` |

## 错误代码格式

```
{模块}-{HTTP 状态码}-{序号}
```

| 模块 | 前缀 |
|------|------|
| 通用 | `COMMON-` |
| 渲染 | `RENDER-` |
| 字幕 | `SUBTITLE-` |
| 特效 | `EFFECT-` |
| 时间轴 | `TIMELINE-` |
| 迁移 | `MIGRATION-` |
| 权益 | `ENTITLEMENT-` |
| Feature Flag | `FF-` |
| NLQ | `NLQ-` |
| 监控 | `MONITORING-` |
| 反馈 | `FEEDBACK-` |

## 测试规范

| 规范 | 规则 |
|------|------|
| 测试类名 | `<ClassName>Test` |
| 测试方法名 | `should<预期行为>When<条件>` |
| 测试包 | 与生产类相同 |
| 单元测试 | 纯 JUnit，无 Spring 上下文 |
| 集成测试 | `@SpringBootTest` |
| 测试数据库 | H2 内存数据库 |

## 前端命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 组件 | PascalCase | `TimelineEditor.vue` |
| 组合式函数 | `use<Name>.ts` | `usePlayback.ts` |
| Store | `<name>Store.ts` | `projectStore.ts` |
| 类型 | PascalCase 接口 | `interface Clip { ... }` |
| 路由 | kebab-case | `/me/capabilities` |
