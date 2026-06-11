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

## 暂停/弃用条件

- 如果无法证明相对 FFmpeg 在字幕/模板场景有明确收益
- 如果字体 asset 管理无法统一
