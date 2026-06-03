# 渲染管线

> **模块：** `render-module`
> **最后更新：** 2026-05-18

## 概述

渲染管线是一个多阶段视频处理系统。它管理渲染作业从提交到 AI 脚本生成、提供者渲染、制品存储的完整生命周期。

## 作业生命周期状态机

```mermaid
stateDiagram-v2
    [*] --> QUEUED: 提交()
    QUEUED --> AI_PROCESSING: 编排()
    AI_PROCESSING --> RENDERING: 脚本已生成
    AI_PROCESSING --> FAILED: 脚本错误
    RENDERING --> COMPLETED: 渲染成功
    RENDERING --> FAILED: 渲染错误
    QUEUED --> REJECTED: 配额超限
    QUEUED --> CANCELLED: 用户取消
    RENDERING --> CANCELLED: 用户取消
    COMPLETED --> [*]
    FAILED --> [*]
    REJECTED --> [*]
    CANCELLED --> [*]
    FAILED --> QUEUED: 重试()
```

## 管线阶段

```mermaid
graph TB
    subgraph Stage1["1. 提交"]
        SUB["POST /api/v1/render/jobs/submit"]
        QUOTA["配额检查"]
        SUB --> QUOTA
        QUOTA -->|"通过"| CREATE["创建作业 (QUEUED)"]
        QUOTA -->|"超限"| REJECT["REJECTED"]
    end

    subgraph Stage2["2. AI 处理"]
        AI["AiGatewayPort.chat(prompt)"]
        CREATE --> AI
        AI -->|"脚本"| SCRIPT["AI 脚本已生成"]
        AI -->|"错误"| FAIL1["FAILED"]
    end

    subgraph Stage3["3. 渲染"]
        ROUTE["提供者路由器"]
        SCRIPT --> ROUTE
        ROUTE -->|"profile 匹配"| PROVIDER["JavaCVRenderProvider"]
        PROVIDER -->|"RenderResult"| RESULT["渲染完成"]
        PROVIDER -->|"错误"| FAIL2["FAILED"]
    end

    subgraph Stage4["4. 制品"]
        STORE["StorageCatalogPort.store()"]
        RESULT --> STORE
        STORE -->|"storageUri"| CATALOG["ArtifactCatalogService.register()"]
        CATALOG -->|"事件"| OUTBOX["RenderJobCompletedEvent"]
        OUTBOX --> COMPLETED["COMPLETED"]
    end
```

## 渲染提供者

| 提供者 | 类型 | 能力 | 状态 |
|--------|------|------|------|
| JavaCV | 转码 | 裁剪、转码、字幕、水印 | ✅ 主要 |
| OFX | 特效 | 特效、转场、滤镜 | ✅ |
| GPAC | 封装 | DASH/HLS、CMAF、MP4 faststart | ✅ |
| MLT | 渲染 | XML 生成、melt 命令 | ✅ |
| GStreamer | 渲染 | 管线处理、字幕叠加 | ✅ |
| FFMPEG | 转码 | 通用转码 | ✅ |

## 支持的 Profile

| Profile | 分辨率 | 用途 |
|---------|--------|------|
| `default_1080p` | 1920x1080 | 标准高清 |
| `default_720p` | 1280x720 | Web 高清 |
| `social_1080p` | 1920x1080 | 社交媒体 |
| `social_720p` | 1280x720 | 社交媒体（轻量） |
| `mobile_480p` | 854x480 | 移动端 |
| `4k_2160p` | 3840x2160 | 4K |
| `free_720p_watermarked` | 1280x720 | 免费层 |
| `pro_1080p` | 1920x1080 | 专业层 |
| `team_4k` | 3840x2160 | 团队层 |

## GPU 预设

| 预设 | 编码器 | 层级访问 |
|------|--------|---------|
| GPU_H264 | NVENC H.264 | TEAM+ |
| GPU_H265 | NVENC HEVC | TEAM+ |
| GPU_VP9 | VAAPI VP9 | TEAM+ |

## 错误码

| 错误码 | HTTP | 描述 |
|--------|------|------|
| RENDER-500-001 | 500 | 通用渲染失败 |
| RENDER-409-001 | 409 | 配额超限 |
| RENDER-404-001 | 404 | 作业未找到 |

## 当前限制

| 限制 | 状态 | 说明 |
|------|------|------|
| 多轨道合成 | ❌ 不支持 | 仅处理第一个轨道 |
| 复杂转场 | ❌ 不支持 | 仅基础淡入淡出 |
| 完整字幕烧录 | ⚠️ 部分 | 框架已就绪 |
| GPU 加速 | ❌ 不支持 | 仅 CPU |
| 远程 Worker | ❌ 不支持 | 全部进程内渲染 |
| H.265 编码 | ❌ 不支持 | 尚未实现 |
| HDR 视频 | ❌ 不支持 | 尚未实现 |
