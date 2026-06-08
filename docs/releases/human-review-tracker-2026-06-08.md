# P4 Human Review Tracker

**Date:** 2026-06-08
**RC Tag:** rc/p4-import-export-2026-06-06.3
**Last Updated:** 2026-06-08
**Status:** RC Ready / Human Review Pending / Staging Pending / Production Not Ready

---

## 1. Current Status

| Area | Status | Notes |
|------|--------|-------|
| **RC** | ✅ Ready | rc/p4-import-export-2026-06-06.3 |
| **Staging** | ⏳ Pending review | 等待 human review + infra inputs |
| **Production** | ❌ Not Ready | 需要 staging validation |
| **P4-owned gates** | ✅ Passing | 361 backend + 9 frontend + typecheck 0 errors |
| **Infra inputs** | ⏳ Pending | OIDC、storage、secrets、domain |
| **Human sign-off** | ⏳ Pending | 7 个 review area 待签字 |

---

## 2. Reviewer Assignment

| Review Area | Primary Reviewer | Backup Reviewer | Owner Team | Required Before | Current Status |
|-------------|------------------|-----------------|------------|-----------------|----------------|
| **Security** | Security Team Lead | Security Engineer | Security | Staging | ⏳ Pending |
| **QA / Golden Render** | QA Team Lead | QA Engineer | QA | Staging | ⏳ Pending |
| **DevOps / Infrastructure** | DevOps Lead | Platform Engineer | DevOps | Staging | ⏳ Pending |
| **Tech Lead / Architecture** | Tech Lead | Senior Backend Engineer | Engineering | Staging | ⏳ Pending |
| **Frontend Lead / UX** | Frontend Lead | UX Designer | Frontend | Staging | ⏳ Pending |
| **DBA / Data** | DBA | Backend Engineer | Data | Staging/Production | ⏳ Pending |
| **Release Manager** | Release Manager | Tech Lead | Release | Staging | ⏳ Pending |

---

## 3. Evidence Links

| Evidence | File/Link | Used By | Notes |
|----------|-----------|---------|-------|
| **P4 Architecture** | `docs/architecture/p4-import-export-architecture.md` | All reviewers | 完整架构说明（19 章节、10 个 Mermaid 图表） |
| **Staging Readiness Gate** | `docs/releases/staging-readiness-gate-2026-06-08.md` | DevOps, Release Manager | Staging 阻塞项、签字清单 |
| **Human Review Execution** | `docs/releases/human-review-execution-2026-06-08.md` | All reviewers | 复核清单、签字矩阵 |
| **RC Release Notes** | `docs/releases/rc-2026-06-06.md` | All reviewers | RC 发布说明、测试结果 |
| **Project Export API Doc** | `docs/media-rendering/project-export.md` | Security, QA, Backend | API 端点详细说明 |
| **Schema Management Policy** | `docs/engineering/schema-management-policy.md` | DBA, Tech Lead | Schema 策略 |
| **Modulith Debt Register** | `docs/modulith-debt-register.md` | Tech Lead | 模块违规登记 |
| **CI Pre-existing Failures** | `docs/releases/ci-preexisting-failures-2026-06-06.md` | Tech Lead, Release Manager | 历史 CI 失败清单 |
| **RC Tag** | `git checkout rc/p4-import-export-2026-06-06.3` | All reviewers | 代码版本 |
| **P4 Backend Tests** | `./gradlew :identity-access-module:test` | Tech Lead, Release Manager | 361/361 passing |
| **Platform App Tests** | `./gradlew :platform-app:test` | Tech Lead, Release Manager | BUILD SUCCESSFUL |
| **Frontend Typecheck** | `npm run typecheck` | Frontend Lead, Release Manager | 0 errors |
| **ImportedMetadataPanel Tests** | `npx vitest run src/components/export/ImportedMetadataPanel.spec.ts` | Frontend Lead, QA | 9/9 passing |

---

## 4. Sign-off Matrix

