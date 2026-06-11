# LiteFlow 集成方案

## 是否使用 LiteFlow

**结论**：可以使用 LiteFlow，但需要明确使用边界。

## LiteFlow 使用边界

### 适合使用 LiteFlow 的场景

1. 多步骤 render workflow 编排
2. provider capability routing
3. fallback 策略
4. pre-process → render → post-process → package 流程
5. POC / Spike provider 的手动实验链路
6. 复杂任务链路，如：
   - `captioned_video_export`
   - `hls_package_export`
   - `timeline_export`
   - `bmf_spike_test`
   - `blender_intro_then_remotion_caption_export`

### 不适合使用 LiteFlow 的场景

1. Provider 类型分派的简单 switch
2. FFmpeg 参数构造细节
3. 每个 provider 内部的低层命令分支
4. 高频、固定、简单逻辑
5. 未经版本管理的线上动态规则热更新

## 推荐架构

```
Render API
  -> RenderOrchestrator
    -> RenderPlanner
      -> LiteFlow Chain / Rule（可选）
        -> Provider Adapter
          -> FFmpeg / Remotion / MLT / GPAC / Libass / Blender / BMF
```

## 两层设计

1. **简单类型分派**：代码 switch
2. **复杂 workflow 编排**：LiteFlow

## LiteFlow Chain 版本管理

每个 LiteFlow chain 必须包含以下版本信息：

- `renderRuleVersion`
- `providerVersion`
- `templateVersion`
- `fontVersion`
- `chainVersion`

## LiteFlow 调度规则

### Production Chain

- Deprecated provider 不能进入任何 chain
- Hold provider 默认不进入 production chain
- Spike provider 只能进入 manual / experiment chain
- BMF 只能进入 bmf_spike_test 等手动实验 chain
- Production chain 不允许隐式调用 Spike / Hold / Deprecated provider
- fallback 必须受 job.allowDegrade 控制

### Experiment/Manual Chain

- 可以包含 Spike / Hold provider
- BMF 只能出现在 manual / experiment chain
- 必须明确标记为实验/手动模式

## 每个 Render Job 需要保存

- 实际执行链路
- 命中的 provider
- provider version
- 输入参数
- 输出 artifact
- LiteFlow chain id
- LiteFlow rule version
- 错误信息
- 是否发生 fallback

## 实现建议

Phase 5 先建立 LiteFlow POC，不要立刻用 LiteFlow 替代全部调度。

先为以下链路建立 LiteFlow POC：
1. `captioned_video_export`
2. `hls_package_export`
3. `timeline_export`
4. `bmf_spike_test`

LiteFlow 只负责编排 workflow，不负责 provider 内部实现细节。
