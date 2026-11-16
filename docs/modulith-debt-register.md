# Spring Modulith 技术债登记

> **预算:** `0`（`ModularityTest` 要求零意外违规）
> **测试:** `platform-app/src/test/java/com/example/platform/ModularityTest.java`
> **Last Validated:** 2026-11-17（MEDIA_PLATFORM_MODULITH_CONTRACT_CLOSURE_R1）

## 策略

- CI 在**精确声明**的 `@ApplicationModule` / `@NamedInterface` / `allowedDependencies` 之上断言零意外违规。
- 新增跨模块依赖必须显式声明；不得用 `filter()`、宽泛豁免或开放整模块掩盖。
- 新违规必须失败；仅保留 graph kernel 的 3 条精确豁免（见下）。

## 当前状态（本轮实测）

- **ModularityTest:** PASS
- **ACTIVE_ALLOWED_VIOLATIONS:** **3**
- **STALE_RULES_REMOVED:** **7**（AV-008/015/022/028/031/032/033，均 0 匹配）
- **CONTRACT_RULES_REPLACED:** **23**（AV-001…029 中的契约项，全部替换为正式声明）
- **意外违规:** 0
- **依赖环:** 0
- **KNOWN_ILLEGAL_WEB_TO_TIMELINE_ADAPTER:** RESOLVED（ProjectDashboardController 不再引用
  `timeline.adapter.TimelineRevisionRepository`；该 Repository 未作为公共契约暴露）
- **MODULITH_GATE:** PASS

> 历史记录：2026-08-19 的状态（1 641 条总违规、39 条 pattern）已被本轮取代，仅为历史身份保留。

## 当前唯一保留的豁免（graph kernel）

| 规则 | 范围 | Owner | 理由 | 退出条件 |
|------|------|-------|------|----------|
| `workflow' depends on module 'graph` | workflow → graph.api/result（3 条边） | Backend / Architecture | `platform-algorithms:graph` 是刻意的非 Spring 纯算法内核，未加 `@ApplicationModule` | 未来以对等契约或等价的准确模块模型替代三条规则，且不污染 graph 内核 |
| `render' depends on module 'graph` | render → graph.api/result（9 条边） | Backend / Architecture | 同上 | 同上 |
| `execution' depends on non-exposed type com.example.platform.graph` | execution → graph.api/result（7 条边） | Backend / Architecture | 同上 | 同上 |

保留规则文本与本轮审计前完全一致，未放宽匹配范围。

## TypedSchema Generated Records

```text
DECISION:  PACKAGE_LEVEL_NAMED_INTERFACE
SCOPE:     CURRENT_APPROVED_CONSUMERS_ONLY（artifact / ingest / media / outbox / persistence）
RISK:      GENERATED_PERSISTENCE_TYPES_SHARED_ACROSS_MODULES
FOLLOW_UP: ONLY_IF_ACTUAL_MAINTENANCE_PROBLEMS_APPEAR
```

声明放在生成器免疫的 `typed-schema-module/src/main/modulith/java` 源根
（与既有 `jooq-tables` 接口同一约定，JOOQ 再生成不会删除）；未修改生成的 Records 源文件。

## 验证

- `ModularityTest`：PASS，0 unexpected，0 cycles，3 条规则。
- 负例 1：移除 `artifact` 的 `typedschema :: records` 声明 → 测试 FAIL（3 条消息）。
- 负例 2：让 `web` 控制器重新引入 `timeline.adapter.TimelineRevisionRepository` → 测试 FAIL
  （`web → timeline` 违规）。
- 完整证据与本轮 Closeout：`ModulithContractClosureR1.md`（仓库外审计/收尾目录）。
