# P4 Staging Readiness Gate

**Date:** 2026-06-08
**RC Tag:** rc/p4-import-export-2026-06-06.3
**Status:** RC Ready / Human Review Pending / Staging Pending / Production Not Ready

---

## 1. Current RC Status

| 维度 | 状态 | 说明 |
|------|------|------|
| **RC Tag** | ✅ rc/p4-import-export-2026-06-06.3 | Latest: rc/p4-import-export-2026-06-06.3, rc/p4-import-export-2026-06-06.2, rc/p4-import-export-2026-06-06 |
| **Latest Commit** | ✅ b53fb97 | chore: trigger CI workflow |
| **P4-owned Gates** | ✅ All passing | 361 backend + 9 frontend + typecheck 0 errors |
| **platform-app** | ✅ BUILD SUCCESSFUL | |
| **Frontend** | ✅ 0 errors | vue-tsc clean |
| **Schema Policy** | ✅ 冻结 | V1 baseline + 追加 migration |
| **Docs Consistency** | ✅ 完成 | architecture doc 与实现一致 |

### 1.1 P4-owned Gates Detail

| Gate | Command | 状态 | Evidence |
|------|---------|------|----------|
| **P4 Backend Tests** | `./gradlew :identity-access-module:test` | ✅ 361/361 | All import/export tests |
| **Platform App Tests** | `./gradlew :platform-app:test` | ✅ BUILD SUCCESSFUL | |
| **Frontend Typecheck** | `npm run typecheck` | ✅ 0 errors | vue-tsc clean |
| **ImportedMetadataPanel** | `npx vitest run src/components/export/ImportedMetadataPanel.spec.ts` | ✅ 9/9 | Targeted component tests |
| **V6 Migration** | FlywaySchemaIntegrationTest | ✅ PASS | FK fix verified |
| **Golden Render E2E** | Automated validation | ✅ 4/4 | Automated validation |

---

## 2. Gate Summary

| Gate | Owner | 状态 | Required Before | Evidence | Decision |
|------|-------|------|-----------------|----------|----------|
| **Security Sign-off** | Security Team | ⏳ Pending | Staging | 安全审计文档 | 阻塞 Staging |
| **Golden Render Visual QA** | QA Team | ⏳ Pending | Staging | 视频播放、字幕、水印、音频同步 | 阻塞 Staging |
| **Infrastructure Inputs** | DevOps | ⏳ Pending | Staging | OIDC、storage、secrets、domain | 阻塞 Staging |
| **Modulith Debt Decision** | Tech Lead | ⏳ Pending | Staging fix or acceptance | docs/modulith-debt-register.md | 阻塞 Staging 或 Tech Lead 接受 |
| **Frontend UI/UX Review** | UX Team | ⏳ Pending | Staging | ImportedMetadataPanel UX | 阻塞 Staging |
| **CI/Test Strategy Review** | Tech Lead | ⏳ Pending | Staging | render integration profile | 阻塞 Staging |
| **Data/Schema Review** | DBA | ⏳ Pending | Staging/Production | schema.sql 同步 | 阻塞 Staging/Production |
| **Render Integration Runtime** | Render Team | ⏳ Pending | Production | 完整 render runtime | 可延后到 Production |
| **Browser E2E** | QA Team | ⏳ Pending | Production | Playwright | 可延后到 Production |

---

## 3. Staging Blockers

| Blocker | Owner | Required Input | 状态 | Notes |
|---------|-------|----------------|------|-------|
| **APP_PUBLIC_DOMAIN** | DevOps | 公共域名 | ⏳ 未配置 | 例如 staging.media-platform.example.com |
| **OIDC_ISSUER_DOMAIN** | DevOps | OIDC 发行者域名 | ⏳ 未配置 | 例如 auth.example.com |
| **STORAGE_PUBLIC_DOMAIN** | DevOps | 存储公共域名 | ⏳ 未配置 | 例如 storage.example.com |
| **EGRESS_SMOKE_URL** | DevOps | Egress 烟雾测试 URL | ⏳ 未配置 | 用于验证出站连接 |
| **STORAGE_PROVIDER** | DevOps | 存储提供商 | ⏳ 未配置 | S3 / MinIO / Local |
| **S3_ENDPOINT** | DevOps | S3 端点 | ⏳ 未配置 | 例如 s3.amazonaws.com 或 minio:9000 |
| **S3_REGION** | DevOps | S3 区域 | ⏳ 未配置 | 例如 us-east-1 |
| **S3_BUCKET** | DevOps | S3 Bucket 名称 | ⏳ 未配置 | |
| **S3_ACCESS_KEY_SECRET_NAME** | DevOps | S3 Access Key Secret | ⏳ 未配置 | Kubernetes Secret 名称 |
| **S3_SECRET_KEY_SECRET_NAME** | DevOps | S3 Secret Key Secret | ⏳ 未配置 | Kubernetes Secret 名称 |
| **DATABASE_SECRET_NAME** | DevOps | 数据库 Secret | ⏳ 未配置 | |
| **JWT_SECRET_NAME** | DevOps | JWT Secret | ⏳ 未配置 | |
| **OTHER_REPLACE_ME_SECRETS** | DevOps | 其他需替换的 Secrets | ⏳ 未配置 | |
| **Ingress/TLS Config** | DevOps | Ingress 配置 | ⏳ 未配置 | TLS 证书 |
| **Object Storage Access Logging** | DevOps | 访问日志决策 | ⏳ 未配置 | 是否启用对象存储访问日志 |

