# 使用方式

> **最后更新:** 2026-05-14

---

## 本地开发

### 环境要求
- Java 25+
- Node.js 22+ / npm
- Gradle 9.1.0+

### 启动后端
```bash
cd media-platform
./gradlew :platform-app:bootRun
# API: http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
# H2 Console: http://localhost:8080/h2-console
```

### 启动前端
```bash
cd frontend
npm install
npm run dev
# http://localhost:3000
```

### 运行测试
```bash
# 所有测试
./gradlew test

# 单个模块测试
./gradlew :render-module:test

# 基础设施验证
bash scripts/infra-validate.sh
```

---

## Docker 本地测试

### 启动所有服务
```bash
docker compose up -d
```

### 服务列表

| 服务 | 端口 | 用途 |
|------|------|------|
| platform-app | 8080 | 后端 API |
| frontend | 3000 | 前端 UI |
| postgresql | 5432 | 数据库 |
| redis | 6379 | 缓存 |
| minio | 9000/9001 | 对象存储 |
| temporal | 7233 | 工作流引擎 |
| temporal-ui | 8088 | Temporal 控制台 |
| prometheus | 9090 | 指标收集 |
| grafana | 3001 | 监控面板 |
| sentry | 9000 | 错误监控 |

---

## 远程工作者 (Remote Worker)

### 启动远程工作者
```bash
cd media-platform
./gradlew :remote-render-worker:bootRun \
  -Dserver.port=8090 \
  -Dapp.render.execution.mode=remote
```

### 注册工作者
```bash
curl -X POST http://localhost:8090/api/v1/remote-worker/register \
  -H "Content-Type: application/json" \
  -d '{
    "workerId": "worker-001",
    "capabilities": ["h264", "h265", "4k", "subtitle"],
    "maxConcurrentJobs": 4
  }'
```

### 工作者状态查询
```bash
curl http://localhost:8080/api/v1/remote-worker/workers \
  -H "X-Tenant-ID: tenant-1"
```

---

## GPU 渲染

### 可用 GPU 预设

| 预设 | 编码器 | 分辨率 | 层级 |
|------|--------|--------|------|
| GPU_H264 | NVENC H.264 | 1920x1080 | 团队+ |
| GPU_H265 | NVENC HEVC | 1920x1080 | 团队+ |
| GPU_VP9 | VAAPI VP9 | 1920x1080 | 团队+ |

### 提交 GPU 渲染任务
```bash
curl -X POST http://localhost:8080/api/v1/render/jobs \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-1" \
  -H "X-API-Key: your-api-key" \
  -d '{
    "projectId": "project-1",
    "profile": "gpu_h264",
    "format": "mp4",
    "workerType": "remote"
  }'
```

---

## 渲染流水线 (RenderPipeline)

### 流水线阶段
1. **特效 (Effects)** - OFX 特效、过渡、滤镜
2. **转码 (Transcode)** - JavaCV/FFMPEG 转码
3. **打包 (Packaging)** - GPAC DASH/HLS 打包

### 提交渲染任务
```bash
curl -X POST http://localhost:8080/api/v1/render/jobs \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-1" \
  -d '{
    "projectId": "project-1",
    "profile": "default_1080p",
    "format": "mp4",
    "audioTrack": "all",
    "frameRate": 30,
    "encoder": "h264"
  }'
```

### 查询任务状态
```bash
curl http://localhost:8080/api/v1/render/jobs/{jobId} \
  -H "X-Tenant-ID: tenant-1"
```

### 导出前验证
```bash
curl -X POST http://localhost:8080/api/v1/render/export/validate \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-1" \
  -d '{"preset": "default_1080p", "outputFormat": "mp4"}'
```

---

## 提示词平台

### 创建模板
```bash
curl -X POST http://localhost:8080/api/v1/prompts/templates \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-1" \
  -d '{"name": "我的模板", "category": "custom", "schemaVersion": "1.0.0"}'
```

### 创建版本
```bash
curl -X POST http://localhost:8080/api/v1/prompts/templates/{id}/versions \
  -H "Content-Type: application/json" \
  -d '{
    "templateBody": "你好 {{name}}！欢迎来到 {{platform}}。",
    "variableSchemaJson": "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"platform\":{\"type\":\"string\"}}}",
    "changelog": "初始版本",
    "createdBy": "user-1"
  }'
```

### 渲染预览
```bash
curl -X POST http://localhost:8080/api/v1/prompts/templates/{id}/render \
  -H "Content-Type: application/json" \
  -d '{"variables": {"name": "世界", "platform": "媒体平台"}, "dryRun": true}'
```

### 风险分析
```bash
curl -X POST http://localhost:8080/api/v1/prompts/risk/analyze \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-1" \
  -d '{"content": "你好 {{name}}", "category": "general"}'
```

---

## 监控与反馈

### Sentry 集成
- 前端和后端异常自动上报
- Session Replay 会话录制
- 敏感数据自动脱敏（API 密钥、密码、令牌）
- 默认关闭，通过环境变量启用：
  ```bash
  SENTRY_DSN=https://xxx@yyy.ingest.sentry.io/zzz
  SENTRY_ENABLED=true
  ```

### OpenReplay 集成
- 用户主动反馈（FeedbackButton 组件）
- 会话录制与回放
- 输入字段自动脱敏
- 通过环境变量配置：
  ```bash
  VITE_OPENREPLAY_PROJECT_KEY=your-project-key
  OPENREPLAY_ENABLED=true
  ```

### 用户反馈
1. 点击右下角"反馈"按钮
2. 选择类型（Bug/功能建议/其他）
3. 选择严重程度（低/中/高/严重）
4. 填写标题和描述
5. 提交后自动关联 Sentry 回放 ID
