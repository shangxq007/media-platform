# 渲染管道实现 (中文)

> **最后更新**: 2026-05-13
> **状态**: 实现分析完成
> **模块**: render-module

## 当前实现概览

渲染管道是一个基于JavaCV（FFmpeg的Java绑定）的完整的视频渲染和转码系统。它支持多轨道时间线处理、AI脚本生成、配额管理和产物存储。

## 架构组件

### 1. RenderJob生命周期

```
QUEUED → AI_PROCESSING → RENDERING → COMPLETED
         ↓               ↓          ↓
      REJECTED        FAILED   CANCELLED
```

**状态转换说明**:
- `QUEUED`: 任务创建，等待AI脚本生成
- `AI_PROCESSING`: AI网关生成视频脚本
- `RENDERING`: JavaCV处理视频
- `COMPLETED`: 产物存储和注册
- `FAILED`: 任何步骤遇到错误
- `REJECTED`: 配额超限或验证失败
- `CANCELLED`: 用户主动取消

### 2. 组件职责

#### RenderController (`/api/v1/render/*`)
- RESTful接口管理
- 任务创建、查询、取消、重试
- 支持租户隔离和传统接口

#### RenderJobService
- 核心任务编排逻辑
- 状态机验证
- 租户访问控制
- 状态历史追踪

#### RenderOrchestratorService
- 完整工作流编排
- 配额检查和消费
- AI脚本生成（通过AiGatewayPort）
- Provider路由和执行
- 存储集成

#### JavaCVRenderProvider
- **主要渲染实现**
- 使用JavaCV/FFmpeg处理视频
- 支持剪辑、转码、字幕、水印
- 处理OTIO时间线格式
- 为空时间线生成占位视频

## JavaCV实现详情

### 支持能力

**格式**: MP4, OGG, WebM, MOV

**编解码器**: H.264, AAC, MP3, VP9

**特效**:
- 转场: fade_in, fade_out, cross_dissolve, dissolve
- 视频: blur, sharpen, brightness, contrast, grayscale, sepia
- 文字: subtitle_burn_in
- 音频: volume
- 叠加: watermark

**配置文件**:
- `default_1080p`, `default_720p`
- `social_1080p`, `social_720p`
- `mobile_480p`, `4k_2160p`
- `free_720p_watermarked`, `pro_1080p`, `team_4k`

### 处理流程

```mermaid
graph TD
    A[SubmitRenderJobRequest] --> B[Quota Check]
    B --> C{Quota OK?}
    C -->|No| D[REJECTED]
    C -->|Yes| E[Create Job (QUEUED)]
    E --> F[AI Script Generation]
    F --> G[AI_PROCESSING]
    G --> H{Script Generated?}
    H -->|No| I[FAILED]
    H -->|Yes| J[Route Provider]
    J --> K[JavaCVRenderProvider.render()]
    K --> L[RENDERING]
    L --> M{Render Success?}
    M -->|No| N[FAILED]
    M -->|Yes| O[Store Artifact]
    O --> P[COMPLETED]
```

### JavaCVRenderProvider核心方法

1. **render(String jobId, String aiScript, String profile)**
   - 主入口点
   - 解析AI脚本或时间线
   - 调用相应的渲染方法
   - 返回包含产物元数据的RenderResult

2. **renderFromTimeline()**
   - 处理OTIO JSON时间线
   - 提取剪辑、开始时间、持续时间
   - 处理字幕轨道
   - 应用烧录字幕

3. **transcodeVideo()**
   - 基于FFmpeg的转码
   - 支持按时间范围剪辑
   - 保持音视频同步

4. **renderPlaceholderVideo()**
   - 生成带帧数的测试视频
   - 用于无源材料时

## 当前限制

### ❌ 不支持的功能
- **多轨道合成**: 仅处理第一个轨道
- **复杂转场**: 仅支持基础淡入淡出
- **完整字幕烧录**: 框架已存在，需要完整的FFmpeg filtergraph集成
- **GPU加速**: 仅CPU处理
- **远程Worker**: 所有渲染在进程内
- **OFX/GStreamer/MLT**: 已规划但未实现
- **H.265编码**: 仅支持H.264
- **HDR视频**: 尚未支持

### ⚠️ 部分支持的功能
- **字幕烧录**: 框架已存在，需要完整的FFmpeg filtergraph集成
- **4K渲染**: 支持但CPU上可能较慢

## 前端集成

### Export Panel → 后端API

**请求示例**:
```json
POST /api/v1/render/jobs/submit
{
  "tenantId": "tenant_123",
  "projectId": "proj_456", 
  "prompt": "创建一个30秒的视频...",
  "profile": "default_1080p"
}
```

**响应**:
```json
{
  "jobId": "rj_789",
  "status": "QUEUED"
}
```

**轮询状态**:
```http
GET /api/v1/render/jobs/{jobId}
```

**状态映射**:
- ExportPanel.Progress → RenderJobStatus
- ExportPanel.Error → RENDER_FAILED, AI_GENERATION_FAILED

## 错误处理

### 错误码
- `RENDER-500-001`: 通用渲染失败
- `RENDER-409-001`: 配额超限
- `RENDER-404-001`: 任务不存在

### 重试逻辑
- 失败任务可通过`/render/jobs/{jobId}/retry`重试
- 状态机验证重试资格
- 重试时重新检查配额

### 异常处理
- `IllegalArgumentException`: 400 Bad Request
- `IllegalStateException`: 409 Conflict
- `PlatformException`: 映射到配置的错误码
- 所有异常都记录jobId上下文

## 产物生成

1. **存储**: 本地文件系统 (`/tmp/platform/artifacts/{jobId}/`)
2. **注册**: 目录服务追踪产物
3. **元数据**: 格式、分辨率、时长存储
4. **清理**: 需要手动清理（尚未实现TTL）

## 测试

```bash
# 运行渲染模块测试
./gradlew :render-module:test

# 测试特定Provider
./gradlew :render-module:test --tests JavaCVRenderProviderTest
```

### 测试覆盖范围
- Profile支持验证（6个配置文件）
- 能力检查（h264, mp4, watermark, subtitle-burn, fade, clip, transcode）
- OTIO时间线解析
- 空时间线处理
- 错误场景

## 配置

```yaml
app:
  storage:
    local-root: /tmp/platform
  render:
    providers:
      javacv:
        enabled: true  # 当前始终启用
```

## 安全说明

- ✅ 业务代码中无Runtime.exec()或ProcessBuilder
- ✅ JavaCV直接使用JNI绑定
- ✅ 文件路径验证存储根目录
- ✅ 渲染完成后清理临时文件
- ✅ 无shell命令拼接

## 性能特征

- **CPU使用**: 1080p约100%单核，4K约200-300%
- **内存**: 每次渲染2-4GB堆内存+本地内存
- **速度**: 1080p实时，4K较慢
- **并发**: 受限于CPU核心数
- **磁盘**: 临时存储用于输入/输出

## 后续步骤（规划中）

1. 实现多轨道合成
2. 添加GPU Worker支持
3. 实现远程渲染Worker
4. 添加H.265编码支持
5. 完整的字幕烧录集成
6. MLT Provider用于高级编辑
7. OFX Provider用于专业特效

---

*本文档反映了截至2026-05-13的当前实现。有关未来功能和路线图，请参见[render-provider-extension-roadmap.md](render-provider-extension-roadmap.md)。*