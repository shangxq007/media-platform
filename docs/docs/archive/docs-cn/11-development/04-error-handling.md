# 错误处理设计

> **最后更新：** 2026-05-18

## 错误响应格式

所有后端错误返回 JSON：

```json
{
  "errorCode": "RENDER-409-001",
  "message": "租户 tenant-123 配额已用完",
  "details": {
    "tenantId": "tenant-123",
    "featureCode": "render.1080p",
    "limit": 60,
    "used": 60
  },
  "timestamp": "2026-05-18T10:00:00Z"
}
```

## 错误代码格式

```
{模块}-{HTTP 状态码}-{序号}
```

## 错误代码注册表

错误代码定义在 `shared-kernel/src/main/resources/error-codes.json` 中：

```json
{
  "RENDER-409-001": {
    "numericCode": 409001,
    "messages": {
      "en": "Quota exceeded",
      "zh": "配额已用完"
    },
    "module": "render",
    "status": 409
  }
}
```

## 错误代码模块

| 模块 | 前缀 | 数量 |
|------|------|------|
| 通用 | `COMMON-` | — |
| 渲染 | `RENDER-` | — |
| 字幕 | `SUBTITLE-` | — |
| 特效 | `EFFECT-` | — |
| 时间轴 | `TIMELINE-` | — |
| 迁移 | `MIGRATION-` | — |
| 权益 | `ENTITLEMENT-` | 7 |
| Feature Flag | `FF-` | 13 |
| NLQ | `NLQ-` | 11 |
| 监控 | `MONITORING-` | 2 |
| 反馈 | `FEEDBACK-` | 2 |
| **总计** | | **60+** |

## 异常层次结构

```
RuntimeException
└── PlatformException
    ├── errorCode: ErrorCode
    ├── details: Map<String, Object>
    └── locale: String
```

## 全局异常处理器

| 异常类型 | HTTP 状态码 | 错误代码 |
|----------|------------|----------|
| `PlatformException` | 来自 errorCode | 来自异常 |
| `IllegalArgumentException` | 400 | `COMMON-400-001` |
| `IllegalStateException` | 409 | `COMMON-409-001` |
| `Exception` | 500 | `COMMON-500-001` |

## i18n 支持

- 后端：`ErrorCodeRegistry` 从 `error-codes.json` 加载消息
- 前端：`useI18nError()` 组合式函数提供 `t(errorCode)`
- 区域检测：`Accept-Language` 请求头
- 支持语言：英语（en）、中文（zh）
