# 未来方向：智能渲染与自然语言编辑

> **状态**：规划阶段（📋 Future Work）
> **生成日期**：2026-05-28
> **前置阅读**：[项目架构、运维、开发与智能渲染路线图](project-architecture-operations-guide.md)

---

## 1. 产品愿景

### 目标能力

用户上传素材 + 输入提示词，系统调用 LLM 和工具，生成多个预览，用户用自然语言持续修改，最终导出成片。

### 完整工作流

```
素材上传
  → 素材分析（ffprobe / 缩略图 / 场景检测 / ASR）
  → LLM 生成多个编辑方案（fast_cut / storytelling / product_focus）
  → 生成多个低清预览（360p/480p，异步渲染）
  → 用户自然语言反馈（"把第三剪缩短 3 秒"）
  → LLM 生成 timeline patch
  → render-worker 重新预览
  → 用户确认
  → 高质量导出（1080p/4K）
  → 交付分发
```

### 核心价值

1. **降低专业门槛** — 自然语言替代手动时间线操作
2. **多方案并行** — 一次生成多个风格变体，用户选择最优
3. **迭代式编辑** — 持续对话式修改，无需重新生成
4. **确定性执行** — LLM 只生成结构化指令，渲染 Worker 确定性执行

---

## 2. 推荐架构

### 架构原则

- **LLM 不直接执行渲染** — LLM 生成结构化 Edit Plan
- **Edit Plan 转 OTIO / Render Plan** — 中间层确保可验证性
- **Render Worker 确定性执行** — 相同输入始终产生相同输出
- **所有 patch 需 schema validation** — 防止 LLM 幻觉导致破坏性操作
- **所有素材引用需 assetId 校验** — 防止引用不存在的素材
- **所有操作需 operation allowlist** — 仅允许白名单内的操作类型

### 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        Editor Frontend                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐       │
│  │  Prompt  │  │ Multi-   │  │ NL       │  │ Timeline  │       │
│  │  Input   │  │ Preview  │  │ Feedback │  │ Patch UI  │       │
│  └────┬─────┘  └────▲─────┘  └────┬─────┘  └────▲─────┘       │
│       │              │              │              │             │
└───────┼──────────────┼──────────────┼──────────────┼─────────────┘
        │              │              │              │
        ▼              │              │              │
┌───────────────┐      │              │              │
│  Intent       │      │              │              │
│  Parser       │      │              │              │
│  (NL →        │      │              │              │
│   structured) │      │              │              │
└───────┬───────┘      │              │              │
        │              │              │              │
        ▼              │              │              │
┌───────────────┐      │              │              │
│  Edit Plan    │      │              │              │
│  Generator    │      │              │              │
└───────┬───────┘      │              │              │
        │              │              │              │
        ▼              │              │              │
┌───────────────┐      │              │              │
│  OTIO         │      │              │              │
│  Timeline     │      │              │              │
│  (exchange    │      │              │              │
│   layer)      │      │              │              │
└───────┬───────┘      │              │              │
        │              │              │              │
        ▼              │              │              │
┌───────────────┐      │              │              │
│  Render Plan  │      │              │              │
│  (execution   │      │              │              │
│   layer)      │      │              │              │
└───────┬───────┘      │              │              │
        │              │              │              │
        ▼              │              │              │
┌───────────────┐      │              │              │
│  Preview      │──────┘              │              │
│  Render       │                     │              │
│  (low-res)    │                     │              │
└───────────────┘                     │              │
                                      │              │
        ┌─────────────────────────────┘              │
        │                                            │
        ▼                                            │
┌───────────────┐                                    │
│  Patch Plan   │────────────────────────────────────┘
│  Generator    │
│  (LLM 生成    │
│   增量修改)   │
└───────────────┘
```

### 数据流

```
Prompt → Intent Parser → Edit Plan → OTIO Timeline → Render Plan → Preview Render
    ↑                                                                      ↓
    ←←←←←←←←←← User Feedback → LLM Patch → Timeline Patch ←←←←←←←←←←←←←←←←←
