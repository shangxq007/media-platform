# Spring Modulith 技术债登记

> **预算:** `0`（`ModularityTest` 要求零违规）  
> **测试:** `platform-app/src/test/java/com/example/platform/ModularityTest.java`

## 策略

- CI 在 **未过滤** 的 `ApplicationModules.detectViolations()` 上断言 `!hasViolations()`。
- 新增跨模块依赖须通过 `@NamedInterface` + `allowedDependencies` 显式暴露，禁止 `filter()` 掩盖。
- `secrets.api.port`、`render` 子包、`commerce`/`billing`/`entitlement` 命名接口已补齐（2026-05-23）。

## 典型违规类型（待消除）

| 类型 | 示例 | 修复方向 |
|------|------|----------|
| 模块依赖 | `render` → `billing` / `entitlement` / `extension` | 端口接口、`shared-kernel` 事件、render-api 包 |
| 非暴露类型 | `web` → `render` / `prompt` / `identity` 内部类 | `@NamedInterface` + 仅依赖 api 包 |
| 聚合穿透 | `app` / `security` → `identity` 实现类 | Controller 只调 identity `api` |

## 关联文档

- [module-boundaries.md](module-boundaries.md)
- [layering-and-open-source.md](layering-and-open-source.md)
