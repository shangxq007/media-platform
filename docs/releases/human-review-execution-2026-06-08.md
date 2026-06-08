# P4 Human Review Execution

**Date:** 2026-06-08
**RC Tag:** rc/p4-import-export-2026-06-06.3
**Status:** RC Ready / Human Review Pending / Staging Pending / Production Not Ready

---

## 1. Review Scope

本次复核范围涵盖 P4 Import/Export Pipeline 的所有方面：

### 1.1 功能范围
- **Project Export** - JSON 和 ZIP 导出（metadata_only、linked_assets）
- **Project Import Preview** - JSON 和 ZIP 预览（无副作用）
- **Project Import Execute Shell** - ZIP Shell 导入（创建 Project Shell + 元数据持久化）
- **Metadata Persistence** - 元数据清洗和存储
- **Summary/Detail API** - 导入元数据读取 API
- **Frontend ImportedMetadataPanel** - UI 面板展示导入元数据
- **Security Scrubbing** - 三层防御清洗机制
- **Schema Policy** - V1 baseline + 追加 migration
- **CI/Test Strategy** - P4-owned gates、render integration profile
- **Staging Readiness** - 基础设施输入、部署配置

### 1.2 不在范围内
- AI 模型集成（平台级 Non-P4 能力）
- 支付集成（平台级 Non-P4 能力）
- editor/runtime restore（Post-RC）
- full media import（Post-RC）
- bundled_assets（Post-RC）
- async export/import（Post-RC）

---

## 2. Required Reviewers

| Review Area | Required Reviewer | Backup Reviewer | Required Before | Status |
|-------------|-------------------|-----------------|-----------------|--------|
| **Security** | Security Team Lead | Security Engineer | Staging | ⏳ Pending |
| **QA / Golden Render** | QA Team Lead | QA Engineer | Staging | ⏳ Pending |
| **DevOps / Infrastructure** | DevOps Lead | Platform Engineer | Staging | ⏳ Pending |
| **Tech Lead / Architecture** | Tech Lead | Senior Backend Engineer | Staging | ⏳ Pending |
| **Frontend Lead / UX** | Frontend Lead | UX Designer | Staging | ⏳ Pending |
| **DBA / Data** | DBA | Backend Engineer | Staging/Production | ⏳ Pending |
| **Release Manager** | Release Manager | Tech Lead | Staging | ⏳ Pending |

---

## 3. Evidence Package

### 3.1 文档证据

| 文档 | 说明 | 路径 |
|------|------|------|
| **P4 Architecture** | 完整架构说明（19 章节、10 个 Mermaid 图表） | `docs/architecture/p4-import-export-architecture.md` |
| **Staging Readiness Gate** | Staging 阻塞项、签字清单 | `docs/releases/staging-readiness-gate-2026-06-08.md` |
| **RC Release Notes** | RC 发布说明、测试结果 | `docs/releases/rc-2026-06-06.md` |
| **Project Export API Doc** | API 端点详细说明 | `docs/media-rendering/project-export.md` |
| **Schema Management Policy** | Schema 策略、V1 baseline | `docs/engineering/schema-management-policy.md` |
| **Modulith Debt Register** | 模块违规登记、修复计划 | `docs/modulith-debt-register.md` |
| **CI Pre-existing Failures** | 历史 CI 失败清单 | `docs/releases/ci-preexisting-failures-2026-06-06.md` |

### 3.2 代码证据

| 项目 | 说明 | 路径 |
|------|------|------|
| **RC Tag** | rc/p4-import-export-2026-06-06.3 | `git checkout rc/p4-import-export-2026-06-06.3` |
| **P4 Backend Tests** | 361/361 passing | `./gradlew :identity-access-module:test` |
| **Platform App Tests** | BUILD SUCCESSFUL | `./gradlew :platform-app:test` |
| **Frontend Typecheck** | 0 errors | `npm run typecheck` |
| **ImportedMetadataPanel Tests** | 9/9 passing | `npx vitest run src/components/export/ImportedMetadataPanel.spec.ts` |

