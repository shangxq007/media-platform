# 提示词工程管理平台

> **最后更新:** 2026-05-14

---

## 概述

提示词工程管理平台提供全面的 AI 提示词模板生命周期管理，包括创建、版本控制、变量校验、渲染预览、执行跟踪、风险评估和审计。

---

## 模板管理

### 状态流转
```
DRAFT → ACTIVE → DEPRECATED → ARCHIVED
  │        │          │
  └────────┴──────────┘
      (可回滚到任意版本)
```

### 创建模板
```bash
curl -X POST /api/v1/prompts/templates \
  -d '{"name": "视频摘要", "category": "video", "schemaVersion": "1.0.0"}'
```

### 版本管理
```bash
# 创建新版本
curl -X POST /api/v1/prompts/templates/{id}/versions \
  -d '{"templateBody": "请总结视频内容：{{video_url}}", "changelog": "初始版本"}'

# 版本对比
curl -X POST /api/v1/prompts/templates/{id}/diff \
  -d '{"versionId1": "v1", "versionId2": "v2"}'

# 回滚
curl -X POST /api/v1/prompts/templates/{id}/rollback \
  -d '{"targetVersion": "1.0.0"}'
```

---

## 变量 Schema

### 变量类型

| 类型 | 说明 | 示例 |
|------|------|------|
| STRING | 字符串 | `{{name}}` |
| NUMBER | 数字 | `{{count}}` |
| BOOLEAN | 布尔值 | `{{enabled}}` |
| ENUM | 枚举值 | `{{format: "mp4"\|"webm"}}` |
| ARRAY | 数组 | `{{tags}}` |
| OBJECT | 对象 | `{{config}}` |
| SECRET_REFERENCE | 密钥引用 | `{{api_key}}` |
| FILE_REFERENCE | 文件引用 | `{{video_file}}` |

### 敏感变量
标记为 `sensitive=true` 的变量：
- 渲染输出中自动脱敏（显示为 `[REDACTED]`）
- 执行记录中存储哈希值而非明文
- 支持多种脱敏策略：FULL、PARTIAL、HASH、MASK_LAST_FOUR

---

## 渲染预览

```bash
curl -X POST /api/v1/prompts/templates/{id}/render \
  -d '{
    "variables": {"name": "世界", "platform": "媒体平台"},
    "dryRun": true
  }'

# 响应:
# {
#   "renderedPrompt": "你好 世界！欢迎来到 媒体平台。",
#   "redactedPrompt": "你好 世界！欢迎来到 媒体平台。",
#   "missingVariables": [],
#   "warnings": []
# }
```

---

## 安全扫描

### 自动检测

| 检测类型 | 模式 | 风险等级 |
|----------|------|----------|
| API 密钥 | `sk-[a-zA-Z0-9]{20,}` | 严重 |
| 密码 | `password: xxx` | 严重 |
| AWS 密钥 | `AKIA[0-9A-Z]{16}` | 严重 |
| GitHub 令牌 | `ghp_[a-zA-Z0-9]{36}` | 严重 |
| 破坏性命令 | `rm -rf /` | 严重 |
| 生产环境操作 | `production + deploy` | 高 |

### 风险等级与动作

| 等级 | 动作 | 说明 |
|------|------|------|
| 低 | ALLOW | 安全内容，正常执行 |
| 中 | WARN | 轻微问题，可继续但记录警告 |
| 高 | REQUIRE_REVIEW | 需要人工复核后才能执行 |
| 严重 | BLOCK | 自动阻止执行 |

---

## 执行与审计

### 执行记录
每次提示词执行都会记录：
- 执行 ID、模板 ID、版本号
- 租户 ID、用户 ID
- 渲染提示词哈希（非明文）
- 输入变量（敏感变量脱敏）
- 输出摘要
- 状态（PENDING → RUNNING → SUCCEEDED/FAILED）
- 风险等级
- Token 估算、成本估算

### 评估
```bash
curl -X POST /api/v1/prompts/executions/{id}/evaluate \
  -d '{
    "evaluatorUserId": "reviewer-1",
    "acceptanceCriteriaMet": true,
    "documentationUpdated": true,
    "manifestUpdated": true,
    "testsPass": true,
    "hasHighRiskChanges": false,
    "hasHumanReviewItems": false,
    "hasScopeCreep": false,
    "hasFalseClaims": false
  }'
```

---

## 文件扫描与导入

### 扫描提示词文件
```bash
curl -X POST /api/v1/prompts/files/scan \
  -d '{"fileContents": ["---\nname: 测试\n---\nHello {{name}}"], "fileNames": ["test.md"]}'
```

### 导入文件
```bash
curl -X POST /api/v1/prompts/files/import \
  -d '{"content": "---\nname: 测试提示词\ncategory: test\n---\n你好 {{name}}！", "fileName": "test.md", "owner": "user-1"}'
```

---

## MANIFEST 一致性

平台维护 `prompts/MANIFEST.md` 记录：
- 所有提示词模板状态
- 版本历史
- 执行统计
- 缺口报告
- 人工复核点

```bash
curl -X POST /api/v1/prompts/manifest/validate \
  -d '{"prompts": {}}'
```