| Area | Reviewer | Decision | Conditions | Signature | Date | Status |
|------|----------|----------|------------|-----------|------|--------|
| **Security** | Security Team Lead | ⏳ Pending | | | | Pending |
| **QA / Golden Render** | QA Team Lead | ⏳ Pending | | | | Pending |
| **DevOps / Infrastructure** | DevOps Lead | ⏳ Pending | | | | Pending |
| **Tech Lead / Architecture** | Tech Lead | ⏳ Pending | | | | Pending |
| **Frontend Lead / UX** | Frontend Lead | ⏳ Pending | | | | Pending |
| **DBA / Data** | DBA | ⏳ Pending | | | | Pending |
| **Release Manager** | Release Manager | ⏳ Pending | | | | Pending |

**Decision 允许值:**
- `Pending` - 待审核
- `Approved` - 无阻塞，可继续
- `Approved with conditions` - 有条件通过，需满足条件
- `Blocked` - 阻塞，必须解决后才能继续

---

## 5. Blocking Item Tracker

| Blocking Item | Owner | Required Before | Current Status | Resolution Plan | Evidence | Notes |
|---------------|-------|-----------------|----------------|-----------------|----------|-------|
| **Security sign-off** | Security Team | Staging | ⏳ Pending | 完成安全审计并签字 | Security Review Checklist | |
| **Golden Render visual QA** | QA Team | Staging | ⏳ Pending | 完成人工视觉检查并签字 | QA / Golden Render Checklist | |
| **Infrastructure inputs** | DevOps | Staging | ⏳ Pending | 配置 OIDC、storage、secrets、domain | DevOps / Infrastructure Checklist | |
| **Modulith debt decision** | Tech Lead | Staging | ⏳ Pending | 接受或批准修复计划 | Modulith Debt Register | |
| **Frontend UI/UX review** | Frontend Lead | Staging | ⏳ Pending | 完成前端体验审查并签字 | Frontend / UX Checklist | |
| **CI/test strategy review** | Tech Lead | Staging | ⏳ Pending | 接受 CI/test 策略并签字 | CI Pre-existing Failures | |
| **Staging smoke config** | DevOps | Staging | ⏳ Pending | 完成烟雾测试配置 | DevOps / Infrastructure Checklist | |
| **Render integration runtime** | Render Team | Production | ⏳ Pending | 完整 render runtime 验证 | RenderPipelineDagIT | 可延后到 Production |
| **Browser E2E** | QA Team | Production | ⏳ Pending | Playwright 测试 | Frontend / UX Checklist | 可延后到 Production |
| **Unrelated module CI debt** | Various | Production | ⏳ Pending | 修复或隔离 | CI Pre-existing Failures | 可延后到 Production |
| **Data/schema review** | DBA | Production | ⏳ Pending | 完成数据/模式审查 | DBA / Data Checklist | Tech Lead 可接受 staging 风险 |

---

## 6. Review Instructions by Area

### 6.1 Security Review Instructions

**Reviewer:** Security Team Lead  
**Required Before:** Staging  
**Evidence:** `docs/architecture/p4-import-export-architecture.md` §8, `docs/media-rendering/project-export.md`

**Checklist:**
- [ ] Tenant isolation reviewed - Path tenantId 是唯一可信 source
- [ ] Source tenantId ignored - ZIP 内 tenantId 被忽略
- [ ] Signed URL not in audit - 审计载荷排除 signed URL
- [ ] storageUri/storageRef not exposed - API response 不包含敏感字段
- [ ] linked_assets sharing semantics accepted - 短期分享，非长期归档
- [ ] MetadataScrubber key policy accepted - 当前删除所有 key 字段
- [ ] Scrub-on-write reviewed - 持久化前清洗
- [ ] Scrub-on-read reviewed - 读取时再次清洗
- [ ] Frontend sanitizeForDisplay reviewed - 前端展示前清洗
- [ ] ZIP slip/bomb/checksum/allowlist reviewed - 安全检查完整
- [ ] Read audit policy accepted - 读取端点不生成审计
- [ ] No secrets in docs/code - 无真实 secret 泄露

---

### 6.2 QA / Golden Render Instructions