### 3.3 CI/CD 证据

- GitHub Actions: 查看最新 CI 运行结果
- P4-owned gates: 全部通过
- 历史 22 failures: 已归档，与 P4 无关

---

## 4. Security Review Checklist

**Reviewer:** Security Team Lead  
**Required Before:** Staging

| Check | Evidence | Status | Notes |
|-------|----------|--------|-------|
| **Tenant Isolation** | Path tenantId 是唯一可信 source | ⏳ Pending | ZIP 内 tenantId 被忽略 |
| **Source tenantId Ignored** | 代码审查通过 | ⏳ Pending | ProjectImportExecuteService 使用 path tenantId |
| **Signed URL Not in Audit** | 审计载荷排除 signed URL | ⏳ Pending | AuditPort 不记录 URL |
| **storageUri/storageRef Not Exposed** | API response 不包含敏感字段 | ⏳ Pending | 清洗后验证 |
| **linked_assets Sharing Semantics** | 文档已描述分享语义 | ⏳ Pending | 短期分享，非长期归档 |
| **MetadataScrubber Key Deletion Policy** | 当前删除所有 key 字段 | ⏳ Pending | 未来可改为 context-aware |
| **Scrub-on-Write Reviewed** | 持久化前清洗 | ⏳ Pending | MetadataScrubber.scrub() |
| **Scrub-on-Read Reviewed** | 读取时再次清洗 | ⏳ Pending | 防御纵深 |
| **Frontend sanitizeForDisplay Reviewed** | 前端展示前清洗 | ⏳ Pending | ImportedMetadataPanel.vue |
| **ZIP Slip/Bomb/Checksum/Allowlist** | 安全检查完整 | ⏳ Pending | ProjectExportZipReader |
| **Read Audit Policy** | 读取端点不生成审计 | ⏳ Pending | 可通过 Feature Flag 启用 |
| **No Secrets in Docs/Code** | 无真实 secret 泄露 | ⏳ Pending | 文档和代码审查 |

---

## 5. QA / Golden Render Checklist

**Reviewer:** QA Team Lead  
**Required Before:** Staging

| Check | Evidence | Status | Pass/Fail Notes |
|-------|----------|--------|-----------------|
| **final_1080p.mp4 Manually Played** | 视频播放正常 | ⏳ Pending | |
| **Audio Sync Checked** | 音频同步无杂音 | ⏳ Pending | |
| **Subtitles Visible** | 字幕显示正确 | ⏳ Pending | |
| **Watermark Position Checked** | 水印位置正确 | ⏳ Pending | |
| **Fade Checked** | 淡入淡出效果正常 | ⏳ Pending | |
| **Cross-dissolve Checked** | 交叉溶解效果正常 | ⏳ Pending | |
| **Crop/Placement Checked** | 裁剪和位置调整正确 | ⏳ Pending | |
| **Extracted Frames Reviewed** | 帧提取内容符合预期 | ⏳ Pending | |
| **Visual Artifacts Checked** | 无视觉伪影 | ⏳ Pending | |

---

## 6. DevOps / Infra Checklist

**Reviewer:** DevOps Lead  
**Required Before:** Staging

| Check | Required Input | Status | Notes |
|-------|----------------|--------|-------|
| **APP_PUBLIC_DOMAIN** | 公共域名 | ⏳ Pending | 例如 staging.media-platform.example.com |
| **OIDC_ISSUER_DOMAIN** | OIDC 发行者域名 | ⏳ Pending | 例如 auth.example.com |
| **STORAGE_PUBLIC_DOMAIN** | 存储公共域名 | ⏳ Pending | 例如 storage.example.com |
| **STORAGE_PROVIDER** | 存储提供商 | ⏳ Pending | S3 / MinIO / Local |
| **S3/MinIO Endpoint** | 端点地址 | ⏳ Pending | |
| **S3 Bucket** | Bucket 名称 | ⏳ Pending | |
| **S3 Credentials Secret Names** | Access Key / Secret Key | ⏳ Pending | Kubernetes Secret 名称 |
| **DATABASE_SECRET_NAME** | 数据库 Secret | ⏳ Pending | |
| **JWT_SECRET_NAME** | JWT Secret | ⏳ Pending | |
| **Ingress/TLS** | Ingress 配置 | ⏳ Pending | TLS 证书 |
| **Egress Smoke URL** | 烟雾测试 URL | ⏳ Pending | |
| **Allowed Domains** | 允许的域名列表 | ⏳ Pending | |
| **Object Storage Access Logging** | 访问日志决策 | ⏳ Pending | 是否启用 |
| **REPLACE_ME Secrets Removed** | 占位符已替换 | ⏳ Pending | |

