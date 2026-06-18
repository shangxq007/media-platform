# 常见问题 (FAQ)

> **最后更新:** 2026-05-14

---

## 快速开始

**Q: 如何快速启动本地开发环境？**
```bash
# 后端
cd media-platform && ./gradlew :platform-app:bootRun

# 前端
cd frontend && npm run dev
```

**Q: 如何运行所有测试？**
```bash
./gradlew test
```

**Q: 如何构建生产版本？**
```bash
./gradlew :platform-app:bootJar  # 后端
cd frontend && npm run build     # 前端
```

---

## 渲染流水线

**Q: 支持哪些渲染提供者？**
- JavaCV（转码、水印、字幕烧录）
- OFX（特效、过渡、滤镜）
- GPAC（DASH/HLS 打包）
- MLT（时间线渲染）
- GStreamer（流水线处理）
- FFMPEG（通用转码）

**Q: 如何使用 GPU 渲染？**
选择 GPU 预设（GPU_H264、GPU_H265、GPU_VP9），系统会自动路由到远程工作者。需要团队或更高层级。

**Q: 渲染任务失败怎么办？**
1. 检查任务状态: `GET /api/v1/render/jobs/{jobId}`
2. 查看错误码和详情
3. 重试: `POST /api/v1/render/jobs/{jobId}/retry`

**Q: 字幕烧录支持哪些格式？**
SRT、VTT、ASS。字幕在渲染时烧录到视频中。

**Q: 如何做增量渲染（只重跑变更部分）？**
1. 新时间线必须是 **Internal Timeline 1.0** JSON（`schemaVersion: "1.0"`）。
2. 提交时带上已完成作业的 `baseJobId`（或 MCP `render_timeline` / `render_segment`）。
3. 启用 `renderGraph.segmentPolicy` 时可做段级复用；详见 [增量渲染说明](incremental-rendering.md)。

**Q: 如何获取段/终稿 cache 的下载链接？**  
作业完成后调用 `GET /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/cache/presign`；单条 cache 加查询参数 `cacheKey`（须 URL 编码）。前端可用 `IncrementalRenderAPI.presignCache()`。

**Q: 内容哈希失效如何通知？**  
增量执行前发布 `render.cache.hash_invalidated` 事件；可在通知设置中订阅 WEBHOOK/邮件，或配置 `RENDER_CACHE_WEBHOOK_URL` 接收出站 POST。

**Q: 如何清理过期远程 cache？**  
开启 `RENDER_CACHE_CLEANUP_ENABLED` 后由定时任务按 `RENDER_CACHE_RETENTION_DAYS` 清理；或调用 `POST .../render/cache/cleanup` / `IncrementalRenderAPI.cleanupExpiredCache()`。

**Q: 段级缓存与 S3 如何配置？**
设置 `render.cache.upload-enabled=true` 与 `storage.s3.enabled=true`。支持 MinIO、自托管 RustFS（`STORAGE_S3_COMPATIBILITY=generic`）、**Cloudflare R2**（`STORAGE_S3_COMPATIBILITY=r2` 或 profile `r2`，见 [部署配置](vault-and-rustfs-setup.md) §3.7）。开启 `render.cache.content-hash-enabled` 后，哈希不匹配的任务会自动改为全量执行该阶段。

---

## 提示词平台

**Q: 如何创建提示词模板？**
```bash
curl -X POST /api/v1/prompts/templates \
  -d '{"name": "我的模板", "category": "custom"}'
```

**Q: 如何进行风险分析？**
```bash
curl -X POST /api/v1/prompts/risk/analyze \
  -d '{"content": "提示词内容", "category": "general"}'
```

**Q: 高风险提示词会被如何处理？**
- 严重: 自动阻止 (BLOCK)
- 高: 需要人工复核 (REQUIRE_REVIEW)
- 中: 记录警告，可继续
- 低: 允许执行

**Q: 如何回滚模板版本？**
```bash
curl -X POST /api/v1/prompts/templates/{id}/rollback \
  -d '{"targetVersion": "1.0.0"}'
```

---

## 成本控制

**Q: 如何设置预算？**
```bash
curl -X PUT /api/v1/billing/tenants/{tenantId}/budget \
  -d '{"budgetLimit": 100.0}'
```

