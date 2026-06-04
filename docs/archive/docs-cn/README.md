# Media Platform — 中文文档中心

> **版本：** 0.2.0-SNAPSHOT
> **最后更新：** 2026-05-18
> **文档重建：** Prompt 67

---

## 📖 阅读路径

| 角色 | 起始位置 |
|------|---------|
| 新开发者 | `00-overview/` → `01-architecture/` → `11-development/` |
| 后端工程师 | `01-architecture/` → `02-modules/` → `03-media-rendering/` |
| 前端工程师 | `04-frontend/` → `06-api/` |
| DevOps / SRE | `10-deployment-ops/` → `09-observability-quality/` |
| 技术负责人 / 审查者 | `12-review/` → `00-overview/02-project-status.md` |
| 产品 / QA | `00-overview/` → `04-frontend/` → `12-review/03-review-checklists.md` |

---

## 📂 目录结构

```
docs/cn/
├── README.md                          # ← 你在这里
├── index.md                           # 完整文档索引
├── 00-overview/                       # 项目总览与状态（2 篇）
├── 01-architecture/                   # 架构文档（8 篇，含 Mermaid 图）
├── 02-modules/                        # 模块参考（4 篇）
├── 03-media-rendering/                # 渲染管线与媒体（5 篇）
├── 04-frontend/                       # 前端文档（8 篇）
├── 05-access-entitlement-billing/     # 权限、权益、计费（7 篇）
├── 06-api/                            # API 策略（3 篇）
├── 07-prompt-ai-nlq/                  # Prompt / AI / NLQ（3 篇）
├── 08-extension-platform/             # 动态扩展平台（2 篇）
├── 09-observability-quality/          # 监控与质量（5 篇）
├── 10-deployment-ops/                 # 部署与运维（4 篇）
├── 11-development/                    # 开发规范（4 篇）
└── 12-review/                         # 审查与阻塞项（4 篇）
```

---

## 🔑 状态标记说明

| 标记 | 含义 |
|------|------|
| ✅ 已实现 | 完整实现并通过测试 |
| ⚠️ 部分实现 | 核心功能已实现，部分仍为存根 |
| 🔧 存根 / Mock | 基础设施就绪，真实实现待完成 |
| 📋 未来规划 | 已规划但尚未实现 |
| 🔴 生产阻塞项 | 生产环境前必须修复 |
| 🧪 需要人工复核 | 需要人工验证 |

---

## 📊 项目概览

| 指标 | 数值 |
|------|------|
| Gradle 模块总数 | 30 |
| Java 源文件数 | ~350+ |
| 后端测试文件数 | 54+ |
| 后端测试用例数 | ~340+ |
| 前端测试文件数 | 78+ |
| 前端测试用例数 | 639+ |
| 错误码数量 | 60+ |
| Flyway 迁移脚本数 | 17 |
| 数据库表数 | 28+ |
| 已完成 Prompt 数 | 67 |

---

## 🔴 活跃的生产阻塞项

1. **无认证机制** — 未配置 Spring Security 过滤器链
2. **无租户隔离** — TenantContext 未在数据层强制执行
3. **支付存根** — 所有支付提供商均为 Noop
4. **AI 存根** — StubChatProvider，无真实模型集成
5. **OpenFeature 远程提供者** — LocalFeatureFlagProvider 仅为内存实现

详见 `12-review/01-production-blockers.md`。

---

## 📝 许可

内部项目。保留所有权利。

---

*对应英文文档：`docs/README.md`*
