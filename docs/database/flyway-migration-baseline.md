# PostgreSQL18 canonical greenfield baseline

## 未上线阶段政策

**Media Platform 尚未正式上线期间，活动 Flyway 迁移目录原则上只保持一个 `V1__baseline.sql`。**
这是当前阶段的开发政策，不是正式上线后永久禁止追加 Migration，也不是对 SQL 或数据库操作的授权。

后续经授权的未上线 Schema 变更原则上整合进新 V1，并重新完成：

- 全新空库 Flyway 验证，不能用旧库上的 migrate=0 替代。
- Schema 一致性检查，解释并解决有效业务结构差异。
- jOOQ 重新生成及一致性验证，按项目策略同步已版本化的生成源码。
- 必要的构建、测试和业务回归。
- 迁移清单、完整摘要及相关保护机制更新。

不得将旧 V1 的历史验收证据冒充新 V1 的验收，也不得未经授权改写已执行过迁移的数据库历史。
正式上线并需保留生产数据后，必须重新确定迁移治理政策，保护已发布迁移的不可变性。

## 当前已验收身份

路径：`platform-app/src/main/resources/db/migration/V1__baseline.sql`。
SHA-256：`1af0089e27dafe8df2def9ed255cab18ba78073827ee7888dc9e542909e932d9`。
该身份已经通过与旧 V1+V2 的业务 Schema 等价验证；此为既有基线事实，不表示原则整理任务重新执行了验收。

The active migration directory contains only `V1__baseline.sql`. It consolidates the accepted
V1+graph-execution V2 semantics for this unreleased project. New databases must start empty; no
historical production-data compatibility or fake upgrade path is maintained. Historical frozen
migration bytes and prior checksums remain untouched in external audit snapshots.

The baseline includes `render_graph_execution`, composite Render/Artifact scope constraints,
platform attempt/generation binding, native task/attempt/generation FK and final-output equality
checks. It adds no duplicate job status, Artifact commit authority or native execution completion.

The Gradle canonical baseline guard pins the current full V1 SHA-256. Exact one-V1 Flyway inventory,
full fresh-PG18 business DDL comparison with the accepted V1+V2, jOOQ3.21.9 byte parity and real database
business tests verify consolidation. Only Flyway bookkeeping, dump random delimiters and distinct
synthetic owner/ACL metadata are excluded; no business Schema difference is ignored.

See [development and regeneration](../development/canonical-baseline.md) and
[First Render ownership](../testing/first-render.md). Never rewrite an applied database's history.

日常工程指导见[功能驱动总则](../engineering/functional-development-and-module-principles.md)；
本文件保持数据库阶段政策的唯一专项入口。
