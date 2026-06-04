# 媒体处理模块

> **最后更新：** 2026-05-18

## render-module

**状态：** ✅ 已实现

核心渲染编排模块。管理渲染作业、提供者路由和配额集成。

| 功能 | 状态 | 说明 |
|------|------|------|
| 渲染作业生命周期 | ✅ | QUEUED → AI_PROCESSING → RENDERING → COMPLETED/FAILED |
| JavaCV 提供者 | ✅ | 主要提供者（基于 JNI） |
| OFX 提供者 | ✅ | 特效、转场、滤镜 |
| GPAC 提供者 | ✅ | DASH/HLS 封装 |
| MLT 提供者 | ✅ | XML 生成、melt 命令 |
| GStreamer 提供者 | ✅ | 管线处理 |
| FFMPEG 提供者 | ✅ | 通用转码 |
| GPU 预设 | ✅ | GPU_H264、GPU_H265、GPU_VP9（TEAM+ 层级） |
| 提供者路由器 | ✅ | 基于 Profile 的选择 |
| 配额集成 | ✅ | 渲染前配额检查 |
| OTIO 时间线 | ✅ | 片段/轨道解析 |
| 字幕烧录 | ✅ | 框架已就绪 |
| 制品存储 | ✅ | 通过 StorageCatalogPort |
| 状态历史 | ✅ | V10 迁移 |

**依赖：** `shared-kernel`、`ai-module`（API + domain）、`storage-module`（API + domain）

**REST API：** `/api/v1/render/*`

## workflow-module

**状态：** ✅ 已实现

Temporal + LiteFlow 工作流编排。

| 功能 | 状态 | 说明 |
|------|------|------|
| Temporal 工作流定义 | ✅ | RenderWorkflowImpl |
| Temporal Activity 实现 | ✅ | RenderActivitiesImpl |
| LiteFlow 规则链 | ✅ | 提供者选择、路由 |
| Feature Flag 集成 | ✅ | 通过 FeatureFlagEvaluator SPI |

**依赖：** `shared-kernel`、`policy-governance-module`（feature-flags）

## ai-module

**状态：** ⚠️ 部分实现

AI 模型集成，提供 ChatProvider SPI。

| 功能 | 状态 | 说明 |
|------|------|------|
| ChatProvider SPI | ✅ | 模型提供者接口 |
| ModelRouter | ✅ | SimpleModelRouter 实现 |
| AiGatewayPort | ✅ | 供 render-module 使用的命名接口 |
| StubChatProvider | 🔧 存根 | 返回硬编码响应 |
| 真实模型集成 | 📋 未来 | GLM-4/Claude/GPT 待完成 |

**依赖：** `shared-kernel`

## remote-render-worker

**状态：** ✅ 已实现

远程渲染 Worker，支持分布式渲染。

| 功能 | 状态 | 说明 |
|------|------|------|
| Worker 注册表 | ✅ | 内存中 Worker 追踪 |
| 作业分发 | ✅ | 轮询分配 |
| 健康监控 | ✅ | 基于心跳 |
| GPU 支持 | 📋 未来 | 架构就绪 |

**依赖：** `shared-kernel`

## artifact-catalog-module

**状态：** ✅ 已实现

制品追踪与元数据管理。

| 功能 | 状态 | 说明 |
|------|------|------|
| 制品注册 | ✅ | 存储元数据、存储 URI |
| 制品查询 | ✅ | 按项目、按作业 |
| 存储集成 | ✅ | 通过 storage-module |

**依赖：** 无

## storage-module

**状态：** ✅ 已实现

多提供者 Blob 存储目录。

| 功能 | 状态 | 说明 |
|------|------|------|
| StorageCatalogPort | ✅ | 供 render-module 使用的命名接口 |
| 本地文件系统存储 | ✅ | 默认实现 |
| 多提供者支持 | ✅ | 可通过 SPI 扩展 |
| BlobStorage 领域类型 | ✅ | PutObjectCommand、StorageObjectRef |

**依赖：** `shared-kernel`
