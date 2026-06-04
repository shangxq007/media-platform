# 模块包结构

> **最后更新：** 2026-05-18

## 标准模块布局

每个业务模块遵循以下结构：

```
<module-name>/
├── src/main/java/com/example/platform/<module>/
│   ├── package-info.java              # @ApplicationModule 注解
│   ├── api/                           # 公共边界
│   │   ├── <Module>Controller.java
│   │   ├── dto/
│   │   │   ├── RequestDto.java
│   │   │   └── ResponseDto.java
│   │   └── package-info.java          # @NamedInterface("API")
│   ├── app/                           # 应用服务
│   │   ├── <Module>Service.java
│   │   └── <Module>Orchestrator.java
│   ├── domain/                        # 领域模型
│   │   ├── <Entity>.java
│   │   ├── <ValueObject>.java
│   │   ├── <DomainEvent>.java
│   │   └── package-info.java          # @NamedInterface("domain")
│   ├── spi/                           # 端口接口
│   │   └── <Module>Port.java
│   └── infrastructure/                # 适配器
│       ├── <Adapter>.java
│       └── repository/
│           └── <Entity>Repository.java
├── src/main/resources/
│   └──（模块特定资源）
├── src/test/java/com/example/platform/<module>/
│   ├── api/
│   ├── app/
│   └── infrastructure/
└── build.gradle.kts
```

## package-info.java 示例

```java
@ApplicationModule(
    displayName = "Render",
    allowedDependencies = {
        "ai", "ai :: API", "ai :: domain",
        "shared",
        "storage", "storage :: API", "storage :: domain"
    }
)
package com.example.platform.render;

import org.springframework.modulith.ApplicationModule;
```

## 已知不一致项

| 问题 | 位置 | 修复优先级 |
|------|------|-----------|
| Repository 类在 `app/` 包中 | `render-module` | 中 |
| `RenderModule` 缺少根 `package-info.java` | `render-module` | 低 |
| `GStreamerCommandFactory` 是 `@Component` 而其他是普通类 | `render-module` | 低 |
| `JavaCVMediaProbeAdapter` 具体注入 | `render-module` | 低 |
| `OpenTimelineioAdapter` 是占位符 | `render-module` | 中 |

参阅 `12-review/02-technical-debt.md` 获取完整详情。
