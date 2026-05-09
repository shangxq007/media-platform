# 通知技术选型：Novu 与备选方案

> **文档导航**：[docs/README.md](./README.md)。与 `notification-module` 的 **provider SPI**（`NotificationProvider`）及「对规范领域事件反应、不对原始支付 webhook 反应」的约定，见 [architecture-notes.md](./architecture-notes.md)。

---

## 1. 定位

本平台的 **`notification-module`** 负责：领域事件 → 模板与变量 → 渠道路由 → 实际投递（邮件 / 短信 / Webhook 等），并通过 **`NotificationProvider`** 将具体厂商隔离在 `infrastructure`。

**结论：可以使用 Novu。** 它适合作为 **可选的外部编排层**：统一工作流、多通道、模板与订阅偏好；实现上建议做成 **`novu-adapter`（或 REST 客户端封装）**，由应用侧在收到 Outbox / 领域事件后调用 Novu Trigger API（或等价接口），**不**把 Novu 当作领域真相来源。与 Kill Bill 等一样：**平台内仍保留投递记录、幂等与审计**；Novu 负责「何时、走哪条工作流、发到哪些通道」的可视化与运营能力（若采用其云或自托管控制台）。

---

## 2. 推荐选型（与仓库现状对齐）

| 层级 | 建议 | 说明 |
|------|------|------|
| **默认/骨架路径** | 继续 **`NotificationProvider` 多实现**（如 Email / SMS / Webhook 直连厂商 SDK 或 HTTP） | 依赖面小、易测；与当前模块划分一致。 |
| **需要统一工作流与多通道运营** | **Novu**（自托管或 Novu Cloud）或下表同类 **Customer Engagement / Notification Infrastructure** | 通过适配器接入，领域事件与模板 ID 映射留在本仓库。 |
| **重投递与补偿** | 已有 **Temporal** 时，可将「重试、退避、对账」放在 Workflow/Activity 中，底层仍调 SPI 或 Novu | 避免在 Novu 与本地 DB 之间出现两套互不知晓的重试语义；需明确谁为「投递状态」主记录。 |

---

## 3. Novu 简评

| 维度 | 说明 |
|------|------|
| **能力** | 工作流、多通道（邮件、短信、应用内、Push 等）、触发器（Trigger）、用户/Topic、首选项；开源可自托管。 |
| **与本项目契合度** | 高：可作为 **`NotificationProvider` 的后端之一**，或并行「关键通道直连 + 非关键走 Novu」。 |
| **注意** | 自托管需运维其栈；云版本需评估数据驻留与供应商锁定；Java 侧多为 **HTTP API 集成**，版本与限流要在适配器层处理。 |

官方参考：[Novu](https://novu.co/) / [GitHub: novuhq/novu](https://github.com/novuhq/novu)。

---

## 4. 备选方案（按类型）

### 4.1 类 Novu：统一触发 + 工作流 + 多通道

| 方案 | 侧重点 | 备注 |
|------|--------|------|
| **Knock** | 产品化工作流、跨通道、Dashboard | 商业为主；适合「运营可配流程」团队。 |
| **Courier** | 统一 API 聚合多家 ESP/短信/Push | 偏「路由到已有供应商」，与直连 Twilio+SendGrid 相比减少集成数量。 |
| **MagicBell** | 应用内通知 + 多通道 | 若 in-app 通知中心是核心场景可评估。 |

### 4.2 偏单一通道或大规模推送

| 方案 | 侧重点 | 备注 |
|------|--------|------|
| **OneSignal** | Push、邮件等，规模大 | 适合移动端 Push 为主；与领域模板体系需自己做映射层。 |
| **Firebase Cloud Messaging (FCM)** | Android/Web Push | 常作为 `NotificationProvider` 的一个实现。 |
| **AWS SNS / SES / Pinpoint** | 云原生、与 AWS 生态绑定 | 适合已选 AWS 的团队；Pinpoint 带部分编排能力。 |

### 4.3 事务邮件 / 营销自动化（与「事务通知」重叠需谨慎）

| 方案 | 侧重点 | 备注 |
|------|--------|------|
| **Customer.io**、**Braze** 等 | 生命周期营销 + 部分事务能力 | 与「订单/权益变更」类事务通知可并存，但要分清 **canonical 事件源** 仍在本平台（见 architecture-notes）。 |

### 4.4 不引入第三方编排：直连 ESP / 短信网关

| 方案 | 侧重点 | 备注 |
|------|--------|------|
| **SendGrid / Postmark / Mailgun / Resend** | 事务邮件 | 实现 `EmailNotificationProvider` 的常见选择。 |
| **Twilio** 等 | 短信 / 部分通道 | 与现有 `SmsNotificationProvider` 方向一致。 |

### 4.5 自研编排（轻量）

- **Outbox + 本地表 + 定时重试**：与当前 Outbox 导向一致，适合通道少、无运营工作流需求。
- **Temporal**：长流程、明确补偿语义时，用 Activity 调用各 `NotificationProvider` 或 Novu HTTP API。

---

## 5. 集成方式建议（无论选 Novu 或其它）

1. **领域边界**：`commerce` / `payment` / `billing` 的 **canonical 状态变更** 经 Outbox 进入 `notification-module`；**不**直接绑支付网关原始 webhook（与 README / architecture-notes 一致）。
2. **适配器位置**：新增 `*.infrastructure.novu`（或 `knock`、`courier` 等）实现或委托现有 `NotificationProvider`，避免在 `domain` 引用外部 DTO。
3. **幂等与审计**：至少在本库记录 `notification_event` / `notification_delivery`（或等价表），与外部系统的 message id 做映射，便于对账与合规。
4. **密钥与配置**：经 `secrets-config-module` 或 Spring 配置注入，不把 API Key 写死在仓库。

---

## 6. 与「未默认引入依赖」的对照

Novu、Knock、Courier 等 **当前未写入根 BOM / `notification-module` 的默认依赖**；与 `docs/layering-and-open-source.md` 中其它「规划项」相同，落地时以 **可选模块或 `implementation` + 特性开关** 引入，并在本文档更新 **实际选型** 一行即可。
