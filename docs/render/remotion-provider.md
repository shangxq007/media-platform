# Remotion Provider 详细设计

## 职责

RemotionRenderProvider 是 **POC / P1 / CompositionRenderProvider**。

### 核心职责

1. **字幕字体**：支持自定义字体，通过统一 font asset 管理
2. **字幕特效**：逐词高亮、动态字幕效果
3. **模板渲染**：React 模板化视频、品牌包装、标题卡
4. **前后端一致预览**：前端 Remotion Player 预览，后端 Remotion Renderer 输出

### 不负责

- 视频 trim、转码、音频提取、格式修复
- 多轨 timeline 编辑
- 3D 渲染
- 流媒体打包
- 基线字幕烧录（baseline subtitle burn-in）— 该能力由 FFmpeg/libass 负责

## 与 FFmpeg/libass 分工

- **FFmpeg/libass** 是字幕烧录的生产基线，处理标准 SRT/WebVTT/ASS 字幕
- **Remotion** 用于高级动态字幕（逐词高亮、卡拉OK、社交媒体字幕、品牌模板等）
- Remotion 不替代 FFmpeg/libass 基线字幕烧录，二者是不同层级的能力

## 输入

标准 RenderJob JSON：

```json
{
  "compositionId": "caption-template-001",
  "templateId": "tiktok-subtitle",
  "propsJson": "{ \"text\": \"Hello World\", \"style\": \"modern\" }",
  "fontFamily": "NotoSansCJK",
  "fontAssetUri": "s3://fonts/NotoSansCJK.ttf",
  "captionsJson": "[{\"text\": \"Hello\", \"start\": 0, \"end\": 1}]"
}
```

## 输出

- MP4 视频文件
- 或 frames 序列

## 字体管理

- 字体必须通过统一 font asset 管理
- 不允许依赖系统字体
- 前端和后端共享同一字体 asset
- 确保前后端字体一致性

## 字幕断行与时间轴一致性

- 字幕断行和时间轴必须由上游 RenderJob 提供
- Remotion 只负责渲染，不重新决定字幕断行
- 前后端共享 template/schema，最大化预览和导出一致性

## 前端预览

前端使用 Remotion Player 进行预览：

```tsx
import { Player } from '@remotion/player';
import { CaptionTemplate } from './templates/CaptionTemplate';

<Player
  component={CaptionTemplate}
  compositionWidth={1080}
  compositionHeight={1920}
  fps={30}
  durationInFrames={150}
  props={props}
/>
```

## 后端渲染

后端使用 Remotion CLI 进行渲染：

```bash
npx remotion render <composition> <output> --props <props-file>
```

## 晋级条件

- 完成字幕字体、字幕特效、模板渲染 POC
- 输入标准 RenderJob JSON
- 输出 MP4 或 frames
- 与前端 Remotion Player 共享 template/schema
- 完成字体一致性测试

## Production Hardening Status

### R1 — Input Validation and Font Preflight (COMPLETED)

- Schema validation for Remotion input props (dimensions, fps, captions, font specs, templates)
- Font readiness preflight before Remotion render
- sourceUrl exclusion hardening in render command payload
- Remotion dispatch exclusion for baseline subtitle burn-in verified
- Advanced template capability representation via Stable SPI verified

### R2 — Worker Environment Contract and Deterministic Render Skeleton (CURRENT)

- Worker environment contract defined (see below)
- Internal worker configuration model with safe defaults
- Template resolution guard rejecting raw code, filesystem paths, URLs
- Deterministic render test skeleton
- Production safety tests (npm install disabled, code execution blocked, system fonts rejected)
- Remotion production dispatch still globally disabled

### R3 — Planned

- Real worker image build and deployment
- ExecutionEnvironment integration (OpenCue or similar)
- Actual golden render fixture with pixel comparison
- Capability-level dispatch flag (not global enable)

## Worker Environment Contract

### Required Runtimes

| Component | Policy | Version |
|-----------|--------|---------|
| Node.js | Pinned major version, set via worker image | 22.x |
| Remotion CLI | Pinned via bundled package, set via worker image | 4.x |
| Chrome/Chromium | headless-shell mode only, set via worker image | stable |
| FFmpeg | Required for output normalization after Remotion render | system or bundled |

### Font Loading Policy

- Fonts load exclusively via FontManifest `subsetUrl` (preferred) or `sourceUrl` (fallback)
- No system font dependency — `--font` or system font directories not allowed
- Font asset readiness verified via font preflight before render
- Production mode requires `productionSafe=true` on all font specs

### Allowed Input Format

- Declarative, schema-validated `RemotionInputProps` only
- `compositionWidth`, `compositionHeight`, `fps`, `durationInFrames` — validated range
- `captions[]` — validated id/text/time/style/words
- `fontSpecs[]` — validated family/weight/style/subsetUrl/productionSafe
- `template` — validated templateId/templateVersion/params (see Template Resolution below)
- `outputFormat` — one of: mp4, webm, mov, png-sequence, jpeg-sequence

### Template Resolution Policy

- Templates resolved by `templateId` + `templateVersion` against a trusted registry
- Trusted registry root: `bundled://remotion-templates/`
- **Rejected**: raw React/JS/TS source code, arbitrary filesystem paths, npm package names, URLs, path traversal
- Template params validated for code injection patterns

### Forbidden Runtime Behaviors

| Behavior | Default | Override |
|----------|---------|----------|
| Network access | Disabled | Controlled environment only |
| npm install at runtime | Disabled | Never |
| User code execution | Disabled | Never |
| System font dependency | Disabled | Never |
| Shell command construction | Disabled | Process argument list only |
| Raw template source | Rejected | Never |

### Resource Limits

| Limit | Default |
|-------|---------|
| Render timeout | 900,000 ms (15 min) |
| Max output size | 2 GB |
| Max duration | 3,600 seconds (1 hour) |
| Max width | 7680 px (8K) |
| Max height | 4320 px (8K) |
| Max fps | 120 |
| Max captions | 5,000 |
| Max font specs | 100 |

### Output Directory Policy

- Output written to worker-specific temporary directory: `/tmp/remotion-worker/output/`
- Output path must stay under the controlled working directory
- Arbitrary output paths from user input are rejected

### Network Policy

- Network access: **disabled by default**
- May be enabled for controlled environments only (e.g., internal asset CDN)
- Never enabled for arbitrary internet access
- Never used for npm package download at runtime

### Filesystem Policy

- Working directory: `/tmp/remotion-worker/`
- Output directory: `/tmp/remotion-worker/output/`
- No write access outside working directory
- Input font files loaded from FontManifest URLs (S3/storage, not local filesystem)
- No system font paths consulted

### Deterministic Render Expectations

- Same `RemotionInputProps` → same normalized command payload
- Same FontManifest/subsetUrl → stable render input
- `sourceUrl` never present in render command
- `templateId`/`templateVersion` stable and validated
- Invalid dimensions/fps/duration rejected before render attempt
- Unsafe template references rejected before render attempt
- Command builder output deterministic for equivalent inputs

## 暂停/弃用条件

- 如果无法证明相对 FFmpeg 在字幕/模板场景有明确收益
- 如果字体 asset 管理无法统一
