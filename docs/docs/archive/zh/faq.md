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

## 已知限制

| 限制 | 说明 | 计划 |
|------|------|------|
| 无用户认证 | 仅支持 API 密钥认证 | 🔴 阻塞 |
| AI 存根 | 使用 StubChatProvider | 🔴 阻塞 |
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
2. 检查 H2 内存数据库配置
3. 查看测试报告 `build/reports/tests/test/index.html`