```

---

## 3. OTIO 技术选型

### OpenTimelineIO 适合描述

| 概念 | OTIO 支持 | 说明 |
|-------|-----------|------|
| Clip trim | ✅ | 剪辑入出点调整 |
| Track order | ✅ | 轨道顺序 |
| Gap | ✅ | 间隙 |
| Transition dissolve | ✅ | 溶解转场 |
| Marker | ✅ | 标记点 |
| Metadata | ✅ | 元数据 |
| Subtitle burn | ⚠️ | 通过 metadata/effect 表达 |
| Overlay | ⚠️ | 通过 track/metadata 表达 |

### OTIO 不适合直接表达

| 概念 | OTIO 支持 | 建议 |
|-------|-----------|------|
| 复杂 VFX | ❌ | 使用 render-plan 扩展 |
| 调色参数 | ⚠️ 有限 | 使用 render-plan 扩展 |
| 编码参数 | ❌ | 使用 render-plan 扩展 |
| AI 生成过程 | ❌ | 使用 metadata 标记 |

### 建议角色

- **OTIO** 作为 timeline exchange layer（导入/导出/互操作）
- **render-plan** 作为平台执行层（完整渲染参数）
- 两者之间双向转换

### 支持矩阵

| 操作 | OTIO 支持 | 平台第一阶段建议 |
|------|-----------|-----------------|
| Clip trim | ✅ yes | ✅ support |
| Track order | ✅ yes | ✅ support |
| Gap | ✅ yes | ✅ support |
| Transition dissolve | ✅ yes | ✅ support |
| Marker | ✅ yes | ✅ support |
| Metadata | ✅ yes | ✅ support |
| Subtitle burn | ⚠️ metadata/effect | ✅ support via render-plan |
| Overlay | ⚠️ track/metadata | ✅ support |
| Speed change | ⚠️ partial | 📋 defer |
| Complex VFX | ❌ no | 📋 defer |
| Color grading | ⚠️ limited | 📋 defer |
| Encoding params | ❌ no | ✅ render-plan only |
| AI generation | ❌ no | ✅ render-plan only |

---

## 4. LLM 多预览设计

### Variant Generation

一次用户请求生成多个编辑方案，每个方案有不同的风格和时长目标。

### Preview Profile

| 参数 | 预览值 | 最终值 |
|------|--------|--------|
| 分辨率 | 480p | 1080p/4K |
| 码率 | 1Mbps | 8Mbps+ |
| 帧率 | 24fps | 30/60fps |
| 编码器 | libxfast | libx264 slow |
| 音频 | AAC 64k | AAC 256k |

### 异步 Job

- 每个 variant 是一个独立的渲染作业
- 前端轮询状态 + 预览播放器
- 支持取消单个 variant

### 成本控制

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `max-variants` | 3 | 最大同时生成数 |
| `max-preview-duration-sec` | 60 | 预览最大时长 |
| `max-preview-tokens` | 4096 | LLM 最大 token |
| `preview-ttl-hours` | 24 | 预览缓存 TTL |
| `max-retry` | 1 | 失败重试次数 |

### 示例 JSON

```json
{
  "variants": [
    {
      "id": "variant-1",
      "style": "fast_cut",
      "description": "快节奏剪辑，适合社交媒体",
      "durationTargetSec": 30,
      "previewProfile": "480p-1M"
    },
    {
      "id": "variant-2",
      "style": "storytelling",
      "description": "叙事型剪辑，保留完整故事线",
      "durationTargetSec": 45,
      "previewProfile": "480p-1M"
    },
    {
      "id": "variant-3",
      "style": "product_focus",
      "description": "产品展示型，突出产品特写",
      "durationTargetSec": 30,
      "previewProfile": "480p-1M"
    }
  ],
  "assetIds": ["asset-1", "asset-2", "asset-3"],
  "prompt": "制作一个适合 Instagram Reels 的视频，风格活泼",
  "costBudget": {
    "maxVariants": 3,
    "maxTokens": 4096
  }
}
```

---

## 5. 自然语言连续修改

### Versioned Timeline

- 每次 patch 创建新的 timeline revision
- 保留完整历史（不可变）
- 支持 diff 比较任意两个版本

### Patch-Based Editing

- LLM 输出结构化的 patch 指令
- Patch 经过 schema validation 后应用
- 支持 undo/redo

### Edit History

```json
{
  "revisionId": "rev-3",
  "parentRevisionId": "rev-2",
  "patchType": "composite",
  "operations": [
    {
      "op": "trim",
      "targetClipId": "clip-2",
      "removeRange": {
        "start": "00:00:05.000",
        "end": "00:00:08.000"
      }
    },
    {
      "op": "move",
      "targetClipId": "clip-3",
      "newTrackIndex": 1,
      "newStartTime": "00:00:10.000"
    }
  ],
  "userPrompt": "把第三剪缩短 3 秒",
  "llmExplanation": "移除 clip-2 的 5-8 秒片段，总时长减少 3 秒",
  "createdAt": "2026-05-28T12:00:00Z"
}
```

### Diff

```json
{
  "fromRevision": "rev-2",
  "toRevision": "rev-3",
  "changes": [
    {
      "type": "trim",
      "clipId": "clip-2",
      "before": { "duration": "00:00:15.000" },
      "after": { "duration": "00:00:12.000" }
    }
  ],
  "totalDurationDelta": "-00:00:03.000"
}
```

### Rollback

- 选择任意历史 revision 恢复
- 生成反向 patch 或直接用快照恢复

### Selected Variant

- 用户从多 variant 中选择一个进入精编
- 选中后加载对应的 timeline 到编辑器

### Context Window Management

- 长对话时截断历史，保留关键决策点
- 使用 timeline summary 替代完整历史
- 分层 context：当前状态 + 最近 N 次操作 + 用户偏好摘要

### User Confirmation for Destructive Edits

以下操作需要用户确认：
- 删除轨道
- 删除素材引用
- 时长变更 > 50%
- 变速 > 2x
- 删除字幕轨道

### 示例 Patch

```json
{
  "patchType": "trim",
  "targetClipId": "clip-2",
  "removeRange": {
    "start": "00:00:05.000",
    "end": "00:00:08.000"
  },
  "expectedResult": {
    "durationDelta": "-00:00:03.000",
    "totalDuration": "00:00:27.000"
  }
}
```

---

## 6. 技术选型建议

### Timeline / Edit Format

| 选项 | 优点 | 缺点 | 建议 |
|------|------|------|------|
| **Custom Render Plan** | 完全控制，与现有渲染管道集成 | 无生态互操作 | ✅ Phase 1 |
| **OTIO** | 行业标准，互操作性强 | 复杂 VFX 表达有限 | ✅ Phase 2 import/export |
| **MLT XML** | MLT 原生格式 | 其他提供商不支持 | ⚠️ MLT 专用 |
| **EDL/XML/FCPXML** | 专业工具兼容 | 功能受限 | ⚠️ 导入/导出 |

**建议**：
- Phase 1：Custom Render Plan（平台执行层）
- Phase 2：引入 OTIO import/export（互操作层）
- Phase 3：OTIO 作为内部 timeline exchange

### Renderer

| 选项 | 优点 | 缺点 | 建议 |
|------|------|------|------|
| **FFmpeg filter_complex** | 成熟稳定，功能全 | 复杂合成不便 | ✅ Phase 1 |
| **MLT** | 专业 NLE 框架 | 学习曲线 | ✅ Phase 2 |
| **MoviePy** | Python 友好 | 性能一般 | ❌ |
| **Blender** | 3D 合成强 | 重量级 | ⚠️ Worker 专用 |
| **Natron** | 节点合成 | 维护不活跃 | ⚠️ 可选 |
| **GStreamer** | 流式处理 | 复杂度高 | ⚠️ 可选 |

**建议**：
- Phase 1：FFmpeg（已集成）
- Phase 2：MLT 或内部图（复杂合成）
- Complex VFX：通过 Blender/Natron Worker

### Agent / Tool Layer

| 选项 | 优点 | 缺点 | 建议 |
|------|------|------|------|
| **Direct backend tool calls** | 简单直接 | 紧耦合 | ✅ First |
| **MCP server** | 标准化，多客户端 | 额外维护 | ✅ Then |
| **ChatGPT Skill** | 用户触达 | OpenAI 依赖 | ⚠️ 可选 |
| **Internal agent orchestrator** | 完全控制 | 开发量大 | ⚠️ 长期 |

**建议**：
- First：后端确定性工具（已有 MCP 控制器基础）
- Then：OTIO 实用 CLI
- Then：ChatGPT Skill
- Then：MCP server

### LLM Strategy

| 选项 | 安全性 | 灵活性 | 建议 |
|------|--------|--------|------|
| **LLM 输出 raw FFmpeg 命令** | ❌ 危险 | 高 | ❌ 禁止 |
| **LLM 输出 render-plan JSON** | ✅ 安全 | 中 | ✅ 推荐 |
| **LLM 输出 OTIO** | ✅ 安全 | 中 | ✅ Phase 2 |
| **LLM 输出 patches** | ✅ 安全 | 高 | ✅ 推荐 |

**建议**：
- **不允许** LLM 输出 raw shell 命令
- **允许** LLM 输出 schema-validated render-plan / patch
- OTIO 可作为中间层

---

## 7. 安全与治理

### Schema Validation

- 所有 LLM 输出必须通过 JSON Schema 验证
- 验证失败 → 拒绝执行 + 返回错误给 LLM 修正

### Operation Allowlist

```yaml
allowedOperations:
  - trim
  - move
  - split
  - delete_clip
  - add_transition
  - adjust_speed
  - add_overlay
  - add_subtitle
  - adjust_volume
  - reorder_tracks
