# Spring Boot 4、Spring Modulith 2 与周边依赖

> 文档索引：[docs/README.md](./README.md)。

本文说明：**本仓库当前基线**、**Modulith 与 Boot 4 的版本对应**、以及升级或 bump 其它依赖时的注意点。撰写时请以你升级当日的官方发行说明为准。

---

## 1. 本仓库当前基线（已落地）

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Boot | **4.0.4**（GA） | 根 `build.gradle.kts` BOM + `org.springframework.boot` 插件 |
| Spring Modulith | **2.0.4**（GA） | `spring-modulith-starter-core` / `starter-insight` / `starter-test`；`spring-modulith-api` 全模块 `compileOnly` |
| springdoc OpenAPI | **3.0.2** | Boot 4 需 **3.x**；2.8.x 面向 Boot 3 |
| Spring AI | **spring-ai-bom:2.0.0-M3** | **Milestone**：Central 上尚无与 Boot 4 配套的 Spring AI **GA** BOM；生产需自行评估 |
| Temporal | **temporal-spring-boot-starter:1.33.0** | 与 Boot 4 试跑通过（以 `./gradlew test` 为准） |
| LiteFlow | **2.15.3.2** | 第三方自动配置；升级 Boot 大版本后应回归启动与规则链 |
| PF4J | **3.15.0** | 与 Spring 耦合弱 |

运行环境：[Spring Boot 4.0 系统要求](https://docs.spring.io/spring-boot/4.0/system-requirements.html)（**Java 17～26**）；本仓库 **Java 25** + **Gradle 9.1**。

---

## 2. Spring Modulith 与 Spring Boot 4 用哪条线？

| Modulith 线 | 典型 Spring Boot | 说明 |
|-------------|------------------|------|
| **1.4.x** | **3.5.x** | 维护线；**不面向 Boot 4**。 |
| **2.0.4**（GA） | **4.0.4**（与本仓库一致） | `spring-modulith-starter-core:2.0.4` 的 POM 依赖 **`spring-boot-starter:4.0.4`**。 |
| **2.1.x** | **4.0.x**（演进） | [Spring Modulith 2.1 参考文档](https://docs.spring.io/spring-modulith/reference/2.1/index.html) 与 Boot 4 同世代；Central 上可能仍有 **Milestone**，升级前核对 GA。 |

**结论**：在要求 **Boot 4 + Modulith 均为正式版** 的前提下，**Spring Boot 4.0.4 + Spring Modulith 2.0.4** 是当前可行且已在 CI 构建中验证的组合。

---

## 3. 其它依赖 bump 原则

**不要无差别全部升到 Central `<latest>`**；应按 **Boot 4 / Spring Framework 7** 兼容性分批核对。

| 依赖 | 注意点 |
|------|--------|
| **springdoc** | 保持 **3.x**；参见 [springdoc 与 Boot 4 相关讨论](https://github.com/springdoc/springdoc-openapi/issues)。 |
| **Spring AI** | GA **1.1.x** 面向 Boot 3.4/3.5；Boot 4 需 **2.x** 路线，目前多为 **Milestone**。 |
| **Temporal / LiteFlow** | 以发行说明 + 本地/CI 启动与集成测试为准。 |
| **Flyway / JDBC / jOOQ** | 由 **Spring Boot BOM** 与驱动版本共同约束。 |

---

## 4. 推荐升级顺序（其它分支或 fork 参考）

1. 将 **Spring Boot** 升到目标 **4.0.x GA**，修复编译与 **`ModularityTest`**。
2. 将 **Spring Modulith** 升到与 Boot 4 对齐的 **2.0.4**（或官方后续 GA）。
3. 将 **springdoc** 升到 **3.x**。
4. 将 **Spring AI BOM** 升到与 Boot 4 兼容的 **2.x**（关注里程碑风险）。
5. 单独验证 **Temporal、LiteFlow**。
6. 全量 **`./gradlew test`** 与关键流程冒烟。

---

## 5. 维护

升级完成后请更新：`build.gradle.kts`、`platform-app/build.gradle.kts`、`README.md`、`architecture-notes.md`、`layering-and-open-source.md` 与本文件中的版本号。
