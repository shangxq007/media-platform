# 架构决策记录（ADR）

> **模块：** 全部
> **最后更新：** 2026-05-18

## ADR-001：使用 Spring Modulith 的模块化单体

**状态：** 已接受
**日期：** 2026-05-08

**背景：** 需要在保持单一可部署单元的同时，组织 30+ 业务模块并明确边界。

**决策：** 使用 Spring Modulith 2.0.4 配合 `@ApplicationModule` 注解。`shared-kernel` 为 `Type.OPEN`；其他所有模块为 `CLOSED`。

**影响：**
- ✅ 通过 `ModularityTest` 强制执行模块边界
- ✅ 后续易于拆分为微服务
- ✅ 单一部署产物
- ⚠️ 所有模块共享同一 JVM（无进程隔离）

---

## ADR-002：Temporal + LiteFlow 编排

**状态：** 已接受
**日期：** 2026-05-08

**背景：** 同时需要持久化工作流编排和轻量级本地路由。

**决策：** Temporal 用于长时间运行、需持久化的工作流（渲染作业、计费周期）。LiteFlow 用于本地、无状态的规则链（提供者选择、路由）。

**影响：**
- ✅ Temporal 提供持久性、重试、可见性
- ✅ LiteFlow 轻量，适合简单路由
- ⚠️ 生产环境需要 Temporal Server
- ⚠️ 需要维护两套编排系统

---

## ADR-003：事件驱动的跨模块通信

**状态：** 已接受
**日期：** 2026-05-08

**背景：** 模块需要通信但不能产生紧耦合。

**决策：** 使用 Spring `ApplicationEventPublisher` 进行进程内事件。`outbox-event-module` 实现事务性 Outbox 模式。跨模块事件定义在 `shared-kernel` 中。

**影响：**
- ✅ 模块间松耦合
- ✅ 通过 Outbox 保证事务一致性
- ✅ 审计和通知为事件驱动
- ⚠️ 跨模块事件顺序不保证

---

## ADR-004：OpenFeature 用于 Feature Flag

**状态：** 已接受
**日期：** 2026-05-14

**背景：** 需要一个支持目标规则、百分比灰度和远程提供者的 Feature Flag 系统。

**决策：** 使用 OpenFeature Java SDK，默认 `LocalFeatureFlagProvider`。`OpenFeatureFlagEvaluator` 预留给远程提供者（LaunchDarkly、flagd、Unleash）。

**影响：**
- ✅ 通过 OpenFeature 的标准 API
- ✅ 本地提供者支持目标 + 灰度
- ✅ 易于切换为远程提供者
- ⚠️ 本地提供者仅为内存实现（不持久化）
- 🔴 远程未配置（生产阻塞项）

---

## ADR-005：JavaCV 作为主要渲染提供者

**状态：** 已接受
**日期：** 2026-05-12

**背景：** 需要一个不需要调用 FFmpeg CLI 的 Java 原生视频处理方案。

**决策：** JavaCV（FFmpeg 的 Java 绑定）作为主要渲染提供者。业务代码中不使用 `Runtime.exec()` 或 `ProcessBuilder`。

**影响：**
- ✅ 无 shell 命令注入风险
- ✅ 基于 JNI，性能优于 CLI
- ✅ 支持裁剪、转码、字幕、水印
- ⚠️ `extension-module` 中仍保留 Apache Commons Exec 用于非视频工具
- ⚠️ GPU 加速尚未实现

---

## ADR-006：Spring AI 用于 AI 模型抽象

**状态：** 已接受
**日期：** 2026-05-08

**背景：** 需要一个统一的 AI 客户端抽象，支持多模型提供者。

**决策：** 使用 Spring AI BOM 2.0.0-M3 配合 `spring-ai-starter-model-openai`。`ai-module` 通过 `ChatProvider` SPI 实现模型路由。

**影响：**
- ✅ 多 AI 提供者的统一 API
- ✅ 易于切换模型
- ⚠️ Spring AI 2.0.0-M3 是 Milestone 版本（非 GA）
- 🔴 当前使用 `StubChatProvider`（生产阻塞项）

---

## ADR-007：PF4J 用于插件系统

**状态：** 已接受
**日期：** 2026-05-12

**背景：** 需要一个 JVM 插件系统来支持动态扩展加载。

**决策：** 使用 PF4J 3.15.0 进行插件管理。`extension-module` 处理插件生命周期。`sandbox-runtime-module` 用于脚本执行。

**影响：**
- ✅ 插件的类加载器隔离
- ✅ 运行时插件加载/卸载
- ✅ 版本管理
- ⚠️ 插件治理仍在成熟中

---

## ADR-008：jOOQ 用于类型安全 SQL

**状态：** 已接受
**日期：** 2026-05-08

**背景：** 需要类型安全的 SQL 和复杂查询支持。

**决策：** 使用 jOOQ 3.19.18 配合 Gradle 代码生成。Flyway 用于 Schema 迁移。

**影响：**
- ✅ 类型安全 SQL
- ✅ 复杂查询支持
- ⚠️ 需要构建时代码生成
- ⚠️ 开发者学习曲线

---

## ADR-009：Vue 3 + Vite 用于前端

**状态：** 已接受
**日期：** 2026-05-08

**背景：** 需要一个现代前端框架来构建视频编辑器。

**决策：** Vue 3 配合 Vite 构建工具。Pinia 用于状态管理。Apollo Client 用于 GraphQL。

**影响：**
- ✅ Vite 快速构建
- ✅ Vue 3 响应式 UI
- ✅ 组件化架构
- ✅ 639 个 Vitest 测试

---

## ADR-010：Sentry + OpenReplay 用于监控

**状态：** 已接受
**日期：** 2026-05-14

**背景：** 需要错误监控和用户会话回放。

**决策：** Sentry 用于错误追踪和会话回放。OpenReplay 用于用户反馈和会话录制。

**影响：**
- ✅ 全面的错误追踪
- ✅ 会话回放便于调试
- ✅ 用户反馈收集
- ✅ 自动数据脱敏
- ⚠️ 需要外部服务配置