deniedOperations:
  - delete_track  # 需要用户确认
  - replace_asset  # 需要用户确认
  - export  # 不属于编辑操作
```

### Asset Permission Check

- 所有素材引用必须通过 assetId 校验
- 检查用户是否有权访问该素材
- 检查素材是否存在且未删除

### Tenant Isolation

- 所有操作在租户上下文内执行
- 跨租户素材引用被拒绝
- 渲染作业计入租户配额

### Max Render Duration

- 预览渲染：最大 60 秒
- 最终渲染：按租户 Entitlement 限制
- 超时自动取消

### Max Preview Count

- 每次请求最多 3 个 variant
- 每个用户最多 5 个并发预览
- 预览 24 小时后自动清理

### Cost Budget

- 每次请求 token 上限
- 每日 LLM 调用次数限制（按 Entitlement）
- 超出预算返回友好错误

### Audit Logging

- 记录每次 LLM 请求/响应摘要
- 记录 patch 应用结果
- 记录用户确认/拒绝
- **不记录** LLM 完整 prompt/response（隐私）

### Prompt Injection 防护

- 系统 prompt 与用户 prompt 隔离
- 用户输入中的指令性内容被转义
- LLM 输出仅作为建议，不直接执行

### Tool Call Allowlist

- LLM 只能调用白名单内的工具
- 工具参数经过类型校验
- 危险操作需要用户确认

### No Arbitrary Shell

- LLM 永远不能生成 shell 命令
- 渲染命令由 Worker 根据 render-plan 确定性生成
- FFmpeg 参数白名单

### No Arbitrary External URL

- LLM 不能指定任意外部 URL 作为素材
- 所有素材必须通过平台上传流程
- URL 引用需通过 SSRF 验证

### Content Safety

- 输出内容审核（如需要）
- 敏感内容检测
- 合规性检查

---

## 8. 未来任务拆分

### P4-LLM 系列（LLM 集成）

| ID | 任务 | 依赖 | 预估 |
|----|------|------|------|
| P4-LLM-1 | 定义 render-plan.json schema | 无 | S |
| P4-LLM-2 | render-worker 支持 render-plan 本地预览 | P4-LLM-1 | M |
| P4-LLM-3 | 素材分析 pipeline：ffprobe / 缩略图 / 场景检测 / ASR | 无 | M |
| P4-LLM-4 | LLM prompt → render-plan | P4-LLM-1, P4-LLM-3 | L |
| P4-LLM-5 | 多 variant 预览 | P4-LLM-4, P4-LLM-2 | L |
| P4-LLM-6 | 自然语言 patch | P4-LLM-4 | L |
| P4-LLM-7 | timeline versioning / diff / rollback | P4-LLM-6 | M |

### P4-OTIO 系列（OTIO 集成）

| ID | 任务 | 依赖 | 预估 |
|----|------|------|------|
| P4-OTIO-1 | OTIO validator / inspector | 无 | S |
| P4-OTIO-2 | OTIO → render-plan | P4-LLM-1 | M |
| P4-OTIO-3 | render-plan → OTIO export | P4-LLM-1 | M |
| P4-OTIO-4 | OTIO ChatGPT Skill | P4-OTIO-1 | M |
| P4-OTIO-5 | OTIO MCP server | P4-OTIO-1 | L |

### 依赖关系

```
P4-LLM-1 (schema)
  ├── P4-LLM-2 (render-worker)
  ├── P4-LLM-4 (LLM → plan)
  │     ├── P4-LLM-5 (多预览)
  │     └── P4-LLM-6 (patch)
  │           └── P4-LLM-7 (版本控制)
  ├── P4-OTIO-2 (OTIO → plan)
  └── P4-OTIO-3 (plan → OTIO)

