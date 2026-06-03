# 基础设施即代码（IaC）：放在哪、能否抹平云差异

> **文档导航**：[docs/README.md](./README.md)。应用内多云资源抽象见根目录 `README.md` 中的 **`cloud-resource-module`**；路线图中的 **Crossplane** 与本节「控制平面」思路一致。

---

## 1. IaC 是否应该写在本仓库里？

**不要把 IaC 写进业务 Java 源码树**（例如混在 `notification-module/src/...`）。Terraform / Pulumi / Helm / Crossplane manifest 与 **运行时应用代码** 生命周期、审阅权限、工具链都不同，应通过 **目录边界** 或 **仓库边界** 分开。

| 方式 | 适用场景 | 优点 | 注意 |
|------|----------|------|------|
| **同仓库、独立目录**（如 `infra/terraform/`、`deploy/helm/`） | 中小团队、希望一次 PR 同时改应用与配套资源 | 关联清晰、本地可一起 clone；CI 可对变更路径分别 job | 仓库体积与职责变重；需约定 **谁有权限合入 infra** |
| **独立 `platform-infra` 仓库** | 平台/SRE 与应用团队分工、多应用共享一套底座 | 权限与发布节奏独立；状态文件（state）边界干净 | 要与 **应用版本、镜像 tag、配置契约** 显式对齐，避免漂移 |
| **云厂商控制台 + 脚本** | 极早期原型 | 成本低 | 难审计、难复现，**不建议**作为长期主路径 |

**对本仓库的推荐表述**：以 **Gradle 模块 = 应用与领域** 为主；IaC 作为 **同仓 `infra/`** 或 **独立仓** 均可，**二者都比「写进 `src/main/java`」合适**。若采用 monorepo，建议在根 `.gitignore`、CI 与 CODEOWNERS 上对 `infra/**` 单独策略。

---

## 2. 能否抹消不同云厂商的差异？

**不能从根上抹消**，只能 **分层收敛** 到「可接受的差异面」。

- **永远会泄漏的差异**：区域与合规、IAM 模型、网络（VPC / PrivateLink）、托管服务名称与 SLA、配额与计费维度、部分 API 语义（例如对象存储一致性、消息投递语义）。
- **值得统一的层次**：
  1. **应用侧**：继续用 **`cloud-resource-module` 的抽象**（bucket、queue、function、CDN 等）表达「平台意图」，具体实现放在各云适配器；与 Kill Bill 等 **SPI 模式** 一致。
  2. **IaC 侧**：用 **模块化的 Terraform**（`modules/object-storage` + `envs/aws`、`envs/gcp`）、**Pulumi ComponentResource**、或 **Crossplane Composition** 把「一套业务含义」映射到多实现；重复代码下沉到 module，而不是在业务里 if-else 云厂商。
  3. **运行时编排**：**Kubernetes** 可在 **工作负载形态** 上部分统一，但集群本身、LoadBalancer、存储类仍依赖底层云。

**结论**：目标是 **缩小变更半径**（换云时主要改适配器 + IaC 模块），而不是声称「一套 HCL/ YAML 零改动跑遍所有云」。若团队规模有限，**先单云 IaC + 应用 SPI** 往往比过早做多云 IaC 更稳。

---

## 3. 与本项目路线图的关系（Crossplane）

README 将 **Crossplane** 列为可选演进：在 **Kubernetes 上** 用 CRD 声明云资源，由 provider 协调 AWS/GCP/Azure。它与 **Terraform 主导** 的团队可以并存（需约定 **单一真相来源**，避免同一资源被两种工具争抢）。

建议：

- **应用代码**：不直接依赖 Crossplane CR；仍通过 **`cloud-resource-module`** 或运维提供的绑定（Secret、ConfigMap、服务发现）。
- **IaC / 平台仓**：Crossplane、Terraform 或二者之一明确为 **平台层**；本应用仓库只消费 **结果**（endpoint、ARN、连接串由密钥模块注入）。

---

## 4. 实践清单（摘要）

1. IaC 与 Java 分层：**独立目录或独立仓库**，不要塞进业务模块源码包。
2. 多云：**应用 SPI + IaC 模块化** 双轨；接受厂商差异在适配层显式存在。
3. 与 CI：对 `infra/**` 做 `terraform validate` / `pulumi preview` 等门禁，与 `./gradlew test` 分开或并行 job。
4. 状态与安全：远程 state、加密、最小权限 IAM；密钥不进 Git，与 **`secrets-config-module`** 的运行时注入衔接。

若后续选定 **单一工具链**（例如仅 Terraform 或仅 Crossplane），可在本文档顶部增加一行 **「当前组织默认」** 便于 onboarding。
