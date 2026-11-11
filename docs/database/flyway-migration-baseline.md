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

## 当前开发基线

Owner R1E 删除已无正式消费者的旧出站交付、Render Worker/Lease/Queue 和 Pipeline/AI/URI/旧 Provider 字段；正式图执行、复用及 Delivery 权威保持不变。须重新完成空 PG18、真实 jOOQ 生成与产品回归。

路径：`platform-app/src/main/resources/db/migration/V1__baseline.sql`。
完整 SHA-256：`4e8a539025794c45b67964407e5270ef48606296ad65d77c90db32e0c2278450`。

Owner R1D 增加仅保存授权交付选择的不可变 Delivery 表、同作用域外键及幂等唯一性；不改变 Artifact 身份或生命周期。须通过新鲜 PG18 和 jOOQ 一致性验证。

Owner R1C 在 RenderJob 增加同租户、同项目的不可变增量参考 Job 外键；保留原 Job 生命周期与 Artifact 权威。该新基线须经全新空 PG18、完整 jOOQ 生成和实际增量回归。

Owner OD1–OD3 授权在未上线单一 V1 内移除无消费者的 Provider Candidate Snapshot 表和
Cover/Thumbnail 专用任务表，并由现有 Execution admission/result 持久化承接能力请求。
该基线也包含 Render preparation 的阶段/截止时间、既有补偿恢复，以及逻辑 Execution 与
原生 task/attempt/generation 的复合关联约束。它没有创建第二套 Artifact 或 Render 完成权威。

这是有意的 Schema 演进，不能声称与首次集成的旧 V1 结构完全相同。
历史 `1af0089e…` 曾完成旧 V1+V2 等价验收；该事实只属于其当时的冻结源码。
当前源码必须重新通过真实空 PG18 的单 V1、完整约束检查、jOOQ3.21.9 生成/编译和对应回归，
以本次源码身份和执行日志为依据，不沿用旧 PASS 或固定生成文件数量。

jOOQ 生成源码按仓库策略版本化；只从真实 PG18 introspection 输出更新，必须逐字节对账，
不得手写补齐生成类。Gradle Schema Guard 绑定当前完整 V1 摘要，迁移清单只允许单一 V1。
现有数据库历史、历史快照和审计包不随本任务改写。

See [development and regeneration](../development/canonical-baseline.md) and
[First Render ownership](../testing/first-render.md). Never rewrite an applied database's history.

日常工程指导见[功能驱动总则](../engineering/functional-development-and-module-principles.md)；
本文件保持数据库阶段政策的唯一专项入口。
