# ADR-005: LiteFlow 工作流编排

## 状态

已接受

## 日期

2026-06-11

## 背景

系统需要一个灵活的 workflow 编排层，用于：
- 多步骤 render workflow 编排
- provider capability routing
- fallback 策略
- pre-process → render → post-process → package 流程

## 决策

使用 LiteFlow 作为 Render Workflow 编排层。

### 适合使用 LiteFlow 的场景

1. 多步骤 render workflow 编排
2. provider capability routing
3. fallback 策略
4. pre-process → render → post-process → package 流程
5. POC / Spike provider 的手动实验链路

### 不适合使用 LiteFlow 的场景

1. Provider 类型分派的简单 switch
2. FFmpeg 参数构造细节
3. 每个 provider 内部的低层命令分支
4. 高频、固定、简单逻辑
5. 未经版本管理的线上动态规则热更新

### 两层设计

1. **简单类型分派**：代码 switch
2. **复杂 workflow 编排**：LiteFlow

### LiteFlow Chain 版本管理

每个 LiteFlow chain 必须包含以下版本信息：
- renderRuleVersion
- providerVersion
- templateVersion
- fontVersion
- chainVersion

### Production Chain 限制

- Deprecated provider 不能进入任何 chain
- Hold provider 默认不进入 production chain
- Spike provider 只能进入 manual / experiment chain
- BMF 只能进入 bmf_spike_test 等手动实验 chain

## 后果

- 复杂 workflow 可配置编排
- 简单逻辑保持代码 switch
- Production chain 安全可控

## 替代方案

1. 全部使用代码 switch - 被拒绝，因为复杂 workflow 编排不灵活
2. 全部使用 LiteFlow - 被拒绝，因为简单逻辑不需要编排层
