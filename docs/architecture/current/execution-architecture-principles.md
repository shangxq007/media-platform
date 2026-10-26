# Media Platform — 执行架构专项原则

本文件补充[功能驱动开发与模块架构原则](../../engineering/functional-development-and-module-principles.md)，
不构成第二套默认总则、规划模型或领域契约。以下区分已采纳的**边界要求**与**实现状态**；
记录方向不等于证明适配器、部署或完整优化已经完成，也不授予功能开发权限。

## Timeline 与规划主链

- 平台参考 OpenTimelineIO 并扩展自己的 Canonical Timeline；平台拥有其语义和身份。
  Operation 是修改 Timeline 的正式入口，TimelineRevision 是不可变版本，不新增绕过 Operation 的直接修改路径。
- OTIO 主要用于交换与互操作；渲染不要求先把平台 Timeline 转回 OTIO。
  OpenCue 不直接解释 Canonical Timeline 或决定媒体语义。
- 优先复用既有主链，不为 OpenCue 或 BMF 建立另一个 RenderPlan 权威：

```text
TimelineRevision
  → #20 RenderPlan / RenderGraph
  → #21 LogicalExecutionGraph / PhysicalExecutionPlan
  → #22 Provider Binding
  → Execution Dispatch
  → Runtime / Artifact Commit
```

这是职责和依赖方向，不表示每个入口或历史路径均已完整收敛。规划从平台版本和计划派生，
外部执行后端不能反过来定义平台的编辑或规划模型。

## Capability、Provider 与执行分区

- 平台负责 Capability 合法性、Provider 候选匹配、选择及绑定。
  执行环境履行已固定的绑定，不擅自切换 Provider、改写语义或扩大执行授权。
- 多 Provider 通过明确的数据与执行契约协作；跨 Provider 数据交接优先采用有类型、不可变、
  可验证的 Artifact materialization。无法证明等价性时，不做不安全的融合或优化。
- BMF 是媒体 Graph/Pipeline 引擎，可承载自定义 Module、媒体操作融合及 CPU/GPU 计算。
  它不拥有 Timeline、RenderJob、Artifact Commit 或跨平台 Provider 选择权威。
  可在同一执行分区内部完成的媒体 Graph，不为适配 OpenCue 而强制拆散。

## OpenCue Role V2

OpenCue 定位为**可选、专业、可替换的离线 Render Farm 执行后端**。
它可接收平台产生的 Job/Layer/Frame 执行投影，负责该农场内的排队、资源分配和依赖调度；
平台授权、绑定和跨后端完成契约在委托边界仍须成立。

OpenCue 不替代通用 Worker Fabric，不拥有 Canonical Timeline、RenderPlan、Capability/Provider 选择、
Temporal 业务 Workflow、Artifact Commit 或 Render Job 最终完成权威。
正式 API/Worker 和基础 FFmpeg/BMF 路径不能强制依赖 OpenCue 存在。
早期通用优先调度器的 OpenCue Role V1 已被替代，不得恢复。

既有[后端职责修订](../governance/roadmap-22-worker-runtime-backend-resource-accounting-amendment-1.md)
和[运行所有权修订](../governance/roadmap-22-runtime-ownership-resource-hold-completion-amendment-3.md)
提供相应依据。规则标识本身的 `_V1` 后缀不等于已退休的 OpenCue Role V1 定位。
这些边界不代表真实 OpenCue 农场接入或部署已经验收。

## 增量渲染与多产物

- 局部失效、时间窗口影响、缓存复用及跨 Revision 内容判断属于平台规划职责；
  OpenCue 只调度平台决定需要执行的工作。
  **规划级完整增量渲染尚未完成**，不能由旧的 incremental 类、局部复用或 Worker 缓存推导为已实现。
- [ADR-025](../adr/ADR-025-artifact-dag-indefinite-deferral.md) 对全局 Artifact DAG 的延期继续有效；
  不因 OpenCue 集成恢复它，也不把现有 Provenance、任务依赖或已校验的局部复用等同于该全局方案。
  [ADR-007](../adr/ADR-007-execution-planner.md) 的 Artifact DAG 规划输入/权威依赖模型是与后续裁决不一致的旧前提，
  不能据此重启方案；原 ADR 文本及正式登记本次未改，登记整理需另行授权。
- 一个执行任务可以产生多个媒体或数据输出；正式 Artifact 身份和提交仍归平台。
  scope、attempt/generation、内容校验、Provenance、不可变引用与领域终态权威必须保留。
  执行后端返回成功不等于 Artifact Commit 成功；最终成品按正式输出契约识别，不猜取首条或最新记录。
  本原则不证明所有现有适配器已经支持完整多产物发布。

## 基础设施透明性：JuiceFS

JuiceFS 是**可选共享文件系统和缓存基础设施**，不是领域模型、执行规划或 Artifact 权威。

- Timeline、Operation、Canonical RenderPlan、Capability、Provider 领域语义与 Artifact 领域身份
  不直接依赖文件系统品牌、挂载路径或缓存实现；使用平台的 ArtifactId、ContentDigest、StorageReference 等抽象身份。
  禁止把 `/mnt/juicefs/...` 这类物理路径写入 Canonical Timeline 或作为 Artifact 唯一领域身份。
- JuiceFS、NFS、CephFS、S3/MinIO、本地 NVMe 可在合适的 Storage/Execution 基础设施契约下使用。
  FFmpeg、BMF、OpenCue Worker 可通过受控挂载和标准路径 I/O，无需 JuiceFS 专用业务接口；
  Worker、Storage Adapter、部署与监控层可感知实际后端。
- 未来可通过通用资源契约表达缓存命中、数据局部性、吞吐和可用性，不能把 JuiceFS 品牌直接写为核心领域的
  Provider 选择条件。优化不得改变媒体语义或绕过授权。
- 文件可见不等于 Artifact 正式可用，输出仍须校验和 Artifact Commit；
  不默认采用会使已提交成品失去持久性保证的异步写入策略。
- 当前不新增 JuiceFS Module、Storage Provider 或执行链路。
  真实接入留待多 Worker/BMF/OpenCue 的具体需求单独授权与验证；此处只固定基础设施无感边界。

这一方向与既有[materialization/reuse 决定](../governance/roadmap-22-phase-16-artifact-staging-materialization-reuse-decision-recovery.md)
中 JuiceFS `POC_CANDIDATE`、Provider 不直连后端和 Artifact 提交权威一致；并未把候选技术升级为已实现能力。

## First Render 与当前实施边界

First Render 是可长期保留的**薄型架构验证探针**；保留正式 `/api/first-render` 入口。
底层复用通用规划、执行、Artifact 和 Render 的公开契约，不形成独立长期业务领域系统。
相关实现和当前验证方式见[开发指南](../../development/canonical-baseline.md)与
[First Render 说明](../../testing/first-render.md)；本文件不修改、禁用入口或新增执行链。

已有局部实现或历史验收不证明全路径已收敛 Operation、规划级增量渲染、完整多产物、BMF/OpenCue 生产接入、
JuiceFS 或正式权限部署。未完能力与历史表述需要分别记录，不能靠原则文档宣布完成。
数据库阶段政策只引用[既有 Flyway 基线](../../database/flyway-migration-baseline.md)，不另建迁移权威。