**Q: 预算超限时会发生什么？**
- 软限制 (80%): 发出警告
- 硬限制 (100%): 阻止新的渲染任务
- 超额容忍 (110%): 允许少量超额

**Q: 如何估算渲染成本？**
```bash
curl -X POST /api/v1/billing/cost/estimate \
  -d '{"providerKey": "javacv", "preset": "default_1080p", "durationSeconds": 60}'
```

---

## 监控与反馈

**Q: 如何启用 Sentry？**
```bash
export SENTRY_DSN=https://xxx@yyy.ingest.sentry.io/zzz
export SENTRY_ENABLED=true
```

**Q: 如何提交用户反馈？**
点击右下角的"反馈"按钮，填写类型、严重度、标题和描述。

**Q: 如何查看监控状态？**
前端页面底部显示 Sentry 和 OpenReplay 状态指示器。

---

## 扩展系统

**Q: 如何注册自定义扩展？**
```java
registry.registerProviderExtension("my-provider", new MyProviderExtension(), "admin");
```

**Q: 扩展的安全限制是什么？**
- 执行超时: 30 秒（最大 120 秒）
- 输出限制: 4 MB
- 网络访问: 默认禁用
- 文件系统: 仅工作目录

**Q: 如何回滚扩展？**
```java
registry.rollbackExtension("my-provider", "1.0.0", "admin");
```

---

## 问题数据

**Q: 系统如何检测问题数据？**
自动扫描渲染任务、提示词执行、提供者/工作者输出，使用 12 条检测规则。

**Q: 检测到问题数据后会发生什么？**
1. 标记为 DETECTED
2. 可修复项自动修复
3. 严重问题自动隔离
4. 复杂问题标记为需要人工复核

**Q: 如何查看隔离的数据？**
查询 `quarantined_render_jobs`、`quarantined_prompt_executions` 表。

---

## AI 时间线编辑与增量渲染

**Q: 前端如何发起 AI 改时间线？**  
在编辑器右侧导出面板选择「增量 / AI」，填写自然语言指令后点击「应用 AI 编辑」。请求走 `POST /api/v1/tenants/{tenantId}/projects/{projectId}/timeline/ai-edit`，结果可再用于「增量导出」。

**Q: 编辑器 schema 2.0 与 Internal Timeline 1.0 关系？**  
前端提交编辑器 JSON（`schemaVersion: 2.0.0`）；`AiTimelineEditService` 与渲染管线在服务端规范化为 Internal Timeline 1.0。详见 [ai-timeline-editing.md](ai-timeline-editing.md)。

**Q: 如何接 Gemini / OpenRouter / NIM？**  
部署 LiteLLM，启用 `spring.profiles.active` 含 `litellm`，在 `app.ai.routing` 配置各 capability 的 `model`。见 [ai-gateway-architecture.md](ai-gateway-architecture.md)。

**Q: 多轮改稿如何关联上一轮成片？**  
选择「基准作业」或传 `baseJobId`；`editSessionId` 与 `metadata.platform.ai.*` 写入时间线与作业元数据，成片在 R2/对象存储，不在 LiteLLM 侧持久化。

---

## 已知限制

| 限制 | 说明 | 计划 |
|------|------|------|
| 无用户认证 | 仅支持 API 密钥认证 | 🔴 阻塞 |
| AI 默认存根 | 未启用 `litellm` profile 时使用 StubChatProvider；生产推荐 LiteLLM | ⚠️ 可配置 |
| 支付存根 | 支付提供者为 Noop | 🔴 阻塞 |
| 内存存储 | 提示词模块使用内存存储 | ⚠️ 部分 |
| 沙箱占位符 | Wasm 运行时未实现 | 📋 未来 |
| 联邦查询存根 | 查询网关为存根 | ⚠️ 部分 |

---

## 故障排除

**Q: 后端无法启动**
1. 检查端口 8080 是否被占用: `lsof -i :8080`
2. 检查数据库连接
3. 运行 `./gradlew clean test` 重置

**Q: 前端无法连接后端**
1. 检查代理配置 `vite.config.ts`
2. 确认后端运行在 8080 端口
3. 检查 CORS 配置

**Q: 测试失败**
1. 运行 `./gradlew clean test`
2. 检查 PostgreSQL 连接配置
3. 查看测试报告 `build/reports/tests/test/index.html`
