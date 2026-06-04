Technical Roadmap: Video Platform Integration

### 前端
- Vue.js Timeline Editor
  - 多轨道支持（视频/音频/字幕）
  - Clip 拖拽、裁剪、删除
  - 字幕轨管理（多语种、字体、打轴）
- Effects Panel
  - 自定义特效包加载（effect-pack.json）
  - 特效拖拽、参数调整
- Export Panel
  - 内置 / 外挂字幕选择
  - 渲染 preset 与用户等级匹配
- 字幕解析库
  - subtitles-parser / ass-parser
  - fontkit / opentype.js
- 预览
  - Canvas / WebGL / Video.js

### 后端
- RenderPipeline Providers
  - JavaCVProvider (FFmpeg + OpenCV)
  - OFXProvider (高级特效)
  - GStreamerProvider (可选实验管线)
  - GPACProvider (MP4封装)
  - MLTProvider (Timeline composition)
- OTIO JSON 作为统一 Timeline / Effect / Subtitle schema
- 字体 fallback 与 RenderJob 校验
- 多语种字幕轨道渲染
- Script / CLI 迁移器通过 extension-module
- LiteFlow / policy-governance-module 管理版本迁移策略

### 测试与部署
- Docker Compose 本地环境
- smoke test：Timeline → Export Panel → RenderJob → Artifact → 字幕渲染
- 单元 + 集成测试
- MANIFEST 和文档更新

### 用户等级策略
- FREE → 基础 JavaCV + 外挂字幕
- PRO → JavaCV/OFX + 1–2 内置字幕轨
- TEAM → OFX + 多语种内置字幕
- ENTERPRISE → 全量 Provider + 高级特效
- EXPERIMENTAL → 可使用实验 provider

### 输出格式
- 视频：MP4, MOV
- 字幕：内置 / 外挂（SRT, ASS）
- 特效：effectKey + providerPreference + parameters