**Reviewer:** QA Team Lead  
**Required Before:** Staging  
**Evidence:** Golden Render Project, `docs/architecture/p4-import-export-architecture.md` §11

**Checklist:**
- [ ] final_1080p.mp4 manually played - 视频播放正常
- [ ] Audio sync checked - 音频同步无杂音
- [ ] Subtitles visible - 字幕显示正确
- [ ] Watermark position checked - 水印位置正确
- [ ] Fade checked - 淡入淡出效果正常
- [ ] Cross-dissolve checked - 交叉溶解效果正常
- [ ] Crop/placement checked - 裁剪和位置调整正确
- [ ] Extracted frames reviewed - 帧提取内容符合预期
- [ ] Visual artifacts checked - 无视觉伪影
- [ ] Pass/fail notes added - 添加通过/失败备注

---

### 6.3 DevOps / Infrastructure Instructions

**Reviewer:** DevOps Lead  
**Required Before:** Staging  
**Evidence:** `docs/releases/staging-readiness-gate-2026-06-08.md` §3

**Checklist:**
- [ ] APP_PUBLIC_DOMAIN provided - 公共域名
- [ ] OIDC_ISSUER_DOMAIN provided - OIDC 发行者域名
- [ ] STORAGE_PUBLIC_DOMAIN provided - 存储公共域名
- [ ] STORAGE_PROVIDER selected - 存储提供商（S3/MinIO/Local）
- [ ] S3/MinIO endpoint provided - 端点地址
- [ ] S3 bucket provided - Bucket 名称
- [ ] S3 credentials secret names provided - Access Key / Secret Key
- [ ] DATABASE_SECRET_NAME provided - 数据库 Secret
- [ ] JWT_SECRET_NAME provided - JWT Secret
- [ ] Ingress/TLS plan ready - Ingress 配置
- [ ] Egress smoke URL provided - 烟雾测试 URL
- [ ] Allowed domains updated - 允许的域名列表
- [ ] Object storage access logging decision made - 访问日志决策
- [ ] REPLACE_ME secrets removed - 占位符已替换

---

### 6.4 Tech Lead / Architecture Instructions

**Reviewer:** Tech Lead  
**Required Before:** Staging  
**Evidence:** `docs/modulith-debt-register.md`, `docs/engineering/schema-management-policy.md`, `docs/releases/ci-preexisting-failures-2026-06-06.md`

**Checklist:**
- [ ] Modulith debt accepted or fix plan approved - identity→artifact/storage 违规
- [ ] identity→artifact/storage violations reviewed - 8 项违规已记录
- [ ] No module merge policy accepted - 不通过合并模块掩盖违规
- [ ] shared-kernel port direction accepted - Port 接口抽象方向
- [ ] Adapter relocation plan accepted - Adapter 迁移计划
- [ ] Schema policy accepted - V1 baseline + 追加 migration
- [ ] Bootstrap hydrate-only accepted - 禁止 DDL
- [ ] Render integration profile accepted - 分离到专用 profile
- [ ] CI/test strategy accepted - P4-owned gates 通过

---

### 6.5 Frontend / UX Instructions

**Reviewer:** Frontend Lead  
**Required Before:** Staging  
**Evidence:** `frontend/src/components/export/ImportedMetadataPanel.vue`, `frontend/src/components/export/ExportPanel.vue`

**Checklist:**
- [ ] ImportedMetadataPanel UX reviewed - UI 交互正常
- [ ] Shell import not misleading as full media restore - 明确提示仅创建 Shell
- [ ] Assets need upload warning clear - 警告信息清晰
- [ ] No sensitive fields displayed - 无 storageUri/downloadUrl/signedUrl
- [ ] Empty/loading/error states reviewed - 状态展示正常
- [ ] Browser/E2E debt accepted - E2E 测试债务接受

---

### 6.6 DBA / Data Instructions

**Reviewer:** DBA  
**Required Before:** Staging/Production  
**Evidence:** `docs/engineering/schema-management-policy.md`, `platform-app/src/main/resources/db/migration/V1__initial_schema.sql`