---

## 4. Human Review Checklist

### 4.1 Security Review

| Review | Owner | Checklist | Evidence | Sign-off | Status |
|--------|-------|-----------|----------|----------|--------|
| **Tenant Isolation** | Security Team | ✅ Path tenantId 是唯一可信 source<br>✅ ZIP 内 tenantId 被忽略<br>✅ 数据库查询强制 WHERE tenant_id = ? | docs/architecture/p4-import-export-architecture.md §8.1 | ⏳ Pending | ⏳ Pending |
| **Signed URL Safety** | Security Team | ✅ 签名 URL 不进入 audit<br>✅ URL TTL 1-86400 秒<br>✅ URL 自动过期 | docs/architecture/p4-import-export-architecture.md §8.2 | ⏳ Pending | ⏳ Pending |
| **Storage URI/Ref Safety** | Security Team | ✅ storageUri/storageRef 不进入 API response<br>✅ 清洗前验证 | docs/architecture/p4-import-export-architecture.md §8.3 | ⏳ Pending | ⏳ Pending |
| **MetadataScrubber Key Policy** | Security Team | ✅ 当前接受所有 key 字段被删除<br>✅ 未来可改为 context-aware | docs/architecture/p4-import-export-architecture.md §8.3 | ⏳ Pending | ⏳ Pending |
| **linked_assets Sharing Semantics** | Security Team | ✅ 分享 zip = 分享短期下载权限<br>✅ 不适合长期归档 | docs/architecture/p4-import-export-architecture.md §8.2 | ⏳ Pending | ⏳ Pending |
| **Read Audit Policy** | Security Team | ✅ 读取端点不生成审计（当前）<br>✅ 可通过 Feature Flag 启用 | docs/architecture/p4-import-export-architecture.md §8.4 | ⏳ Pending | ⏳ Pending |

### 4.2 QA Review

| Review | Owner | Checklist | Evidence | Sign-off | Status |
|--------|-------|-----------|----------|----------|--------|
| **final_1080p.mp4** | QA Team | ✅ 手动播放视频<br>✅ 验证视频可播放 | Golden Render Project | ⏳ Pending | ⏳ Pending |
| **Subtitle Visibility** | QA Team | ✅ 字幕可见<br>✅ 字幕同步 | Golden Render Project | ⏳ Pending | ⏳ Pending |
| **Watermark** | QA Team | ✅ 水印可见<br>✅ 水印位置正确 | Golden Render Project | ⏳ Pending | ⏳ Pending |
| **Audio Sync** | QA Team | ✅ 音频同步<br>✅ 无杂音 | Golden Render Project | ⏳ Pending | ⏳ Pending |
| **Fade/Cross-dissolve** | QA Team | ✅ 淡入淡出效果<br>✅ 交叉溶解效果 | Golden Render Project | ⏳ Pending | ⏳ Pending |
| **Crop/Placement** | QA Team | ✅ 裁剪效果<br>✅ 位置调整正确 | Golden Render Project | ⏳ Pending | ⏳ Pending |
| **Extracted Frames** | QA Team | ✅ 帧提取正确<br>✅ 帧内容符合预期 | Golden Render Project | ⏳ Pending | ⏳ Pending |

### 4.3 Frontend Review

| Review | Owner | Checklist | Evidence | Sign-off | Status |
|--------|-------|-----------|----------|----------|--------|
| **ImportedMetadataPanel UX** | UX Team | ✅ 摘要显示清晰<br>✅ 详情懒加载正常<br>✅ 错误提示友好 | ImportedMetadataPanel.vue | ⏳ Pending | ⏳ Pending |
| **Shell Import Not Misleading** | UX Team | ✅ 明确提示仅创建 Project Shell<br>✅ 不暗示媒体已恢复 | ExportPanel.vue | ⏳ Pending | ⏳ Pending |
| **Assets Need Upload Warning** | UX Team | ✅ 资产需要上传警告清晰<br>✅ 用户理解后续步骤 | ImportedMetadataPanel.vue | ⏳ Pending | ⏳ Pending |
| **No Sensitive Fields Displayed** | UX Team | ✅ 无 storageUri 显示<br>✅ 无 downloadUrl 显示<br>✅ 无 signedUrl 显示 | ImportedMetadataPanel.vue | ⏳ Pending | ⏳ Pending |

