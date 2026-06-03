# 技术债务

> **最后更新：** 2026-05-18

## 待移除的废弃包装器

### 渲染提供商包装器

| 废弃类 | 规范替代 | 文件位置 |
|--------|----------|----------|
| `FfmpegRenderProvider` | `FFmpegRenderProvider` | `infrastructure/ffmpeg/` |
| `FfmpegCommandFactory` | `FFmpegCommandFactory` | `infrastructure/ffmpeg/` |
| `FfmpegEnvironmentValidator` | `FFmpegEnvironmentValidator` | `infrastructure/ffmpeg/` |
| `FfmpegProbeService` | `FFmpegProbeService` | `infrastructure/ffmpeg/` |
| `GpacRenderProvider` | `GPACRenderProvider` | `infrastructure/gpac/` |
| `GpacPackagingProvider` | `GPACPackagingProvider` | `infrastructure/gpac/` |
| `GpacEnvironmentValidator` | `GPACEnvironmentValidator` | `infrastructure/gpac/` |
| `MeltCommandFactory` | `MLTCommandFactory` | `infrastructure/mlt/` |

**移除标准：**
1. 验证源代码中零引用（包括测试）
2. 移除 `@Deprecated` 类
3. 移除 `RenderProviderRegistrationTest` 中的相应测试断言
4. 更新本文档

### 废弃领域类型

| 废弃类型 | 替代 | 备注 |
|----------|------|------|
| `MediaValidationReport` | `MediaProbeResult` | 旧的探测结果类型 |
| `MediaProbeService.probeLegacy()` | `MediaProbeService.probe()` | 为向后兼容返回旧类型 |

## 剩余不一致项

### 1. MLT 类双重命名

| 当前名称 | 规范 | 状态 |
|----------|------|------|
| `MltRenderProvider` | `MltRenderProvider` | 保留 — "Mlt" 作为专有名称前缀 |
| `MLTCommandFactory` | `MLTCommandFactory` | 正确 — 3 字母缩写 |
| `MeltCommandFactory` | `MLTCommandFactory` | **@Deprecated 包装器** — 可安全移除 |

### 2. 无 SubtitleBurnInAdapter 接口

`SubtitleBurnInService` 是一个具体的 `@Service`。不像 `MediaProbeAdapter` 那样使用适配器模式。

**影响：** 低。当前仅一个实现。

### 3. `app/` 包中的 Repository 类

`RenderJobStatusHistoryRepository` 和 `QuotaUsageRepository` 位于 `com.example.platform.render.app` 中，而非专用的 `repository` 包。

**影响：** 低。无功能影响。

### 4. `RenderProviderAutoConfiguration` 使用 `CommandLineRunner`

提供商注册通过 `CommandLineRunner` bean 发生。可行但不如 `@PostConstruct` 或 `ApplicationReadyEvent` 明确。

**影响：** 低。

### 5. `GStreamerCommandFactory` 是 `@Component`

`GStreamerCommandFactory` 标注了 `@Component`，而其他命令工厂是普通类。

**影响：** 低。两种模式都有效。

### 6. `JavaCVMediaProbeAdapter` 具体注入

`MediaProbeService` 构造函数接受 `JavaCVMediaProbeAdapter`（具体类）而非 `MediaProbeAdapter`（接口）。

**影响：** 低。

### 7. `OpenTimelineioAdapter` 是占位符

对 `toOtioJson()` 和 `fromOtioJson()` 均抛出 `UnsupportedOperationException`。

**影响：** 中。任何调用 OTIO 导出/导入的代码路径都会失败。

### 8. 字体子集生成是占位符

`FontRegistryService.generateFontSubset()` 复制原始字体而非生成真正的子集。

**影响：** 低。生产环境应使用 fonttools 或 Harfbuzz。

### 9. 字幕轨道格式为 `Map<String, Object>`

字幕轨道在整个烧录管道中以 `List<Map<String, Object>>` 传递。无类型化 DTO。

**影响：** 低。类型安全问题。

### 10. 环境验证是一次性的

提供商环境验证仅在启动时运行。如果二进制文件在启动后不可用，提供商仍保持"健康"状态。

**影响：** 低。

### 11. 无提供商指标

提供商选择、回退事件和渲染时长已记录但未作为指标导出。

**影响：** 低。

## 清理优先级顺序

| 优先级 | 项 | 工作量 |
|--------|-----|--------|
| 高 | 移除废弃的 FFmpeg/GPAC 包装器类 | 低 |
| 高 | 移除 `MediaValidationReport` 和 `probeLegacy()` | 低 |
| 中 | 将 repository 类移至 `repository/` 包 | 低 |
| 中 | 为字幕轨道添加类型化 DTO | 中 |
| 低 | 标准化工厂 bean 与普通类模式 | 低 |
| 低 | 添加提供商健康检查 HTTP 端点 | 中 |
| 低 | 添加每提供商 Micrometer 指标 | 中 |
| 未来 | 实现真正的字体子集生成 | 高 |
| 未来 | 实现 OTIO 导入/导出 | 高 |
