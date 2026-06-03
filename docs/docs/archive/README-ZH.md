# 文档索引 (中文)

> **最后更新**: 2026-05-13
> **状态**: 文档审查与补全完成

## 文档阅读顺序推荐

### 第一阶段：项目概览
1. **README.md** - 项目总览和快速开始
2. **docs/architecture-notes.md** - 架构设计和版本策略
3. **docs/layering-and-open-source.md** - 模块分层和开源栈

### 第二阶段：核心功能实现
4. **docs/render-pipeline-implementation.md** - 渲染管道真实实现 ⭐
5. **docs/javacv-migration-guide.md** - JavaCV迁移指南 ⭐
6. **docs/ai-engine-spi.md** - AI引擎SPI ⭐
7. **docs/notification-template-management.md** - 通知模板管理 ⭐

### 第三阶段：模块边界与安全
8. **docs/extension-module-boundary.md** - 扩展模块边界 ⭐
9. **docs/module-boundaries.md** - 模块边界定义
10. **docs/security-and-tenancy.md** - 安全和租户隔离

### 第四阶段：未来规划
11. **docs/render-provider-extension-roadmap.md** - 渲染Provider扩展路线图 ⭐
12. **docs/technical-roadmap-video-platform.md** - 技术路线图
13. **docs/render-worker-architecture.md** - 渲染Worker架构

### 第五阶段：运维与部署
14. **docs/runbook-local-docker.md** - Docker本地运行
15. **docs/runbook-five-capabilities.md** - 五项能力运行手册
16. **docs/docker-external-config.md** - Docker外部配置

### 第六阶段：问题与复核
17. **docs/documentation-gap-report.md** - 文档缺口报告 ⭐
18. **docs/media-processing-module.md** - 媒体处理模块说明 ⭐
19. **docs/human-review-needed.md** - 人工复核清单

## 新创建的核心文档（⭐标记）

### 渲染系统
- **render-pipeline-implementation.md** - 完整的渲染管道实现，包括JavaCV能力、限制、状态机
- **javacv-migration-guide.md** - JavaCV迁移的详细说明，包括与FFmpeg CLI的对比
- **render-provider-extension-roadmap.md** - 未来扩展路线图（OFX、GStreamer、MLT、GPAC）

### AI系统
- **ai-engine-spi.md** - AI引擎的SPI接口，当前只有Stub实现，规划真实Provider

### 通知系统
- **notification-template-management.md** - 8个内置模板，变量替换，多语言规划

### 扩展系统
- **extension-module-boundary.md** - 扩展模块的当前状态和安全边界

### 问题报告
- **documentation-gap-report.md** - 关键发现：JavaCV迁移未完成、media-processor模块缺失等
- **media-processing-module.md** - 媒体处理模块缺失的说明

## 快速开始

### 中文读者推荐顺序：
1. 先看 **README.md** 了解项目结构
2. 阅读 **docs/documentation-gap-report.md** 了解当前状态和问题
3. 查看 **docs/render-pipeline-implementation.md** 理解渲染实现
4. 参考 **docs/runbook-local-docker.md** 运行本地环境
5. 根据需求阅读其他文档

### 开发者重点关注：
- **render-pipeline-implementation.md** - 理解当前实现
- **extension-module-boundary.md** - 了解安全限制
- **ai-engine-spi.md** - AI集成方式

### 运维人员重点关注：
- **runbook-local-docker.md** - 本地部署
- **docker-external-config.md** - 生产配置
- **observability.md** - 监控和日志

## 视频处理工具文档

### 当前使用工具
- **JavaCV 1.5.10** - 主要渲染实现
- **FFmpeg 6.1.1** - 通过JavaCV捆绑
- **Apache Commons Exec 1.6.0** - 仍然存在（需移除）

### 规划工具
- **MLT/melt** - 非线性编辑
- **GPAC/MP4Box** - 流媒体打包
- **GStreamer** - 管道处理
- **OFX** - 专业特效
- **Pango/Cairo** - 文字和图形渲染

## 需要人工复核的问题

### 高优先级（P0）
1. JavaCV迁移未完成 - Apache Commons Exec仍然存在
2. media-processor-module缺失 - 文档中引用但仓库中不存在
3. AI模块只有Stub实现 - 无真实Provider

### 中优先级（P1）
1. 扩展模块CLI工具安全性审查
2. 通知模板多语言支持
3. 渲染Worker拆分架构

---

*这个索引将帮助你快速找到所需文档。所有新创建的文档都已包含详细的实现说明、限制条件未来规划。*