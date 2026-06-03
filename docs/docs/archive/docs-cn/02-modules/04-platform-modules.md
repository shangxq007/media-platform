# 平台服务模块

> **最后更新：** 2026-05-18

## prompt-module

**状态：** ✅ 已实现

Prompt 模板管理与安全治理。

| 功能 | 状态 | 说明 |
|------|------|------|
| 模板 CRUD | ✅ | 创建、读取、更新、删除 |
| 版本控制 | ✅ | 模板版本历史 |
| 变量替换 | ✅ | `{{variable}}` 语法 |
| 模板渲染 | ✅ | 带上下文渲染 |
| 安全治理 | ✅ | 内容安全检查 |
| 内存存储 | 🔧 | ConcurrentHashMap（不持久化） |

**依赖：** `shared-kernel`

**REST API：** `/api/v1/prompts/*`

## extension-module

**状态：** ✅ 已实现

动态扩展平台，基于 PF4J 插件系统。

| 功能 | 状态 | 说明 |
|------|------|------|
| 插件生命周期 | ✅ | 加载、启动、停止、卸载 |
| 工具注册表 | ✅ | 可执行文件白名单 |
| CLI 工具执行 | ✅ | 配置驱动 |
| 信任级别 | ✅ | FULLY_TRUSTED、SEMI_TRUSTED、UNTRUSTED |
| 金丝雀路由 | ✅ | 基于百分比的流量分割 |
| 资源限制 | ✅ | 并发、内存、CPU、I/O |
| 回滚 | ✅ | 版本回滚、路由回滚 |
| 审计 | ✅ | 15+ 事件类型 |
| Apache Commons Exec | ⚠️ | 仍存在于 CLI 工具 |

**依赖：** `shared-kernel`

**REST API：** `/api/v1/extensions/*`

## sandbox-runtime-module

**状态：** ✅ 已实现（占位）

不可信脚本的沙箱执行环境。

| 功能 | 状态 | 说明 |
|------|------|------|
| Groovy 支持 | ✅ | 若 Groovy 在 classpath |
| JavaScript 支持 | ✅ | 若 Nashorn/GraalJS 在 classpath |
| Wasm 支持 | 📋 未来 | Wasmtime/Wasmer 计划 |
| 安全策略 | ✅ | 阻止 Runtime、File、Socket 等 |
| 默认禁用 | ✅ | 必须显式启用 |

**依赖：** 无

**REST API：** `/api/v1/sandbox/*`

## federation-query-module

**状态：** ✅ 已实现

GraphQL 查询聚合层和 NLQ 助手。

| 功能 | 状态 | 说明 |
|------|------|------|
| GraphQL Schema | ✅ | 12+ 查询类型 |
| DataLoader 批量加载 | ✅ | N+1 查询预防 |
| REST 回退 | ✅ | REST 控制器作为回退 |
| 查询限制 | ✅ | 深度、复杂度、页面大小 |
| 审计拦截 | ✅ | 所有查询审计 |
| 数据脱敏 | ✅ | PII 字段脱敏 |
| NLQ 助手 | ✅ | 自然语言 → SQL |
| SQL 安全验证 | ✅ | 10 条安全规则 |
| 范围隔离 | ✅ | 租户/工作空间/用户范围 |
| 图表建议 | ✅ | 自动图表类型推荐 |

**依赖：** `shared-kernel`

**REST API：** `/api/v1/analytics/nlq/*`、`/api/v1/analytics/reports/*`
**GraphQL：** `/graphql`

## cloud-resource-module

**状态：** ✅ 已实现

云资源提供者目录。

| 功能 | 状态 | 说明 |
|------|------|------|
| 资源定义 | ✅ | CloudResourceDefinition CRUD |
| 提供者目录 | ✅ | 多云资源类型 |

**依赖：** `shared-kernel`
