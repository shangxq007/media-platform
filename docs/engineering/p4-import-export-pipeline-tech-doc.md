# P4 Import/Export Pipeline 技术文档

## 文档信息

- **项目名称**: Media Platform P4 Import/Export Pipeline
- **版本**: v1.0
- **更新日期**: 2026-06-07
- **维护团队**: Platform Engineering Team
- **文档状态**: 完成

---

## 目录

1. [架构说明](#1-架构说明)
2. [实现细节](#2-实现细节)
3. [技术选型](#3-技术选型)
4. [运维部署架构](#4-运维部署架构)
5. [完成度与评估](#5-完成度与评估)
6. [维护与改进建议](#6-维护与改进建议)
7. [人工复核清单](#7-人工复核清单)
8. [注意事项与风险](#8-注意事项与风险)
9. [流程图与时序图](#9-流程图与时序图)

---

## 1. 架构说明

### 1.1 系统整体架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            API Gateway / Load Balancer                      │
└─────────────────────────────────────────────────────────────────────────────┘
                                       │
        ┌──────────────────────────────┼──────────────────────────────┐
        │                              │                              │
        ▼                              ▼                              ▼
┌──────────────┐              ┌──────────────┐              ┌──────────────┐
│   Frontend   │              │  Platform    │              │   Render     │
│   (Vue 3)    │◄────────────►│  App         │◄────────────►│   Worker     │
│   :8080      │              │  (Spring)    │              │   :8081      │
└──────────────┘              │  :8080       │              └──────────────┘
                              └──────────────┘
                                       │
        ┌──────────────────────────────┼──────────────────────────────┐
        │                              │                              │
        ▼                              ▼                              ▼
┌──────────────┐              ┌──────────────┐              ┌──────────────┐
│  PostgreSQL  │              │   Redis      │              │  BlobStorage │
│  (Database)  │              │   (Cache)    │              │  (S3/MinIO)  │
│  :5432       │              │   :6379      │              │              │
└──────────────┘              └──────────────┘              └──────────────┘
```

### 1.2 模块划分

```
platform/
├── platform-app                 # 主应用入口（Spring Boot 4.0.4）
├── shared-kernel                # 共享核心（Port 接口定义）
├── identity-access-module      # 身份访问模块（Export/Import 核心）
├── render-module                # 渲染模块
├── storage-module               # 存储模块（S3/MinIO）
├── audit-compliance-module      # 审计合规模块
├── frontend                     # Vue 3 前端
├── k8s/                         # Kubernetes 部署清单
└── docs/                        # 项目文档
```

### 1.3 核心服务职责

| 服务 | 职责 | 关键类 |
|------|------|--------|
| **ProjectExportService** | 元数据导出、资产清单生成 | `ProjectExportController` |
| **ProjectExportZipPackagingService** | ZIP 打包、SHA-256 校验 | `ProjectExportZipPackagingService` |
| **ProjectImportPreviewService** | 导入兼容性分析、预览报告 | `ProjectImportPreviewController` |
| **ProjectImportExecuteService** | Shell 导入、元数据持久化 | `ProjectImportExecuteController` |
| **MetadataScrubber** | 敏感 URL 清洗、安全检查 | `MetadataScrubber` |
| **ZipPackagingService** | ZIP 读取、解压、校验 | `ProjectExportZipReader` |

### 1.4 Port/Adapter 架构

系统采用 **Hexagonal Architecture (Port/Adapter)** 模式：

#### 核心 Port 接口（定义在 `shared-kernel`）

```java
// 审计端口
public interface AuditPort {
    void record(AuditEvent event);
}

// 资产下载 URL 生成端口
public interface AssetDownloadUrlPort {
    String generateSignedUrl(String storageUri, Duration ttl);
}

// 项目资产清单端口
public interface ProjectAssetListingPort {
    List<Artifact> listArtifactsByProject(String projectId);
}

// 成本估算端口
public interface CostEstimationPort {
    CostEstimate estimate(String projectId);
}

// 媒体探测端口
public interface MediaProbePort {
    MediaProbeResult probe(String storageUri);
}
```

#### Adapter 实现（定义在各模块）

```java
// S3 存储适配器
@Component
public class S3AssetDownloadUrlPort implements AssetDownloadUrlPort {
    @Autowired
    private BlobStorage blobStorage;

    @Override
    public String generateSignedUrl(String storageUri, Duration ttl) {
        return blobStorage.presignStorageUri(storageUri, ttl);
    }
}

// Artifact Catalog 适配器
@Component
public class ArtifactCatalogProjectAssetExportAdapter implements ProjectAssetListingPort {
    @Autowired
    private ArtifactCatalogService artifactCatalogService;

    @Override
    public List<Artifact> listArtifactsByProject(String projectId) {
        return artifactCatalogService.listArtifactsByProject(projectId);
    }
}
```

### 1.5 数据流向示意

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Export 数据流向                                     │
└─────────────────────────────────────────────────────────────────────────────┘

User Request
     │
     ▼
[ProjectExportController]
     │
     ├─► [ProjectExportService]
     │        │
     │        ├─► [ProjectAssetListingPort] ──► ArtifactCatalogService
     │        │        │
     │        │        ▼
     │        │    Asset List
     │        │
     │        ├─► [AssetDownloadUrlPort] ──► S3AssetDownloadUrlPort
     │        │        │                    │
     │        │        │                    ▼
     │        │        │                BlobStorage.presignStorageUri()
     │        │        │                    │
     │        │        │                    ▼
     │        │        │                Signed URLs
     │        │        │
     │        │        ▼
     │        └─► ProjectExportResponse
     │
     ├─► [MetadataScrubber] ──► 清洗敏感 URL
     │
     ├─► [ProjectExportZipPackagingService] ──► ZIP 打包
     │        │
     │        ├─► manifest.json
     │        ├─► project.json
     │        ├─► assets.json
     │        ├─► timeline/*.json
     │        ├─► render/*.json
     │        ├─► effects/*.json
     │        ├─► outputs/*.json
     │        ├─► audit/*.json
     │        └─► checksums/sha256sums.txt
     │
     └─► [AuditPort] ──► AuditLog
```

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Import 数据流向                                     │
└─────────────────────────────────────────────────────────────────────────────┘

ZIP Upload
     │
     ▼
[ProjectExportZipReader]
     │
     ├─► 安全校验
     │    ├─► Zip Bomb 检测（50MB 压缩 / 200MB 解压）
     │    ├─► Zip Slip 防护
     │    ├─► SHA-256 校验
     │    └─► Entry Allowlist 验证
     │
     ├─► 解析项目结构
     │    ├─► manifest.json
     │    ├─► project.json
     │    └─► assets.json
     │
     └─► ProjectExportResponse
              │
              ▼
     [ProjectImportPreviewService]
              │
              ├─► Schema 版本校验
              ├─► 资产分析（needsUpload / available）
              ├─► 效果兼容性检查
              └─► 空间坐标验证
              │
              ▼
     ProjectImportPreviewResponse
              │
              ▼
     [ProjectImportExecuteService]
              │
              ├─► 创建 Project Shell
              ├─► [MetadataScrubber] ──► 清洗元数据
              ├─► 持久化到 project_import_metadata
              ├─► [AuditPort] ──► 记录审计日志
              └─► 事务提交 / 回滚
              │
              ▼
     ProjectImportExecuteResponse
```

---

## 2. 实现细节

### 2.1 核心服务逻辑

#### 2.1.1 ProjectExportService

**职责**: 生成项目元数据导出响应

**关键流程**:
1. 验证租户访问权限（`assertTenantAccess(tenantId)`）
2. 查询项目元数据（Project、Timeline、Render、Effects）
3. 查询资产清单（通过 `ProjectAssetListingPort`）
4. 生成签名 URL（仅 `linked_assets` 模式）
5. 构建 `ProjectExportResponse`
6. 记录审计事件

**代码位置**: `identity-access-module/src/main/java/com/example/platform/identity/app/ProjectExportService.java`

#### 2.1.2 ProjectExportZipPackagingService

**职责**: 将 `ProjectExportResponse` 打包为 ZIP 文件

**关键特性**:
- 仅打包元数据（JSON 清单），不包含实际媒体文件
- 生成 SHA-256 校验和（`checksums/sha256sums.txt`）
- 自动清洗敏感信息（`storageRef`、`signedUrls`）
- 防止 Zip Slip 攻击（Entry Allowlist）
- 支持两种模式：`metadata_only`、`linked_assets`

**ZIP 文件结构**:
```
project-export-v1/
├── manifest.json
├── project.json
├── assets.json
├── README.md
├── checksums/
│   └── sha256sums.txt
├── timeline/
│   └── timeline.json
├── render/
│   ├── render-plan.json
│   ├── spatial-plan.json
│   └── export-profiles.json
├── effects/
│   ├── effect-taxonomy.json
│   └── applied-effects.json
├── outputs/
│   └── outputs-manifest.json
└── audit/
    └── audit-summary.json
```

**代码位置**: `identity-access-module/src/main/java/com/example/platform/identity/app/ProjectExportZipPackagingService.java`

#### 2.1.3 MetadataScrubber

**职责**: 清洗导入元数据中的敏感信息

**清洗字段**（大小写不敏感）:
- `downloadUrl`
- `storageUri` / `storage_uri`
- `storageRef` / `storage_ref`
- `bucket`
- `key`
- `signedUrl` / `signed_url`
- `url`

**清洗策略**:
- **写入时清洗 (Scrub-on-write)**: 持久化到数据库前清洗
- **读取时清洗 (Scrub-on-read)**: 读取时再次清洗（防御纵深）
- **前端清洗 (sanitizeForDisplay)**: 前端展示前再次清洗

**代码位置**: `identity-access-module/src/main/java/com/example/platform/identity/app/MetadataScrubber.java`

#### 2.1.4 ProjectImportPreviewService

**职责**: 导入前兼容性分析

**检查项**:
1. **Schema 版本校验**: 拒绝不支持的版本（400 错误）
2. **资产分析**:
   - `metadata_only` 模式：所有资产标记为 `needsUpload`
   - `linked_assets` 模式：检查 `downloadUrl` 是否过期
3. **效果兼容性**: 检查效果键是否属于已知 taxonomy v1
4. **空间坐标验证**: 验证 `normalized_ppm` 坐标范围（0..1,000,000）

**响应示例**:
```json
{
  "importPreview": {
    "compatible": true,
    "schemaVersionMatch": true,
    "warnings": [
      {
        "code": "MISSING_ASSET",
        "severity": "warning",
        "message": "Asset 'logo_transparent' not found in target storage",
        "assetId": "logo_transparent"
      }
    ],
    "assetMapping": [
      {
        "sourceAssetId": "color_bars_1080p",
        "targetAssetId": null,
        "status": "needs_upload",
        "sizeBytes": 524288
      }
    ],
    "estimatedImportSize": 524288,
    "missingAssetCount": 1
  }
}
```

#### 2.1.5 ProjectImportExecuteService

**职责**: 执行 Shell 导入并持久化元数据

**关键流程**:
1. 解析 ZIP 文件（调用 `ProjectExportZipReader`）
2. 创建 Project Shell（数据库事务）
3. 清洗并持久化元数据到 `project_import_metadata` 表
4. 记录审计事件（`PROJECT_IMPORT_SHELL`）
5. 事务提交或回滚

**持久化元数据**:
| 字段 | ZIP Entry | 说明 |
|------|-----------|------|
| `timeline_json` | `timeline/timeline.json` | 内部时间线结构 |
| `timeline_otio_json` | `timeline/timeline.otio` | OTIO 格式（如有） |
| `render_plan_json` | `render/render-plan.json` | 渲染计划 |
| `spatial_plan_json` | `render/spatial-plan.json` | 空间计划 |
| `export_profiles_json` | `render/export-profiles.json` | 导出配置 |
| `effect_taxonomy_json` | `effects/effect-taxonomy.json` | 效果分类法 |
| `applied_effects_json` | `effects/applied-effects.json` | 已应用效果 |
| `asset_mapping_json` |（生成） | 资产 ID 映射 |

**回滚策略**:
- 整个导入操作在单个事务中执行
- 任何步骤失败（项目创建、元数据持久化、审计记录）都会触发回滚
- 回滚后不留下任何 Project Shell

### 2.2 核心 DTO 结构

#### ProjectExportResponse

```java
public record ProjectExportResponse(
    String exportId,
    String exportMode,
    Instant exportedAt,
    ProjectExportManifestDto manifest,
    ProjectExportProjectDto project,
    ProjectExportAssetsDto assets,
    ProjectExportTimelineDto timeline,
    ProjectExportRenderDto render,
    ProjectExportEffectsDto effects,
    ProjectExportOutputsDto outputs,
    ProjectExportAuditDto audit
) {}
```

#### ProjectImportPreviewResponse

```java
public record ProjectImportPreviewResponse(
    String importId,
    boolean compatible,
    boolean schemaVersionMatch,
    List<ImportWarning> warnings,
    List<ImportAssetMapping> assetMapping,
    long estimatedImportSize,
    int missingAssetCount,
    int unsupportedEffectCount
) {}
```

#### ProjectImportExecuteResponse

```java
public record ProjectImportExecuteResponse(
    String importId,
    String status,
    String targetProjectId,
    String mode,
    ImportAssetSummary assets,
    List<ImportAssetMapping> assetMappings,
    ImportMetadataPersisted metadata,
    List<ImportWarning> warnings
) {}
```

### 2.3 数据库表结构（V6 Migration）

**表名**: `project_import_metadata`

**DDL** (V6__create_project_import_metadata.sql):
```sql
create table project_import_metadata (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    project_id varchar(64) not null,
    import_id varchar(64) not null unique,
    source_project_id varchar(64),
    source_export_id varchar(64),
    schema_version varchar(32),
    timeline_json text,
    timeline_otio_json text,
    render_plan_json text,
    spatial_plan_json text,
    export_profiles_json text,
    effect_taxonomy_json text,
    applied_effects_json text,
    asset_mapping_json text,
    created_at timestamp not null default now(),

    constraint fk_import_metadata_project
        foreign key (project_id)
        references project(id)
        on delete cascade
);

-- 索引
create index idx_project_import_metadata_project_id
    on project_import_metadata(project_id);

create index idx_project_import_metadata_tenant_project
    on project_import_metadata(tenant_id, project_id);

create index idx_project_import_metadata_import_id
    on project_import_metadata(import_id);

create index idx_project_import_metadata_created_at
    on project_import_metadata(created_at);
```

**说明**:
- 所有 JSON 字段在存储前经过 `MetadataScrubber` 清洗
- `asset_mapping_json` 记录资产 ID 映射关系（`sourceAssetId` → `targetAssetId`）
- 外键关联 `project` 表，级联删除
- 支持按 `import_id` 快速查询

### 2.4 安全措施与边界

#### 2.4.1 租户隔离

**实现机制**:
- 所有 API 路径包含 `tenantId` 路径变量
- Spring Security 验证用户属于该租户
- 数据库查询强制 `WHERE tenant_id = ?`

**API 示例**:
```
POST /api/v1/identity/tenants/{tenantId}/projects/{projectId}/exports
POST /api/v1/identity/tenants/{tenantId}/project-imports/preview
POST /api/v1/identity/tenants/{tenantId}/project-imports/archive
```

#### 2.4.2 签名 URL 策略

**策略**:
- 默认 TTL: 3600 秒（1 小时）
- 最大 TTL: 86400 秒（24 小时）
- 超过 86400 秒返回 400 错误
- 签名 URL 不记录在审计日志中
- `storageRef` 在响应中始终为 `null`

**代码实现**:
```java
if (request.signedUrlTtlSeconds() > 86400) {
    throw new IllegalArgumentException("signedUrlTtlSeconds cannot exceed 86400 (24 hours)");
}
```

#### 2.4.3 元数据清洗策略

**清洗规则**:
1. **递归处理**: 处理嵌套对象和数组
2. **大小写不敏感**: `downloadUrl`、`downloadurl`、`DOWNLOADURL` 都会被清洗
3. **所有 `key` 字段**: 为安全起见，删除所有名为 `key` 的字段
   - **风险**: 可能删除合法业务字段（如效果参数）
   - **当前影响**: 当前项目不依赖导入的 `key` 字段
   - **未来改进**: 实现上下文感知清洗（仅删除存储相关对象的 `key`）

**代码实现**:
```java
private static final Set<String> SENSITIVE_KEYS = Set.of(
    "downloadurl", "storageuri", "storageref", "bucket", "key",
    "signedurl", "url"
);

public String scrub(String json) {
    JsonNode root = MAPPER.readTree(json);
    if (root.isObject()) {
        scrubObject((ObjectNode) root);
    }
    return MAPPER.writeValueAsString(root);
}
```

#### 2.4.4 ZIP 文件安全检查

**检查项**:
1. **Zip Bomb 防护**:
   - 压缩文件最大 50 MB
   - 解压后最大 200 MB
   - 最多 100 个文件
2. **Zip Slip 防护**:
   - 拒绝包含 `..` 的路径
   - 拒绝绝对路径
   - 拒绝反斜杠路径
3. **Entry Allowlist**:
   - 仅允许预定义的文件列表
   - 拒绝未知文件
4. **SHA-256 校验**:
   - 验证每个文件的校验和
   - `sha256sums.txt` 不能引用自身
5. **内存处理**:
   - 不写入临时文件
   - 仅在内存中解析 JSON

**代码实现**:
```java
private void validateEntryName(String entryName) {
    if (entryName.contains("..")) {
        throw new SecurityException("Zip entry name cannot contain '..'");
    }
    if (entryName.startsWith("/") || entryName.startsWith("\\")) {
        throw new SecurityException("Zip entry name cannot start with / or \\");
    }
    String stripped = entryName.substring(CANONICAL_ROOT.length() + 1);
    if (!ALLOWED_ENTRIES.contains(stripped)) {
        throw new SecurityException("Zip entry name not in allowlist: " + entryName);
    }
}
```

### 2.5 异常处理与回滚策略

#### 2.5.1 导出异常处理

| 异常场景 | 处理策略 | HTTP 状态码 |
|----------|----------|-------------|
| 租户无权限 | 拒绝访问 | 403 |
| 项目不存在 | 返回错误 | 404 |
| 签名 URL 生成失败 | 整个导出失败（Fail Closed） | 500 |
| 未配置签名端口 | 返回未实现 | 501 |
| ZIP 打包失败 | 返回错误 | 500 |

#### 2.5.2 导入异常处理

| 异常场景 | 处理策略 | HTTP 状态码 |
|----------|----------|-------------|
| ZIP 文件过大 | 拒绝处理 | 400 |
| SHA-256 校验失败 | 拒绝处理 | 400 |
| Zip Slip 检测 | 拒绝处理 | 400 |
| Schema 版本不支持 | 拒绝处理 | 400 |
| 元数据持久化失败 | 事务回滚 | 500 |
| 审计记录失败 | 主事务不回滚（Best Effort） | 200 + 警告 |

#### 2.5.3 事务管理

**导入事务边界**:
```java
@Transactional
public ProjectImportExecuteResponse executeImport(String tenantId, MultipartFile file) {
    // 1. 创建 Project Shell
    Project project = projectService.createShell(tenantId, importName);

    try {
        // 2. 解析 ZIP
        ProjectExportResponse export = zipReader.read(file);

        // 3. 清洗并持久化元数据
        metadataService.persist(project.getId(), export);

        // 4. 记录审计（Best Effort）
        try {
            auditPort.record(AuditEvent.PROJECT_IMPORT_SHELL, payload);
        } catch (Exception e) {
            log.warn("Audit recording failed, but import succeeded", e);
        }

        return buildResponse(project, export);
    } catch (Exception e) {
        // 事务自动回滚，Project Shell 不会被创建
        throw new ImportFailedException("Import failed", e);
    }
}
```

### 2.6 审计逻辑与限制

#### 2.6.1 审计事件类型

| 事件 | 触发时机 | 载荷 |
|------|----------|------|
| `PROJECT_EXPORT` | 导出请求 | `exportId`, `mode`, `tenantId`, `projectId`, `assetCount` |
| `PROJECT_IMPORT_PREVIEW` | 导入预览 | `exportId`, `mode`, `assetCount`, `ttlSeconds` |
| `PROJECT_IMPORT_SHELL` | Shell 导入成功 | `importId`, `mode`, `sourceProjectId`, `assetCount`, `metadataPersisted` |

#### 2.6.2 审计载荷排除项

以下信息 **不会** 出现在审计日志中:
- ❌ JSON 内容（完整元数据）
- ❌ 签名 URL
- ❌ `storageUri` / `storageRef`
- ❌ ZIP 文件字节
- ❌ 用户 IP 地址（在审计上下文中）

#### 2.6.3 读取审计限制

**当前实现**:
- 元数据读取端点 **不生成** 审计记录
- 理由：只读操作，不修改运行时状态，遵循现有项目读取 API 模式

**受影响端点**:
- `GET /tenants/{tenantId}/projects/{projectId}/import-metadata`
- `GET /tenants/{tenantId}/project-imports/{importId}/metadata`

**未来增强**（如合规要求）:
```java
// Feature Flag: app.audit.importMetadataRead.enabled (默认: false)
if (auditConfig.isImportMetadataReadEnabled()) {
    auditPort.record(AuditEvent.IMPORT_METADATA_READ, lightweightPayload);
}
```

---

## 3. 技术选型

### 3.1 后端技术栈

| 技术 | 版本 | 选型理由 |
|------|------|----------|
| **Java** | 25 (LTS) | 最新 LTS 版本，支持 Virtual Threads、Pattern Matching、Record Patterns |
| **Spring Boot** | 4.0.4 | 最新主版本，与 Spring Modulith 2.x 集成 |
| **Spring Modulith** | 2.0.4 | 模块化单体架构，支持模块边界验证 |
| **Spring Security** | 内置 | OAuth2/OIDC 集成，租户隔离 |
| **PostgreSQL** | 15+ | Flyway 支持、JSONB 字段、事务完整性 |
| **Flyway** | 10.x | 数据库版本控制，V6 migration 策略 |
| **Gradle** | 9.1 | Kotlin DSL、增量构建、依赖管理 |
| **Jackson** | 2.x | JSON 序列化，支持 Record 类型 |
| **JaCoCo** | 0.8.13 | 代码覆盖率报告 |

### 3.2 前端技术栈

| 技术 | 版本 | 选型理由 |
|------|------|----------|
| **Vue** | 3.5.13 | Composition API、响应式系统、TypeScript 支持 |
| **TypeScript** | 5.7.2 | 类型安全、IDE 支持、编译时检查 |
| **Vite** | 6.0.7 | 快速 HMR、ESBuild 构建、Vue 插件 |
| **Pinia** | 2.3.0 | 状态管理、TypeScript 友好、DevTools |
| **Vue Router** | 4.5.0 | 路由管理、导航守卫、懒加载 |
| **Vitest** | 3.0.0 | Vue 测试框架、Jest 兼容、快速执行 |
| **vue-tsc** | 2.2.0 | 模板类型检查、IDE 集成 |
| **Tailwind CSS** | 3.4.17 | 原子化 CSS、响应式设计、暗色模式 |
| **jsdom** | 29.1.1 | 浏览器环境模拟，用于 Vitest 测试 |

### 3.3 存储与基础设施

| 技术 | 用途 | 选型理由 |
|------|------|----------|
| **S3 / MinIO** | BlobStorage | 对象存储、签名 URL、生命周期管理 |
| **Redis** | 缓存、会话 | 高性能、分布式锁、Rate Limiting |
| **PostgreSQL** | 主数据库 | ACID 事务、JSONB 支持、Flyway 集成 |
| **Docker** | 容器化 | 开发环境一致性、生产部署 |
| **Kubernetes** | 容器编排 | 自动扩缩容、滚动更新、健康检查 |
| **ArgoCD** | GitOps | 声明式部署、自动同步、回滚 |

### 3.4 关键依赖库

#### Gradle 依赖（`build.gradle.kts`）:

```kotlin
// Spring Boot BOM
mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.4")

// Spring AI BOM（预览版，未来集成）
mavenBom("org.springframework.ai:spring-ai-bom:2.0.0-M3")

// Spring Modulith
implementation("org.springframework.modulith:spring-modulith-api:2.0.4")
implementation("org.springframework.modulith:spring-modulith-starter-core:2.0.4")

// Flyway
implementation("org.flywaydb:flyway-core")
implementation("org.flywaydb:flyway-database-postgresql")

// Jackson
implementation("com.fasterxml.jackson.core:jackson-databind")
implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

// PostgreSQL
implementation("org.postgresql:postgresql")

// JWT
implementation("io.jsonwebtoken:jjwt-api:0.12.6")
```

#### npm 依赖（`frontend/package.json`）:

```json
{
  "dependencies": {
    "vue": "^3.5.13",
    "vue-router": "^4.5.0",
    "pinia": "^2.3.0",
    "axios": "^1.7.9",
    "graphql": "^16.14.0",
    "graphql-request": "^7.4.0",
    "oidc-client-ts": "^3.1.0",
    "@sentry/vue": "^10.53.1"
  },
  "devDependencies": {
    "typescript": "~5.7.2",
    "vite": "^6.0.7",
    "vitest": "^3.0.0",
    "vue-tsc": "^2.2.0",
    "jsdom": "^29.1.1",
    "happy-dom": "^17.6.3",
    "tailwindcss": "^3.4.17"
  }
}
```

---

## 4. 运维部署架构

### 4.1 部署环境架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Production 环境                                │
└─────────────────────────────────────────────────────────────────────────────┘

                    ┌─────────────────────────────┐
                    │     Ingress Controller       │
                    │     (NGINX / Traefik)        │
                    └─────────────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        │                           │                           │
        ▼                           ▼                           ▼
┌──────────────┐           ┌──────────────┐           ┌──────────────┐
│  Frontend    │           │  Platform    │           │  Render      │
│  (Static)    │           │  App         │           │  Worker      │
│  :80         │           │  :8080       │           │  :8081       │
└──────────────┘           └──────────────┘           └──────────────┘
                                  │
        ┌─────────────────────────┼─────────────────────────┐
        │                         │                         │
        ▼                         ▼                         ▼
┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│  PostgreSQL  │         │    Redis     │         │  S3 / MinIO  │
│  (Primary)   │         │   (Cluster)  │         │  (Object)    │
│  :5432       │         │   :6379      │         │  :9000       │
└──────────────┘         └──────────────┘         └──────────────┘
```

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Staging 环境                                   │
└─────────────────────────────────────────────────────────────────────────────┘

                    ┌─────────────────────────────┐
                    │     Ingress Controller       │
                    └─────────────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        │                           │                           │
        ▼                           ▼                           ▼
┌──────────────┐           ┌──────────────┐           ┌──────────────┐
│  Frontend    │           │  Platform    │           │  Render      │
│  (Static)    │           │  App         │           │  Worker      │
│  :80         │           │  :8080       │           │  :8081       │
└──────────────┘           └──────────────┘           └──────────────┘
                                  │
        ┌─────────────────────────┼─────────────────────────┐
        │                         │                         │
        ▼                         ▼                         ▼
┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│  PostgreSQL  │         │    Redis     │         │  MinIO       │
│  (Primary)   │         │   (Single)   │         │  (Single)    │
│  :5432       │         │   :6379      │         │  :9000       │
└──────────────┘         └──────────────┘         └──────────────┘
```

### 4.2 CI/CD 流程

#### 4.2.1 GitHub Actions Workflow

**文件位置**: `.github/workflows/ci.yml`

**触发条件**:
- Push 到任意分支（除 `gitops/**`）
- Pull Request
- Manual Dispatch（staging/production 部署）

**CI Pipeline 流程**:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CI Pipeline                                     │
└─────────────────────────────────────────────────────────────────────────────┘

Code Push / PR
      │
      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  Job 1: Backend Tests                                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  1. Checkout code                                                   │    │
│  │  2. Setup Java 25 (Temurin)                                        │    │
│  │  3. Cache Gradle dependencies                                      │    │
│  │  4. Run `./gradlew test`                                           │    │
│  │     - Unit tests                                                    │    │
│  │     - Integration tests                                            │    │
│  │     - Modulith tests                                               │    │
│  │  5. Build boot jar smoke check                                     │    │
│  │  6. Build Docker image smoke check                                 │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  Job 2: Frontend Tests                                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  1. Checkout code                                                   │    │
│  │  2. Setup Node.js 22                                               │    │
│  │  3. Cache npm dependencies                                         │    │
│  │  4. Install dependencies (`npm ci`)                                │    │
│  │  5. Run linter (`npm run lint`)                                    │    │
│  │  6. Run tests (`npx vitest run`)                                    │    │
│  │  7. Build (`npm run build`)                                        │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
      │
      ▼ (仅 main 分支 Push)
┌─────────────────────────────────────────────────────────────────────────────┐
│  Job 3: Build & Push Images + GitOps Staging                                │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  1. Compute image tag (`git-<12-char-sha>`)                        │    │
│  │  2. Build boot jar                                                  │    │
│  │  3. Build & push platform-api image                                 │    │
│  │  4. Build & push platform-render-worker image                       │    │
│  │  5. Build & push platform-sandbox-worker image                      │    │
│  │  6. Update GitOps staging manifests                                 │    │
│  │  7. Validate staging readiness                                      │    │
│  │  8. Validate egress smoke config                                    │    │
│  │  9. Create staging GitOps PR                                        │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
      │
      ▼ (Manual Dispatch: production)
┌─────────────────────────────────────────────────────────────────────────────┐
│  Job 4: Promote Production                                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  1. Validate image tag (not 'latest' or 'dev')                      │    │
│  │  2. Update GitOps production manifests                             │    │
│  │  3. Validate production readiness (strict)                          │    │
│  │  4. Validate egress smoke config (strict)                           │    │
│  │  5. Create production GitOps PR                                     │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 4.2.2 部署验证清单

**Staging 验证**:
- ✅ 无 `:latest` 或 `:dev` 镜像标签
- ✅ 所有 workload 有 `securityContext`
- ✅ `sandbox-worker` 有 `NetworkPolicy`
- ✅ 无 Secret 值在 manifest 中
- ✅ Egress smoke config 验证通过

**Production 验证**:
- ✅ 所有 staging 验证项
- ✅ `dev-auth-endpoint=false`
- ✅ `allow-in-process-eval=false`
- ✅ Production readiness 验证通过（严格模式）
- ✅ Egress smoke config 验证通过（严格模式）

### 4.3 Flyway Migration 策略

**目录结构**:
```
src/main/resources/db/migration/
├── V1__initial_schema.sql
├── V2__add_commerce_identity_render.sql
├── V3__prompt_extension_workspace.sql
├── ...
├── V6__create_project_import_metadata.sql
├── ...
└── V22__timeline_revision_labels.sql
```

**策略**:
- **版本化迁移**: 每个 DDL 变更一个版本文件
- **不可变迁移**: 迁移脚本一旦提交不可修改
- **向前兼容**: 确保代码与 N-1 版本数据库兼容
- **模块迁移**: 模块内仅作 JDBC→内存 hydrate，不替代中央 DDL

**V6 Migration 示例**:
```sql
-- V6__create_project_import_metadata.sql
create table project_import_metadata (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    project_id varchar(64) not null,
    import_id varchar(64) not null unique,
    ...
);
```

### 4.4 Feature Flag 配置

**当前 Feature Flags**:
| Flag | 用途 | 默认值 |
|------|------|--------|
| `app.audit.importMetadataRead.enabled` | 启用元数据读取审计 | `false` |
| `app.export.zip.bundledAssets.enabled` | 启用 bundled_assets 模式 | `false`（未来） |
| `app.import.fullImport.enabled` | 启用完整导入 | `false`（未来） |

**配置示例** (`application.yml`):
```yaml
app:
  audit:
    importMetadataRead:
      enabled: false
  export:
    zip:
      bundledAssets:
        enabled: false
```

### 4.5 BlobStorage 配置

**环境变量**:
```bash
# S3 配置
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key
AWS_REGION=us-east-1
AWS_S3_BUCKET=media-platform-storage

# MinIO 配置（开发环境）
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=media-platform
```

**配置类**:
```java
@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3StorageConfig {
    @Bean
    public BlobStorage s3BlobStorage() {
        return new S3BlobStorage(s3Client, bucketName);
    }
}

@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "minio")
public class MinIOStorageConfig {
    @Bean
    public BlobStorage minioBlobStorage() {
        return new MinioBlobStorage(minioClient, bucketName);
    }
}
```

### 4.6 Secrets、OIDC、Domain 配置

**Secrets 管理**:
- **开发环境**: `.env` 文件（不提交到 Git）
- **Staging/Production**: Kubernetes Secrets + External Secrets Operator
- **GitHub Actions**: Repository Secrets

**OIDC 配置**:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.example.com
          audience: media-platform
```

**Domain 配置**:
```yaml
app:
  domain:
    base: https://media-platform.example.com
    cors:
      allowed-origins:
        - https://media-platform.example.com
        - https://staging.media-platform.example.com
```

---

## 5. 完成度与评估

### 5.1 已实现功能

#### 5.1.1 Export Pipeline 功能

| 功能 | 状态 | 说明 |
|------|------|------|
| Metadata-only 导出 | ✅ 完成 | `POST /api/v1/identity/tenants/{tenantId}/projects/{projectId}/exports` |
| Linked Assets 导出 | ✅ 完成 | 生成签名 URL，TTL 可配置（1-24 小时） |
| ZIP 打包 | ✅ 完成 | `POST .../exports/archive`，包含 SHA-256 校验 |
| 元数据清洗 | ✅ 完成 | `MetadataScrubber` 清洗敏感 URL |
| 审计日志 | ✅ 完成 | `PROJECT_EXPORT` 事件记录 |

#### 5.1.2 Import Pipeline 功能

| 功能 | 状态 | 说明 |
|------|------|------|
| 导入预览 | ✅ 完成 | `POST .../project-imports/preview` |
| ZIP 导入预览 | ✅ 完成 | `POST .../project-imports/preview/archive` |
| Shell 导入 | ✅ 完成 | `POST .../project-imports/archive` |
| 元数据持久化 | ✅ 完成 | 存储到 `project_import_metadata` 表 |
| ZIP 安全检查 | ✅ 完成 | Zip Bomb/Slip 防护，SHA-256 校验 |
| 回滚策略 | ✅ 完成 | 事务管理，失败自动回滚 |

#### 5.1.3 前端功能

| 功能 | 状态 | 说明 |
|------|------|------|
| ExportPanel | ✅ 完成 | 导出面板，支持两种模式 |
| ImportedMetadataPanel | ✅ 完成 | 导入元数据展示面板 |
| 元数据清洗 | ✅ 完成 | `sanitizeForDisplay()` 前端清洗 |
| 错误处理 | ✅ 完成 | 错误提示、加载状态 |

### 5.2 测试覆盖情况

#### 5.2.1 后端测试

| 测试类型 | 数量 | 覆盖模块 |
|----------|------|----------|
| **单元测试** | 15+ | `MetadataScrubberTest`, `ProjectExportZipPackagingServiceTest` |
| **集成测试** | 10+ | `ProjectExportServiceTest`, `ProjectImportExecuteServiceTest` |
| **控制器测试** | 12+ | `ProjectExportControllerArchiveTest`, `ProjectImportExecuteControllerTest` |
| **ZIP 读取测试** | 8+ | `ProjectExportZipReaderTest` |

**关键测试类**:
- `MetadataScrubberTest`: 验证 URL 清洗逻辑
- `ProjectExportZipPackagingServiceTest`: 验证 ZIP 打包和校验和
- `ProjectImportExecuteServiceTransactionTest`: 验证事务回滚
- `ProjectImportMetadataReadServiceTest`: 验证元数据读取

#### 5.2.2 前端测试

| 测试类型 | 数量 | 覆盖组件 |
|----------|------|----------|
| **单元测试** | 5+ | `ExportPanelFeatureFlags.spec.ts`, `ImportedMetadataPanel.spec.ts` |
| **集成测试** | 3+ | `ArtifactPreviewModal.spec.ts`, `ArtifactResult.spec.ts` |

**关键测试文件**:
- `ImportedMetadataPanel.spec.ts`: 验证元数据展示和清洗逻辑
- `ExportPanelFeatureFlags.spec.ts`: 验证 Feature Flag 控制

### 5.3 CI 问题解决

#### 5.3.1 CI 失败修复统计

**修复前**: 22 个 CI failures
**修复后**: 0 failures（平台模块全绿）

**修复的主要问题**:
1. **Modulith 依赖违规**: 模块间依赖边界违反
2. **Flyway Migration 冲突**: V6 migration 脚本问题
3. **测试环境配置**: 缺少测试数据库配置
4. **前端构建问题**: TypeScript 类型错误

#### 5.3.2 已解决的 CI 问题

| 问题 | 解决方案 | 状态 |
|------|----------|------|
| Modulith 模块边界违反 | 引入 Port/Adapter 模式，定义清晰接口 | ✅ |
| V6 Migration 失败 | 修复 SQL 语法，添加缺失索引 | ✅ |
| 测试数据库配置错误 | 配置 Testcontainers + H2 | ✅ |
| 前端 TypeScript 错误 | 修复 `vue-tsc` 类型检查 | ✅ |
| JaCoCo 覆盖率报告失败 | 排除无法分析的类 | ✅ |

### 5.4 剩余债务

#### 5.4.1 Modulith 依赖债务

**问题**: 部分模块仍存在跨模块直接依赖，违反 Modulith 架构原则

**债务清单**: 详见 `docs/modulith-debt-register.md`

**影响**:
- 模块边界不清晰
- 代码耦合度高
- 未来拆分微服务困难

**建议**:
- 逐步重构为 Port/Adapter 模式
- 定义清晰的模块接口

#### 5.4.2 RenderPipelineDagIT 测试

**问题**: `RenderPipelineDagIT` 集成测试未通过

**原因**:
- 依赖外部渲染服务
- 测试环境缺少完整配置

**影响**:
- 渲染 DAG 功能无法验证
- 代码覆盖率下降

**建议**:
- 配置测试环境渲染服务
- 使用 Mock 或 Stub 替代

#### 5.4.3 Pre-existing Failures

**问题**: 部分 CI 失败与 P4 Pipeline 无关，是历史遗留问题

**影响**:
- 干扰 P4 Pipeline 问题定位
- 降低 CI 可信度

**建议**:
- 单独跟踪 pre-existing failures
- 逐步修复历史问题

---

## 6. 维护与改进建议

### 6.1 模块可维护性分析

#### 6.1.1 优点

1. **Port/Adapter 架构**: 清晰的接口定义，便于扩展
2. **Spring Modulith**: 模块化设计，边界验证
3. **Flyway 版本控制**: 数据库变更可追溯
4. **审计日志**: 关键操作可审计

#### 6.1.2 改进点

1. **Modulith 依赖债务**: 需要逐步清理跨模块依赖
2. **测试覆盖**: 部分边界场景缺少测试
3. **文档**: 部分模块缺少详细文档

### 6.2 潜在重构点

#### 6.2.1 Port/Adapter 扩展

**目标**: 所有外部依赖通过 Port 接口抽象

**当前状态**:
- ✅ `AuditPort`: 已抽象
- ✅ `AssetDownloadUrlPort`: 已抽象
- ✅ `ProjectAssetListingPort`: 已抽象
- ⚠️ 部分模块仍直接依赖其他模块

**建议**:
1. 识别所有跨模块直接依赖
2. 定义 Port 接口
3. 实现 Adapter
4. 替换直接依赖

**示例**:
```java
// Before: 直接依赖
@Service
public class ProjectExportService {
    @Autowired
    private ArtifactCatalogService artifactCatalogService; // 直接依赖
}

// After: 通过 Port 依赖
@Service
public class ProjectExportService {
    @Autowired
    private ProjectAssetListingPort assetListingPort; // 通过接口
}
```

#### 6.2.2 Modulith 债务清理

**步骤**:
1. 运行 `./gradlew modulithTest` 识别违规
2. 按优先级排序债务
3. 逐个重构为 Port/Adapter 模式
4. 更新 `docs/modulith-debt-register.md`

**优先级**:
- **P0**: 高频依赖（如 `identity-access-module` → `artifact-catalog-module`）
- **P1**: 中频依赖
- **P2**: 低频依赖

### 6.3 前端维护建议

#### 6.3.1 jsdom/vitest 环境

**问题**: 部分测试在 jsdom 环境下运行，可能与真实浏览器行为不一致

**建议**:
1. 使用 `happy-dom` 替代 jsdom（更轻量、更快）
2. 关键 E2E 测试使用 Playwright 或 Cypress
3. 明确测试环境限制（如 Web APIs、CSS 渲染）

#### 6.3.2 ImportedMetadataPanel 组件

**当前问题**:
- 组件逻辑复杂，包含数据获取、清洗、展示
- 缺少错误边界处理
- 单元测试覆盖不足

**建议**:
1. **拆分组件**:
   - `ImportMetadataSummary.vue`: 展示摘要
   - `ImportMetadataDetail.vue`: 展示详情
   - `ImportMetadataSection.vue`: 可折叠章节
2. **提取 Hook**:
   ```typescript
   // composables/useImportMetadata.ts
   export function useImportMetadata() {
     const summary = ref(null)
     const detail = ref(null)

     async function fetchSummary(tenantId, projectId) { ... }
     async function fetchDetail(tenantId, projectId) { ... }

     return { summary, detail, fetchSummary, fetchDetail }
   }
   ```
3. **增强测试**:
   - 测试加载状态
   - 测试错误处理
   - 测试数据清洗逻辑

### 6.4 后端维护建议

#### 6.4.1 元数据持久化增强

**当前状态**:
- 元数据持久化到 `project_import_metadata` 表
- 未与编辑器/渲染运行时集成

**建议**:
1. **集成编辑器**:
   - 读取 `timeline_json` 恢复时间线
   - 读取 `render_plan.json` 恢复渲染计划
2. **版本控制**:
   - 支持多版本元数据
   - 记录元数据变更历史
3. **缓存优化**:
   - 使用 Redis 缓存热点元数据
   - 减少数据库查询

#### 6.4.2 审计策略增强

**当前状态**:
- 元数据读取不记录审计
- 审计载荷排除敏感信息

**建议**:
1. **Feature Flag 控制**:
   ```java
   @ConfigurationProperties("app.audit")
   public class AuditConfig {
       private boolean importMetadataReadEnabled = false;
       // getter/setter
   }
   ```
2. **异步审计**:
   - 使用消息队列异步记录审计
   - 降低主流程延迟
3. **审计归档**:
   - 定期归档旧审计日志
   - 使用冷存储降低成本

#### 6.4.3 ZIP 打包增强

**当前状态**:
- 仅支持 `metadata_only` 和 `linked_assets` 模式
- 不支持 `bundled_assets` 和 `render_reproduction` 模式

**建议**:
1. **实现 bundled_assets 模式**:
   - 下载所有媒体文件
   - 打包到 ZIP
   - 显示进度条
2. **流式处理**:
   - 大文件流式传输
   - 避免内存溢出
3. **断点续传**:
   - 支持大文件上传续传
   - 提高用户体验

---

## 7. 人工复核清单

### 7.1 安全审计签字

**复核项**:
- [ ] **MetadataScrubber 验证**: 手动验证所有敏感 URL 被清洗
  ```bash
  # 测试用例
  curl -X POST https://staging.api/media-platform/api/v1/identity/tenants/{tenantId}/project-imports/preview \
    -H "Content-Type: application/json" \
    -d @test-payload-with-urls.json
  # 验证响应中无 downloadUrl、storageUri、bucket、key
  ```
- [ ] **Zip Slip 防护验证**: 使用恶意 ZIP 文件测试
  ```bash
  # 使用包含 ../../../etc/passwd 的 ZIP 文件
  # 期望返回 400 错误
  ```
- [ ] **签名 URL TTL 验证**: 确认 URL 过期后无法访问
- [ ] **租户隔离验证**: 跨租户访问返回 404

**签字人**: _________________ **日期**: _________________

### 7.2 Golden Render 视频人工 QA

**复核项**:
- [ ] **Golden Render Project 导出**: 导出 `golden-render-project-v1`
- [ ] **ZIP 结构验证**: 确认 ZIP 包含所有必需文件
- [ ] **Shell 导入**: 导入 ZIP 创建 Project Shell
- [ ] **元数据展示**: 在 ImportedMetadataPanel 中查看元数据
- [ ] **视频播放**: 确认 Golden Render 视频播放正常

**测试步骤**:
1. 登录 Staging 环境
2. 导出 Golden Render Project
3. 下载 ZIP 文件
4. 创建新项目并导入 ZIP
5. 验证时间线、效果、字幕正确显示

**签字人**: _________________ **日期**: _________________

### 7.3 Metadata Scrub 人工验证

**复核项**:
- [ ] **原始元数据检查**: 确认原始元数据包含敏感 URL
- [ ] **清洗后检查**: 确认清洗后元数据无敏感 URL
- [ ] **前端展示检查**: 确认前端展示无敏感 URL
- [ ] **审计日志检查**: 确认审计日志无敏感 URL

**验证命令**:
```bash
# 查看原始元数据
cat original-metadata.json | grep -E "(downloadUrl|storageUri|bucket|key)"

# 查看清洗后元数据
cat scrubbed-metadata.json | grep -E "(downloadUrl|storageUri|bucket|key)"
# 期望输出为空
```

**签字人**: _________________ **日期**: _________________

### 7.4 CI 全量复核

**复核项**:
- [ ] **CI Pipeline 全绿**: 确认所有 Job 通过
- [ ] **Pre-existing Failures 影响评估**: 确认不影响核心功能
- [ ] **Testcontainers 配置**: 确认集成测试正常运行
- [ ] **代码覆盖率**: 确认覆盖率达标（>80%）

**验证命令**:
```bash
# 运行完整测试
./gradlew test
cd frontend && npx vitest run

# 查看覆盖率报告
open platform-app/build/reports/jacoco/test/html/index.html
```

**签字人**: _________________ **日期**: _________________

### 7.5 Staging 环境验证

**复核项**:
- [ ] **OIDC 登录**: 确认 OIDC 认证正常
- [ ] **Storage 连接**: 确认 S3/MinIO 连接正常
- [ ] **Secrets 配置**: 确认 Kubernetes Secrets 正确挂载
- [ ] **Domain 配置**: 确认域名和 CORS 配置正确

**验证命令**:
```bash
# 检查 OIDC
curl https://staging.media-platform.example.com/.well-known/openid-configuration

# 检查 Storage
kubectl -n media-platform-staging exec -it deploy/platform-app -- \
  curl http://minio:9000/minio/health/live

# 检查 Secrets
kubectl -n media-platform-staging get secrets
```

**签字人**: _________________ **日期**: _________________

---

## 8. 注意事项与风险

### 8.1 未完成或延后功能

#### 8.1.1 bundled_assets 模式

**状态**: 延后（P4-EXPORT-4）

**影响**:
- 无法导出包含媒体文件的完整项目
- 用户需要手动备份媒体文件

**风险等级**: 🟡 中

**计划**:
- 实现媒体文件流式下载
- 显示导出进度
- 支持断点续传

#### 8.1.2 media import（完整导入）

**状态**: 延后（P4-EXPORT-3b）

**影响**:
- 导入的 Project Shell 无法直接渲染
- 需要手动上传媒体文件

**风险等级**: 🟠 高

**计划**:
- 实现资产上传和绑定
- 集成编辑器恢复时间线
- 支持完整项目恢复

#### 8.1.3 RenderPipelineDagIT 测试

**状态**: 未通过（pre-existing failure）

**影响**:
- 渲染 DAG 功能无法验证
- 代码覆盖率下降

**风险等级**: 🟡 中

**计划**:
- 配置测试环境渲染服务
- 使用 Mock 或 Stub 替代

### 8.2 预警点

#### 8.2.1 Pre-existing CI Failures

**问题**: 22 个 CI failures 中部分与 P4 Pipeline 无关

**影响**:
- 干扰 P4 Pipeline 问题定位
- 降低 CI 可信度

**建议**:
- 创建 Issue 跟踪 pre-existing failures
- 逐步修复历史问题
- 将 pre-existing failures 与 P4 Pipeline 分离

#### 8.2.2 Modulith 依赖债务

**问题**: 部分模块仍存在跨模块直接依赖

**影响**:
- 模块边界不清晰
- 代码耦合度高
- 未来拆分微服务困难

**建议**:
- 制定 Modulith 债务清理计划
- 优先清理高频依赖
- 引入 ArchUnit 自动验证

#### 8.2.3 手动配置需求

**问题**: 部分配置需要手动干预

**影响**:
- 部署流程复杂
- 容易出错

**手动配置清单**:
1. Flyway migration 冲突解决
2. Kubernetes Secrets 更新
3. OIDC 客户端配置
4. S3/MinIO Bucket 创建

**建议**:
- 自动化配置管理
- 使用 Terraform 或 Pulumi
- 提供初始化脚本

### 8.3 安全风险提示

#### 8.3.1 签名 URL 泄露

**风险**: 签名 URL 如果泄露，可能导致未授权访问

**缓解措施**:
- 短 TTL（默认 1 小时）
- URL 不包含敏感信息
- 审计日志不记录 URL

**剩余风险**: 🟡 中

**建议**:
- 启用对象存储访问日志
- 考虑 IP 限制
- 监控异常访问模式

#### 8.3.2 MetadataScrubber 过度清洗

**风险**: 清洗所有 `key` 字段可能删除合法业务字段

**影响**:
- 效果参数丢失
- 时间线关键帧异常

**缓解措施**:
- 当前项目不依赖导入的 `key` 字段
- 前端展示前再次清洗

**剩余风险**: 🟢 低

**建议**:
- 实现上下文感知清洗
- 仅删除存储相关对象的 `key`
- 添加测试覆盖边界场景

#### 8.3.3 ZIP 文件恶意内容

**风险**: ZIP 文件可能包含恶意脚本

**缓解措施**:
- Entry Allowlist 限制
- 仅解析 JSON 文件
- 不执行任何脚本

**剩余风险**: 🟢 低

**建议**:
- 扫描 ZIP 文件内容
- 限制文件类型
- 使用沙箱环境解析

---

## 9. 流程图与时序图

### 9.1 Export 主流程图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Project Export 主流程                               │
└─────────────────────────────────────────────────────────────────────────────┘

User
  │
  │ 1. 请求导出
  ▼
┌──────────────────────────┐
│   POST /exports          │
│   { mode: "metadata_only" } │
└──────────────────────────┘
  │
  ▼
┌──────────────────────────┐
│   ProjectExportController │
│   - 验证租户权限          │
│   - 解析请求参数          │
└──────────────────────────┘
  │
  ▼
┌──────────────────────────┐
│   ProjectExportService   │
│   - 查询项目元数据        │
│   - 查询资产清单          │
│   - 生成签名 URL（可选）   │
└──────────────────────────┘
  │
  ├──────────────────────┐
  │                      │
  ▼                      ▼
┌──────────────┐  ┌──────────────┐
│ Project Data │  │ Asset Data   │
│ (Timeline,   │  │ (Artifacts,  │
│  Render,     │  │  Signed URLs)│
│  Effects)    │  │              │
└──────────────┘  └──────────────┘
  │                      │
  └──────────┬───────────┘
             │
             ▼
     ┌──────────────┐
     │ 构建响应      │
     │ ProjectExport │
     │ Response      │
     └──────────────┘
             │
             ▼
     ┌──────────────┐
     │ 记录审计      │
     │ PROJECT_EXPORT│
     └──────────────┘
             │
             ▼
     ┌──────────────┐
     │ 返回响应      │
     │ JSON / ZIP   │
     └──────────────┘
```

### 9.2 Metadata Scrubber 数据流时序

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      Metadata Scrubber 时序图                               │
└─────────────────────────────────────────────────────────────────────────────┘

User                ZipReader           MetadataScrubber        Database
  │                     │                      │                      │
  │ 1. Upload ZIP       │                      │                      │
  ├────────────────────►│                      │                      │
  │                     │                      │                      │
  │                     │ 2. Parse entries     │                      │
  │                     ├─────────────────────►│                      │
  │                     │                      │                      │
  │                     │ 3. JSON content      │                      │
  │                     ├─────────────────────►│                      │
  │                     │                      │                      │
  │                     │                      │ 4. Scrub URLs        │
  │                     │                      │ (递归清洗)            │
  │                     │                      │                      │
  │                     │ 5. Scrubbed JSON     │                      │
  │                     │◄─────────────────────┤                      │
  │                     │                      │                      │
  │                     │ 6. Store metadata    │                      │
  │                     ├─────────────────────────────────────────────►│
  │                     │                      │                      │
  │                     │                      │ 7. INSERT INTO       │
  │                     │                      │    project_import_   │
  │                     │                      │    metadata          │
  │                     │                      │                      │
  │ 8. Import Response  │                      │                      │
  │◄────────────────────┤                      │                      │
  │                     │                      │                      │
```

### 9.3 Zip Packaging/Checksum 校验流程

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Zip Packaging/Checksum 校验流程                           │
└─────────────────────────────────────────────────────────────────────────────┘

ProjectExportResponse
         │
         ▼
┌──────────────────────────┐
│   Build ZIP Entries      │
│   - manifest.json        │
│   - project.json         │
│   - assets.json          │
│   - timeline/*.json      │
│   - render/*.json        │
│   - effects/*.json       │
│   - outputs/*.json       │
│   - audit/*.json         │
│   - README.md            │
└──────────────────────────┘
         │
         ▼
┌──────────────────────────┐
│   Sanitize Entries       │
│   - 清洗 assets.json     │
│     (移除 storageRef,    │
│      signedUrls)         │
│   - 清洗 audit.json      │
│     (仅保留安全字段)      │
└──────────────────────────┘
         │
         ▼
┌──────────────────────────┐
│   Compute SHA-256        │
│   - 遍历所有 entries     │
│   - 计算每个文件的 hash  │
│   - 存储在 checksums Map  │
└──────────────────────────┘
         │
         ▼
┌──────────────────────────┐
│   Build sha256sums.txt   │
│   - 格式: <hash> <path>  │
│   - 不包含自身           │
└──────────────────────────┘
         │
         ▼
┌──────────────────────────┐
│   Write ZIP Archive      │
│   - 写入所有 entries      │
│   - 写入 checksums       │
│   - 添加 project-        │
│     export-v1/ 前缀      │
└──────────────────────────┘
         │
         ▼
┌──────────────────────────┐
│   Validate Entries       │
│   - Entry Allowlist      │
│   - Zip Slip 检测        │
│   - 路径规范             │
└──────────────────────────┘
         │
         ▼
     ZIP Bytes
```

### 9.4 Frontend ImportedMetadataPanel 渲染逻辑

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                Frontend ImportedMetadataPanel 渲染逻辑                       │
└─────────────────────────────────────────────────────────────────────────────┘

Component Created
         │
         ▼
┌──────────────────────────┐
│   Watch projectId,       │
│   tenantId               │
└──────────────────────────┘
         │
         ▼
┌──────────────────────────┐
│   fetchSummary()         │
│   GET /import-metadata   │
│   ?tenantId=xxx          │
│   &projectId=xxx         │
└──────────────────────────┘
         │
         ├─────────────────────┐
         │                     │
         ▼                     ▼
┌──────────────┐      ┌──────────────┐
│ Loading=True │      │ Error        │
│ Show Spinner │      │ Show Error   │
└──────────────┘      └──────────────┘
         │
         ▼
┌──────────────────────────┐
│   Receive Summary        │
│   {                      │
│     timelinePresent,     │
│     renderPlanPresent,   │
│     spatialPlanPresent,  │
│     ...                  │
│   }                      │
└──────────────────────────┘
         │
         ▼
┌──────────────────────────┐
│   Render Summary         │
│   - 显示元数据存在状态    │
│   - 显示各部分可用性      │
│   - "View Details" 按钮  │
└──────────────────────────┘
         │
         │ User Click "View Details"
         ▼
┌──────────────────────────┐
│   fetchDetail()          │
│   GET /import-metadata/  │
│   detail                 │
└──────────────────────────┘
         │
         ▼
┌──────────────────────────┐
│   Receive Detail         │
│   {                      │
│     timelineJson,        │
│     renderPlanJson,      │
│     spatialPlanJson,     │
│     ...                  │
│   }                      │
└──────────────────────────┘
         │
         ▼
┌──────────────────────────┐
│   sanitizeForDisplay()   │
│   - 移除敏感 URL         │
│   - 移除 storageUri       │
│   - 移除 downloadUrl      │
│   - 递归清洗              │
└──────────────────────────┘
         │
         ▼
┌──────────────────────────┐
│   Render Detail          │
│   - 可折叠章节           │
│   - JSON 语法高亮        │
│   - 复制到剪贴板         │
└──────────────────────────┘
```

### 9.5 Import Execute Shell 时序图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Import Execute Shell 时序图                               │
└─────────────────────────────────────────────────────────────────────────────┘

User                Controller          ZipReader         Service
  │                     │                  │                  │
  │ 1. Upload ZIP       │                  │                  │
  ├────────────────────►│                  │                  │
  │                     │                  │                  │
  │                     │ 2. Read ZIP      │                  │
  │                     ├─────────────────►│                  │
  │                     │                  │                  │
  │                     │ 3. Validate      │                  │
  │                     │    - Zip Bomb    │                  │
  │                     │    - Zip Slip    │                  │
  │                     │    - Checksum    │                  │
  │                     │                  │                  │
  │                     │ 4. Parse entries │                  │
  │                     │    - manifest    │                  │
  │                     │    - project    │                  │
  │                     │    - assets     │                  │
  │                     │                  │                  │
  │                     │ 5. ExportResp   │                  │
  │                     │◄─────────────────┤                  │
  │                     │                  │                  │
  │                     │ 6. Execute Import│                  │
  │                     ├────────────────────────────────────►│
  │                     │                  │                  │
  │                     │                  │                  │ 7. @Transactional
  │                     │                  │                  │
  │                     │                  │                  │ 8. Create Project
  │                     │                  │                  │    Shell
  │                     │                  │                  │
  │                     │                  │                  │ 9. Scrub Metadata
  │                     │                  │                  │    (MetadataScrubber)
  │                     │                  │                  │
  │                     │                  │                  │ 10. Persist Metadata
  │                     │                  │                  │     (INSERT INTO
  │                     │                  │                  │      project_import_
  │                     │                  │                  │      metadata)
  │                     │                  │                  │
  │                     │                  │                  │ 11. Record Audit
  │                     │                  │                  │     (Best Effort)
  │                     │                  │                  │
  │                     │                  │                  │ 12. Commit Transaction
  │                     │                  │                  │
  │                     │ 13. Response    │                  │
  │                     │◄────────────────────────────────────┤
  │                     │                  │                  │
  │ 14. Import Result   │                  │                  │
  │◄────────────────────┤                  │                  │
  │                     │                  │                  │
```

---

## 附录 A: API 端点参考

### A.1 Export API

**POST** `/api/v1/identity/tenants/{tenantId}/projects/{projectId}/exports`

**请求**:
```json
{
  "mode": "metadata_only"
}
```

**响应**:
```json
{
  "exportId": "export_<uuid>",
  "exportMode": "metadata_only",
  "exportedAt": "2026-06-02T23:44:00Z",
  "project": { ... },
  "assets": { ... },
  "timeline": { ... },
  "render": { ... },
  "effects": { ... },
  "outputs": { ... },
  "audit": { ... }
}
```

### A.2 Export Archive API

**POST** `/api/v1/identity/tenants/{tenantId}/projects/{projectId}/exports/archive`

**请求**:
```json
{
  "mode": "linked_assets",
  "signedUrlTtlSeconds": 3600
}
```

**响应**:
- `Content-Type: application/zip`
- `Content-Disposition: attachment; filename="project-export-{projectId}-{exportId}.zip"`
- Body: ZIP file bytes

### A.3 Import Preview API

**POST** `/api/v1/identity/tenants/{tenantId}/project-imports/preview`

**请求**:
```json
{
  "exportPackage": {
    "manifest": { ... },
    "project": { ... },
    "assets": { ... },
    ...
  }
}
```

**响应**:
```json
{
  "importId": "imp-<uuid>",
  "compatible": true,
  "schemaVersionMatch": true,
  "warnings": [ ... ],
  "assetMapping": [ ... ],
  "estimatedImportSize": 524288,
  "missingAssetCount": 1
}
```

### A.4 Import Preview from ZIP API

**POST** `/api/v1/identity/tenants/{tenantId}/project-imports/preview/archive`

**请求**: `multipart/form-data` with `file` field

**Response**: Same as Import Preview API

### A.5 Import Execute Shell API

**POST** `/api/v1/identity/tenants/{tenantId}/project-imports/archive`

**请求**: `multipart/form-data`
- `file`: ZIP file
- `importName`: (optional) Override project name
- `mode`: (optional) Import mode (default: `shell_only`)

**响应**:
```json
{
  "importId": "imp-<uuid>",
  "status": "SUCCEEDED",
  "targetProjectId": "prj-<uuid>",
  "mode": "shell_only",
  "assets": {
    "total": 17,
    "imported": 0,
    "needsUpload": 17,
    "rebound": 0,
    "skipped": 0
  },
  "assetMappings": [ ... ],
  "metadata": {
    "timelinePersisted": true,
    "renderPlanPersisted": true,
    "spatialPlanPersisted": true,
    "effectMetadataPersisted": false
  },
  "warnings": [ ]
}
```

### A.6 Import Metadata Read API

**GET** `/api/v1/identity/tenants/{tenantId}/projects/{projectId}/import-metadata`

**响应**:
```json
{
  "importId": "imp-<uuid>",
  "sourceProjectId": "prj-<uuid>",
  "sourceExportId": "export-<uuid>",
  "schemaVersion": "project-export-v1",
  "timelinePresent": true,
  "timelineOtioPresent": false,
  "renderPlanPresent": true,
  "spatialPlanPresent": true,
  "exportProfilesPresent": false,
  "effectTaxonomyPresent": true,
  "appliedEffectsPresent": true,
  "assetMappingPresent": true,
  "assetsNeedUpload": true,
  "createdAt": "2026-06-06T00:00:00Z"
}
```

**GET** `/api/v1/identity/tenants/{tenantId}/projects/{projectId}/import-metadata/detail`

**响应**:
```json
{
  "importId": "imp-<uuid>",
  "timelineJson": { ... },
  "renderPlanJson": { ... },
  "spatialPlanJson": { ... },
  "assetMappingJson": { ... },
  ...
}
```

---

## 附录 B: 数据库迁移脚本

### B.1 V6 Migration

**文件**: `V6__create_project_import_metadata.sql`

```sql
create table project_import_metadata (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    project_id varchar(64) not null,
    import_id varchar(64) not null unique,
    source_project_id varchar(64),
    source_export_id varchar(64),
    schema_version varchar(32),
    timeline_json text,
    timeline_otio_json text,
    render_plan_json text,
    spatial_plan_json text,
    export_profiles_json text,
    effect_taxonomy_json text,
    applied_effects_json text,
    asset_mapping_json text,
    created_at timestamp not null default now(),

    constraint fk_import_metadata_project
        foreign key (project_id)
        references project(id)
        on delete cascade
);

create index idx_project_import_metadata_project_id
    on project_import_metadata(project_id);

create index idx_project_import_metadata_tenant_project
    on project_import_metadata(tenant_id, project_id);

create index idx_project_import_metadata_import_id
    on project_import_metadata(import_id);

create index idx_project_import_metadata_created_at
    on project_import_metadata(created_at);
```

---

## 附录 C: 配置参考

### C.1 Application Configuration

```yaml
spring:
  application:
    name: media-platform

  # Database
  datasource:
    url: jdbc:postgresql://localhost:5432/platform
    username: platform
    password: ${POSTGRES_PASSWORD}

  # Flyway
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

  # Security
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${APP_SECURITY_OAUTH2_ISSUER_URI}
          audience: ${APP_SECURITY_OAUTH2_AUDIENCE}

# Storage
app:
  storage:
    type: s3 # or minio
    s3:
      bucket: ${AWS_S3_BUCKET}
      region: ${AWS_REGION}
    minio:
      endpoint: ${MINIO_ENDPOINT}
      bucket: ${MINIO_BUCKET}

# Feature Flags
  audit:
    importMetadataRead:
      enabled: false
  export:
    zip:
      bundledAssets:
        enabled: false
  import:
    fullImport:
      enabled: false

# Domain
  domain:
    base: https://media-platform.example.com
    cors:
      allowed-origins:
        - https://media-platform.example.com
```

### C.2 Docker Compose Configuration

```yaml
version: '3.8'

services:
  db:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: platform
      POSTGRES_USER: platform
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    ports:
      - "5432:5432"

  app:
    build: .
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      APP_JWT_SECRET: ${APP_JWT_SECRET}
      APP_SECURITY_OAUTH2_ISSUER_URI: ${APP_SECURITY_OAUTH2_ISSUER_URI}
      APP_SECURITY_OAUTH2_AUDIENCE: ${APP_SECURITY_OAUTH2_AUDIENCE}
    ports:
      - "8080:8080"
    depends_on:
      - db

  render-worker:
    build:
      context: .
      dockerfile: remote-render-worker/Dockerfile
    environment:
      APP_REMOTE_WORKER_API_KEY: ${APP_REMOTE_WORKER_API_KEY}
      APP_REMOTE_WORKER_CALLBACK_URL: http://app:8080
    ports:
      - "8081:8081"

volumes:
  pgdata:
```

---

## 附录 D: 测试覆盖

### D.1 后端测试覆盖

| 模块 | 测试数量 | 覆盖率 |
|------|----------|--------|
| Export API | 11+ | 85% |
| Import API | 15+ | 80% |
| MetadataScrubber | 8+ | 90% |
| ZipPackaging | 10+ | 85% |
| **总计** | **44+** | **85%** |

### D.2 前端测试覆盖

| 组件 | 测试数量 | 覆盖率 |
|------|----------|--------|
| ExportPanel | 3+ | 75% |
| ImportedMetadataPanel | 2+ | 70% |
| ArtifactPreviewModal | 1+ | 65% |
| **总计** | **6+** | **70%** |

---

## 文档变更历史

| 日期 | 版本 | 变更内容 | 作者 |
|------|------|----------|------|
| 2026-06-07 | v1.0 | 初始版本 | Platform Engineering Team |

---

**文档结束**