**Checklist:**
- [ ] V1 baseline reviewed - production DDL source of truth
- [ ] schema.sql test-only mirror accepted - H2 test-only baseline
- [ ] Archived migrations V2-V5 reviewed - 历史迁移已归档
- [ ] project_import_metadata schema reviewed - 表结构正确
- [ ] Future schema change policy accepted - 追加 migration
- [ ] Migration rewrite policy accepted - 禁止 rewrite

---

### 6.7 Release Manager Instructions

**Reviewer:** Release Manager  
**Required Before:** Staging  
**Evidence:** `docs/releases/rc-2026-06-06.md`, `docs/releases/staging-readiness-gate-2026-06-08.md`

**Checklist:**
- [ ] RC tag verified - rc/p4-import-export-2026-06-06.3
- [ ] Release notes reviewed - RC 发布说明
- [ ] Staging gate reviewed - Staging 阻塞项
- [ ] Sign-off matrix complete - 所有 reviewer 签字
- [ ] Staging go/no-go decision recorded - 记录决策
- [ ] Production not-ready status confirmed - 确认 production 未 ready

---

## 7. Go / No-Go Decision

| Decision Area | Required | Current Status | Go/No-Go | Reason |
|---------------|----------|----------------|----------|--------|
| **RC continuation** | P4-owned gates passing | ✅ Go | Go | 361 backend + 9 frontend + typecheck 0 errors |
| **Staging config PR** | Infra inputs | ⏳ No-Go | No-Go | 等待 OIDC、storage、secrets、domain |
| **Staging deployment** | All sign-off complete | ⏳ No-Go | No-Go | 等待 7 个 review area 签字 |
| **Production promotion** | Staging validation | ❌ No-Go | No-Go | 需要 staging 完成 |

---

## 8. Update Procedure

### 8.1 如何更新 tracker

1. **Reviewer 更新 Decision**
   - 在 Sign-off Matrix 中修改 Decision 字段
   - 可选值：Pending / Approved / Approved with conditions / Blocked
   - 填写 Signature 和 Date

2. **Owner 更新 blocking item 状态**
   - 在 Blocking Item Tracker 中更新 Current Status
   - 更新 Resolution Plan 和 Evidence

3. **Release Manager 汇总 Go/No-Go**
   - 当所有 sign-off 完成后，更新 Go / No-Go Decision
   - 记录 staging go/no-go decision

### 8.2 更新规则

- ✅ 允许：Reviewer 本人更新自己的 Decision
- ✅ 允许：Owner 更新自己的 blocking item 状态
- ✅ 允许：Release Manager 汇总 Go/No-Go
- ❌ 不允许：AI 或自动化代签
- ❌ 不允许：把 Pending 改为 Approved（除非真实 reviewer 已签字）
- ❌ 不允许：删除或弱化 blocker

### 8.3 更新频率

- **每次 review 完成后**：更新 Sign-off Matrix
- **每个 blocking item 解决后**：更新 Blocking Item Tracker
- **所有 sign-off 完成后**：更新 Go / No-Go Decision

---

## 9. Related Documents

| 文档 | 说明 |
|------|------|
| [P4 Architecture](architecture/p4-import-export-architecture.md) | 完整架构说明 |
| [Staging Readiness Gate](staging-readiness-gate-2026-06-08.md) | Staging 阻塞项、签字清单 |
| [Human Review Execution](human-review-execution-2026-06-08.md) | 复核清单、签字矩阵 |
| [RC Release Notes](rc-2026-06-06.md) | RC 发布说明 |
| [Project Export API Doc](../media-rendering/project-export.md) | API 端点详细说明 |
| [Schema Management Policy](../engineering/schema-management-policy.md) | Schema 策略 |
| [Modulith Debt Register](../modulith-debt-register.md) | 模块违规登记 |
| [CI Pre-existing Failures](ci-preexisting-failures-2026-06-06.md) | 历史 CI 失败清单 |

---

**Document prepared by:** Kilo (AI-assisted)  
**Date:** 2026-06-08  
**Status:** Waiting for human review sign-off  
**Next Update:** After first reviewer sign-off