### 4.4 Backend/Architecture Review

| Review | Owner | Checklist | Evidence | Sign-off | Status |
|--------|-------|-----------|----------|----------|--------|
| **Modulith Debt Decision** | Tech Lead | ✅ 接受或批准修复计划<br>✅ 不合并模块掩盖违规 | docs/modulith-debt-register.md | ⏳ Pending | ⏳ Pending |
| **Ports/Adapters Direction** | Tech Lead | ✅ 接受 shared-kernel port 方向<br>✅ 接受 adapter relocation | docs/architecture/p4-import-export-architecture.md §2.3 | ⏳ Pending | ⏳ Pending |
| **Schema Policy** | Tech Lead | ✅ V1 baseline 为 production DDL source<br>✅ 后续变更追加 migration | docs/engineering/schema-management-policy.md | ⏳ Pending | ⏳ Pending |
| **Render Integration Profile** | Tech Lead | ✅ 接受 render integration profile 分离<br>✅ 接受 production 前验证 | docs/architecture/p4-import-export-architecture.md §13.3 | ⏳ Pending | ⏳ Pending |

### 4.5 DevOps Review

| Review | Owner | Checklist | Evidence | Sign-off | Status |
|--------|-------|-----------|----------|----------|--------|
| **Staging Domain Ready** | DevOps | ✅ DNS 解析正常<br>✅ TLS 证书有效 | kubectl get ingress -n media-platform-staging | ⏳ Pending | ⏳ Pending |
| **OIDC Ready** | DevOps | ✅ OIDC 发行者可用<br>✅ 客户端配置正确 | curl https://auth.example.com/.well-known/openid-configuration | ⏳ Pending | ⏳ Pending |
| **Storage Ready** | DevOps | ✅ S3/MinIO 可用<br>✅ Bucket 存在<br>✅ Access Key 有效 | curl https://storage.example.com/health | ⏳ Pending | ⏳ Pending |
| **Secrets Ready** | DevOps | ✅ Kubernetes Secrets 已创建<br>✅ Secrets 已挂载 | kubectl get secrets -n media-platform-staging | ⏳ Pending | ⏳ Pending |
| **Smoke URL Ready** | DevOps | ✅ Smoke URL 可访问<br>✅ 返回预期响应 | curl https://staging.example.com/smoke | ⏳ Pending | ⏳ Pending |
| **Egress Allowed Domains** | DevOps | ✅ Egress 允许域名列表完整<br>✅ 代理配置正确 | docs/engineering/egress-smoke-rollout.md | ⏳ Pending | ⏳ Pending |

---

## 5. Required Before Staging

以下项目必须在 staging 前完成：

### 5.1 必须完成（阻塞 Staging）

- [ ] **Infrastructure Inputs** - OIDC、storage、secrets、domain 全部配置
- [ ] **Security Sign-off** - Security Team 完成安全审计并签字
- [ ] **Golden Render Visual QA** - QA Team 完成人工视觉检查并签字
- [ ] **Modulith Debt Decision** - Tech Lead 接受或批准修复计划
- [ ] **Frontend UI/UX Review** - UX Team 完成前端体验审查并签字
- [ ] **CI/Test Strategy Review** - Tech Lead 接受 CI/test 策略并签字
- [ ] **Staging Smoke Config** - 烟雾测试配置完成

### 5.2 可延后到 Production

以下项目可在 staging 部署后，production 前完成：

- [ ] **Render Integration Runtime Validation** - 完整 render runtime 验证
- [ ] **Browser E2E** - Playwright 端到端测试
- [ ] **Unrelated Module CI Debt** - 非 P4 模块 CI 债务修复或隔离
- [ ] **Data/Schema Review** - DBA 数据/模式审查（Tech Lead 可接受 staging 风险）

---

## 6. Post-RC Enhancements

以下项目在 RC 后实施，不阻塞 staging/production：

- [ ] **editor/runtime restore** - 编辑器/运行时元数据恢复
- [ ] **full media import** - 完整媒体导入
- [ ] **bundled_assets** - 完整资源打包导出
- [ ] **async export/import** - 异步导出/导入
- [ ] **context-aware MetadataScrubber** - 上下文感知的元数据清洗
- [ ] **per-tenant TTL policy** - 按租户配置 TTL
- [ ] **AI model integration** - AI 模型集成（平台级，非 P4 blocker）
- [ ] **payment integration** - 支付集成（平台级，非 P4 blocker）