P4-LLM-3 (素材分析) → P4-LLM-4

P4-OTIO-1 (validator)
  ├── P4-OTIO-4 (Skill)
  └── P4-OTIO-5 (MCP)
```

---

## 9. 当前代码基础

### 已有基础（可直接复用）

| 组件 | 状态 | 说明 |
|------|------|------|
| `AiTimelineAPI` (前端) | ✅ | AI 编辑/预览/提案 API 客户端 |
| `AiProposalsPanel` (前端) | ✅ | AI 提案列表组件 |
| `AiTimelineEditPanel` (前端) | ✅ | AI 时间线编辑组件 |
| `TimelineRevisionAPI` | ✅ | 时间线版本控制 API |
| `TimelinePatchStepsDialog` | ✅ | Patch 步骤对话框 |
| `TimelineHighlightNavigator` | ✅ | Patch 高亮导航器 |
| `IncrementalRenderAPI` | ✅ | 增量渲染 API |
| `McpMediaToolsController` | ✅ | MCP 媒体工具（probe/import/export） |
| `RenderPlan` (前端类型) | ✅ | 渲染计划类型定义 |
| `otio.ts` (前端工具) | ✅ | OTIO 导出逻辑 |
| `render-module` | ✅ | 渲染编排 + 提供商 |
| `ai-module` | ✅ | ChatProvider SPI |
| `StubChatProvider` | ✅ | 可替换为真实 Provider |
| `LiteLLM` 配置 | ✅ | 统一 AI 网关 |
| `prompt-module` | ✅ | Prompt 工程平台 |

### 需要新增

| 组件 | 说明 |
|------|------|
| `EditPlan` schema | Edit Plan JSON 定义 |
| `VariantGenerator` | 多 variant 生成服务 |
| `PatchApplier` | Patch 应用 + validation |
| `IntentParser` | 自然语言 → 结构化意图 |
| `TimelineVersionService` | 版本管理 + diff + rollback |
| `OTIOConverter` | OTIO ↔ render-plan 转换 |
| `CostBudgetService` | LLM 成本预算管理 |
| `ContentSafetyService` | 内容安全审核 |

---

## 10. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| LLM 输出不可预测 | Patch 错误 | Schema validation + 用户确认 |
| 成本失控 | 费用超支 | Token 限制 + 配额 + 预算 |
| 延迟过高 | 用户体验差 | 异步渲染 + 预览降级 |
| Prompt 注入 | 安全漏洞 | 系统/用户 prompt 隔离 |
| OTIO 功能限制 | 复杂编辑不支持 | render-plan 扩展层 |
| 多 variant 渲染成本 | 资源消耗 3x | 预览降级 + 并发限制 |

---

## 11. 成功指标

| 指标 | 目标值 |
|------|--------|
| 预览生成时间 | < 30 秒（480p） |
| LLM 响应延迟 | < 5 秒（首次 token） |
| Patch 应用成功率 | > 95% |
| 用户确认率 | > 80%（patch 无需修改直接应用） |
| 成本 per variant | < $0.10 |
| 端到端编辑周期 | < 5 分钟（从上传到导出） |
