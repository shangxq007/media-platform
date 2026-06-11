# Public API Security

> **Last Updated:** 2026-06-11
> **Status:** Design

## 概述

本文档定义 Render Platform 对外 API 的安全要求。

## 威胁模型

| 威胁 | 描述 | 防护措施 |
|------|------|----------|
| 恶意文件上传 | 上传可执行文件 / 恶意字体 | 文件 quarantine、安全扫描 |
| SSRF | 通过 URL 参数访问内网 | URL allowlist、签名 URL |
| 命令注入 | 通过输入注入 shell 命令 | schema validation、禁止裸命令 |
| 资源耗尽 | 超大文件 / 超长视频 | max file size、max duration、quota |
| 未授权访问 | 跨租户数据访问 | tenant isolation、auth token |
| Webhook 伪造 | 伪造回调请求 | webhook signing |

## 安全要求

### 1. 文件上传 Quarantine

- 所有上传文件先进入 quarantine 状态
- 安全扫描通过后才能进入后续处理
- 扫描失败的文件进入 SECURITY_REJECTED
- Quarantine 文件不对外暴露

### 2. 字体文件安全扫描

- 上传字体必须经过 BasicFontSecurityScanner
- OTS sanitizer（如启用）检查 OpenType 格式安全
- 不把用户字体直接交给 Remotion / Chromium
- 子集化在隔离环境中执行

### 3. 视频 Duration / Codec Probe

- 上传视频必须 probe duration / codec
- 超过 max duration 拒绝处理
- 不支持的 codec 返回明确错误

### 4. MIME Sniffing

- 不信任客户端提供的 Content-Type
- 通过 magic bytes 检测真实类型
- 不匹配时拒绝处理

### 5. Schema Validation

- 所有输入必须通过 Zod / JSON Schema 验证
- 拒绝未知字段
- 严格类型检查

### 6. URL Allowlist / Signed URL

- 外部 URL 必须在 allowlist 中
- 预签名 URL 带过期时间
- 禁止任意外部 URL 拉取

### 7. 禁止 SSRF

- 内部地址（127.0.0.1, 169.254.x.x, 10.x.x.x）不可访问
- DNS rebinding 防护

### 8. 禁止任意命令执行

- 用户输入不直接传递给 shell / exec
- 所有命令通过 allowlist 构造
- 参数必须转义

### 9. 禁止任意脚本执行

- 不允许用户上传 JS / Python / shell script
- 模板系统只允许预定义模板
- 参数通过 schema validation 传递

### 10. Worker Sandbox

- 渲染 worker 运行在隔离环境中
- 网络访问受限
- 文件系统只读（除工作目录）

### 11. Rate Limit

- 按 tenant / API key 限流
- 不同 API 独立限流
- 超限返回 429

### 12. Quota

- 按 tenant 设置配额
- 包括：存储、渲染时长、并发 job 数
- 超限返回 402

### 13. Max File Size

- 默认最大文件大小：1GB
- 可配置 per-tenant
- 超大文件返回 413

### 14. Max Video Duration

- 默认最大时长：1 小时
- 可配置 per-tenant
- 超长视频返回 400

### 15. Artifact Retention

- 默认保留 7 天
- 可配置 per-tenant
- 过期自动删除

### 16. Webhook Signing

- Webhook 回调使用 HMAC-SHA256 签名
- 签名密钥 per-tenant
- 接收方验证签名

### 17. Tenant Isolation

- 所有资源按 tenant 隔离
- 跨 tenant 访问返回 404（不暴露存在性）
- 数据库查询强制 tenant filter

### 18. Audit Logs

- 所有 API 调用记录审计日志
- 包括：操作、租户、时间、结果
- 日志不可篡改

## 相关文档

- [Public Capability API](./public-capability-api.md)
- [API Productization Roadmap](./api-productization-roadmap.md)