---

## 7. Verification Commands

### 7.1 检出 RC Tag

```bash
cd platform
git checkout rc/p4-import-export-2026-06-06.3
```

### 7.2 运行 P4 后端测试

```bash
cd platform

# 运行 identity-access-module 测试（361 tests）
./gradlew :identity-access-module:test

# 运行 platform-app 测试
./gradlew :platform-app:test

# 运行所有测试（可选，可能有 unrelated failures）
./gradlew test

# 运行 render integration test（需要完整 runtime）
./gradlew :platform-app:renderIntegrationTest || true
```

### 7.3 运行前端验证

```bash
cd platform/frontend

# TypeScript 类型检查
npm run typecheck

# ImportedMetadataPanel 测试
npx vitest run src/components/export/ImportedMetadataPanel.spec.ts

# 完整前端测试
npx vitest run
```

### 7.4 验证 Staging Readiness（需要 infra）

```bash
cd platform

# 验证 production readiness（预期有 placeholder 警告）
scripts/validate-production-readiness.sh gitops/production

# 验证 staging egress smoke config
scripts/verify-egress-smoke-config.sh gitops/staging

# 验证 production egress smoke config（strict，预期失败）
scripts/verify-egress-smoke-config.sh gitops/production --strict || true
```

**说明**:
- `renderIntegrationTest` 需要完整 render runtime（FFmpeg、Natron）
- `production strict` 可能因 placeholder / smoke disabled 预期失败
- 不要声称 production-ready

---

## 8. Decision Matrix

| Decision | Allowed? | Conditions |
|----------|----------|------------|
| **Create staging PR** | ✅ Yes | 完成 infra inputs 后 |
| **Deploy to staging** | ✅ Yes | 完成 staging gates 签字后 |
| **Promote to production** | ❌ No | 需要 production blockers 全部关闭 |
| **Continue major feature development** | ⏳ Defer | 建议延后到 staging readiness 完成 |
| **Accept Modulith debt for staging** | ✅ Yes | Tech Lead 签字接受 |

---

## 9. Final Recommendation

### 9.1 RC Status: ✅ Ready

P4 Import/Export Pipeline 代码和 P4-owned gates 全部达标，RC 可继续。

### 9.2 Staging Status: ⏳ Pending

**阻塞项**:
1. Infrastructure inputs（OIDC、storage、secrets、domain）
2. Security sign-off
3. Golden Render visual QA
4. Modulith debt decision
5. Frontend UI/UX review
6. CI/test strategy review
7. Staging smoke config

### 9.3 Production Status: ❌ Not Ready

**阻塞项**:
1. 所有 staging 阻塞项
2. Render integration runtime validation
3. Unrelated module CI debt resolution/isolation
4. Human security sign-off (P2)
5. Browser E2E
6. Data/schema review

### 9.4 Next Owner Actions

| Owner | Action | Deadline |
|-------|--------|----------|
| **DevOps** | 配置 staging infra inputs（OIDC、storage、secrets、domain） | Staging 前 |
| **Security Team** | 完成安全审计并签字 | Staging 前 |
| **QA Team** | 完成 Golden Render visual QA 并签字 | Staging 前 |
| **Tech Lead** | 接受 Modulith debt 或批准修复计划 | Staging 前 |
| **UX Team** | 完成前端 UI/UX review 并签字 | Staging 前 |
| **Tech Lead** | 接受 CI/test strategy | Staging 前 |
| **DevOps** | 完成 staging smoke config | Staging 前 |

---

## 10. Related Documents

| 文档 | 说明 |
|------|------|
| [RC Release Notes](rc-2026-06-06.md) | RC 发布说明 |
| [P4 Architecture](architecture/p4-import-export-architecture.md) | P4 Import/Export Pipeline 架构文档 |
| [Schema Management Policy](engineering/schema-management-policy.md) | Schema 策略 |
| [Modulith Debt Register](../modulith-debt-register.md) | Modulith 违规登记 |
| [CI Pre-existing Failures](releases/ci-preexisting-failures-2026-06-06.md) | 历史 full CI 失败清单 |
| [Human Sign-off Checklist](releases/rc-human-signoff-2026-06-06.md) | 人工复核清单 |
| [Human Review Execution](human-review-execution-2026-06-08.md) | 人工复核执行文档（签字矩阵） |
| [Human Review Tracker](human-review-tracker-2026-06-08.md) | 人工复核跟踪表（最新状态源） |

---

**Document prepared by:** Kilo (AI-assisted)
**Date:** 2026-06-08
**Status:** Human Review Pending - 等待人工复核签字。详见 [Human Review Tracker](human-review-tracker-2026-06-08.md)