---

## 7. Architecture / Modulith Checklist

**Reviewer:** Tech Lead  
**Required Before:** Staging fix or acceptance

| Check | Evidence | Status | Notes |
|-------|----------|--------|-------|
| **Modulith Debt Accepted or Fix Plan Approved** | identity→artifact/storage 违规 | ⏳ Pending | 不合并模块 |
| **identity→artifact/storage Violations Reviewed** | 8 项违规已记录 | ⏳ Pending | docs/modulith-debt-register.md |
| **No Module Merge Policy Accepted** | 不通过合并模块掩盖违规 | ⏳ Pending | |
| **shared-kernel Port Direction Accepted** | Port 接口抽象方向 | ⏳ Pending | |
| **Adapter Relocation Plan Accepted** | Adapter 迁移计划 | ⏳ Pending | |
| **Schema Policy Accepted** | V1 baseline + 追加 migration | ⏳ Pending | |
| **Bootstrap Hydrate-only Accepted** | 禁止 DDL | ⏳ Pending | |
| **Render Integration Profile Accepted** | 分离到专用 profile | ⏳ Pending | |

---

## 8. Frontend / UX Checklist

**Reviewer:** Frontend Lead  
**Required Before:** Staging

| Check | Evidence | Status | Notes |
|-------|----------|--------|-------|
| **ImportedMetadataPanel UX Reviewed** | UI 交互正常 | ⏳ Pending | |
| **Shell Import Not Misleading** | 明确提示仅创建 Shell | ⏳ Pending | 不暗示媒体已恢复 |
| **Assets Need Upload Warning Clear** | 警告信息清晰 | ⏳ Pending | |
| **No Sensitive Fields Displayed** | 无 storageUri/downloadUrl/signedUrl | ⏳ Pending | |
| **Empty/Loading/Error States Reviewed** | 状态展示正常 | ⏳ Pending | |
| **Browser/E2E Debt Accepted** | E2E 测试债务接受 | ⏳ Pending | Post-RC |

---

## 9. Data / Schema Checklist

**Reviewer:** DBA  
**Required Before:** Staging/Production

| Check | Evidence | Status | Notes |
|-------|----------|--------|-------|
| **V1 Baseline Reviewed** | production DDL source of truth | ⏳ Pending | V1__initial_schema.sql |
| **schema.sql Test-only Mirror Accepted** | H2 test-only baseline | ⏳ Pending | 不是 production DDL |
| **Archived Migrations V2-V5 Reviewed** | 历史迁移已归档 | ⏳ Pending | docs/archive/prelaunch-migrations |
| **project_import_metadata Schema Reviewed** | 表结构正确 | ⏳ Pending | V6 migration |
| **Future Schema Change Policy Accepted** | 追加 migration | ⏳ Pending | |
| **Migration Rewrite Policy Accepted** | 禁止 rewrite | ⏳ Pending | |

---

## 10. Sign-off Matrix

| Area | Reviewer | Decision | Conditions | Signature | Date |
|------|----------|----------|------------|-----------|------|
| **Security** | Security Team Lead | ⏳ Pending | | | |
| **QA / Golden Render** | QA Team Lead | ⏳ Pending | | | |
| **DevOps / Infrastructure** | DevOps Lead | ⏳ Pending | | | |
| **Tech Lead / Architecture** | Tech Lead | ⏳ Pending | | | |
| **Frontend Lead / UX** | Frontend Lead | ⏳ Pending | | | |
| **DBA / Data** | DBA | ⏳ Pending | | | |
| **Release Manager** | Release Manager | ⏳ Pending | | | |

