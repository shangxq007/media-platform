# Module Package Structure

> **Last Updated:** 2026-05-18

## Standard Module Layout

Each business module follows this structure:

```
<module-name>/
├── src/main/java/com/example/platform/<module>/
│   ├── package-info.java              # @ApplicationModule annotation
│   ├── api/                           # Public boundary
│   │   ├── <Module>Controller.java
│   │   ├── dto/
│   │   │   ├── RequestDto.java
│   │   │   └── ResponseDto.java
│   │   └── package-info.java          # @NamedInterface("API")
│   ├── app/                           # Application services
│   │   ├── <Module>Service.java
│   │   └── <Module>Orchestrator.java
│   ├── domain/                        # Domain model
│   │   ├── <Entity>.java
│   │   ├── <ValueObject>.java
│   │   ├── <DomainEvent>.java
│   │   └── package-info.java          # @NamedInterface("domain")
│   ├── spi/                           # Port interfaces
│   │   └── <Module>Port.java
│   └── infrastructure/                # Adapters
│       ├── <Adapter>.java
│       └── repository/
│           └── <Entity>Repository.java
├── src/main/resources/
│   └── (module-specific resources)
├── src/test/java/com/example/platform/<module>/
│   ├── api/
│   ├── app/
│   └── infrastructure/
└── build.gradle.kts
```

## package-info.java Example

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

## Known Inconsistencies

| Issue | Location | Fix Priority |
|-------|----------|-------------|
| Repository classes in `app/` package | `render-module` | Medium |
| `RenderModule` missing root `package-info.java` | `render-module` | Low |
| `GStreamerCommandFactory` is `@Component` while others are plain | `render-module` | Low |
| `JavaCVMediaProbeAdapter` concrete-injected | `render-module` | Low |
| `OpenTimelineioAdapter` is placeholder | `render-module` | Medium |

See `12-review/02-technical-debt.md` for full details.