**Decision 选项:**
- `Approved` - 无阻塞，可继续
- `Approved with conditions` - 有条件通过，需满足条件
- `Blocked` - 阻塞，必须解决后才能继续

---

## 11. Blocking Items

| Item | Owner | Required Before | Resolution |
|------|-------|-----------------|------------|
| **Security Sign-off** | Security Team | Staging | 完成安全审计并签字 |
| **Golden Render Visual QA** | QA Team | Staging | 完成人工视觉检查并签字 |
| **Infrastructure Inputs** | DevOps | Staging | 配置 OIDC、storage、secrets、domain |
| **Modulith Debt Decision** | Tech Lead | Staging fix or acceptance | 接受或批准修复计划 |
| **Frontend UI/UX Review** | Frontend Lead | Staging | 完成前端体验审查并签字 |
| **CI/Test Strategy Review** | Tech Lead | Staging | 接受 CI/test 策略并签字 |
| **Staging Smoke Config** | DevOps | Staging | 完成烟雾测试配置 |

---

## 12. Final Release Recommendation

### 12.1 RC Status: ✅ Ready

P4 Import/Export Pipeline 代码和 P4-owned gates 全部达标，RC 可继续。

**条件:**
- 无阻塞 RC 的债务
- P4-owned gates 全部通过

### 12.2 Staging Status: ⏳ Pending Human Review

**阻塞项:**
1. Security sign-off
2. Golden Render visual QA
3. Infrastructure inputs
4. Modulith debt decision
5. Frontend UI/UX review
6. CI/test strategy review
7. Staging smoke config

**可延后到 Production:**
- Render integration runtime validation
- Browser E2E
- Unrelated module CI debt resolution/isolation
- Data/schema review (Tech Lead 可接受 staging 风险)

### 12.3 Production Status: ❌ Not Ready

**阻塞项:**
1. 所有 staging 阻塞项
2. Render integration runtime validation
3. Unrelated module CI debt resolution/isolation
4. Human security sign-off (P2)
5. Browser E2E
6. Data/schema review

### 12.4 Next Steps

| Step | Owner | Deadline |
|------|-------|----------|
| **Security Review** | Security Team | Staging 前 |
| **QA Golden Render Review** | QA Team | Staging 前 |
| **Infrastructure Setup** | DevOps | Staging 前 |
| **Modulith Debt Decision** | Tech Lead | Staging 前 |
| **Frontend UX Review** | Frontend Lead | Staging 前 |
| **CI/Test Strategy Review** | Tech Lead | Staging 前 |
| **Sign-off Collection** | Release Manager | Staging 前 |
| **Staging Deployment** | DevOps | 签字完成后 |

---

## 13. Related Documents

| 文档 | 说明 |
|------|------|
| [P4 Architecture](architecture/p4-import-export-architecture.md) | 完整架构说明 |
| [Staging Readiness Gate](staging-readiness-gate-2026-06-08.md) | Staging 阻塞项、签字清单 |
| [RC Release Notes](rc-2026-06-06.md) | RC 发布说明 |
| [Project Export API Doc](../media-rendering/project-export.md) | API 端点详细说明 |
| [Schema Management Policy](../engineering/schema-management-policy.md) | Schema 策略 |
| [Modulith Debt Register](../modulith-debt-register.md) | 模块违规登记 |
| [CI Pre-existing Failures](ci-preexisting-failures-2026-06-06.md) | 历史 CI 失败清单 |
| [Human Review Tracker](human-review-tracker-2026-06-08.md) | 人工复核跟踪表（最新状态源） |

---

**Document prepared by:** Kilo (AI-assisted)  
**Date:** 2026-06-08  
**Status:** Pending sign-off - 详见 [Human Review Tracker](human-review-tracker-2026-06-08.md)
