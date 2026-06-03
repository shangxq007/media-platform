# 多用户协作与资源授权功能文档

**最后更新:** 2026-05-19T22:30:00+08:00
**状态标记:** ✅ 已实现 / ⚠️ 部分实现 / 🔧 Stub / 📋 Future / 🔴 Blocker / 🧪 需人工复核

---

## 概述

媒体平台的多用户协作与资源授权系统提供了完整的工作区协作、项目共享、权限管理和访问控制功能。该系统支持团队成员之间的实时协作、细粒度的资源授权以及完整的审计跟踪。

### 核心特性

- **工作区协作**: 基于工作区的团队项目管理
- **资源授权**: 所有者、团队、组和ACL模型
- **决策链**: 完整的身份验证到访问决策流程
- **管理控制台**: 全面的策略管理界面
- **用户门户**: MyCapabilities页面显示可用资源和权限
- **审计日志**: 所有操作和决策的完整记录

---

## 架构设计

### 整体架构

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   用户认证      │───▶│ ACL/组/角色检查  │───▶│ ABAC条件判断    │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                                         │
┌─────────────────┐    ┌──────────────────┐           ▼
│ Feature Flags   │───▶│ Entitlements     │───▶ ABAC决策结果
└─────────────────┘    └──────────────────┘           │
                                                         │
┌─────────────────┐    ┌──────────────────┐           ▼
│ Quota Policy    │───▶│ Budget Guard     │───▶ AccessDecision
└─────────────────┘    └──────────────────┘    ┌───────────────┐
                                                   │ 最终决策结果  │
                                                   └───────────────┘
```

### 主要模块

- **identity-access-module**: 身份验证和访问控制
- **policy-governance-module**: 策略治理和Feature Flag
- **entitlement-module**: 权益管理和配额
- **audit-compliance-module**: 审计和合规模块
- **frontend**: Admin Console和MyCapabilitiesPage

---

## 工作区协作功能

### ✅ 已实现的功能

#### 工作区管理
```java
// 工作区成员管理
public record WorkspaceMember(
    String id,
    String workspaceId,
    String userId,
    String role,          // OWNER, ADMIN, MEMBER, VIEWER
    MemberStatus status,  // ACTIVE, INACTIVE, REMOVED
    Instant joinedAt,
    Instant updatedAt
)

// 工作区组管理
public record WorkspaceGroup(
    String id,
    String workspaceId,
    String name,
    String description,
    Instant createdAt
)
```

#### API端点
- `GET /api/v1/workspaces` - 列出用户的所有工作区
- `POST /api/v1/workspaces/{id}/members` - 添加工作区成员
- `PUT /api/v1/workspaces/{id}/members/{userId}/role` - 更新成员角色
- `DELETE /api/v1/workspaces/{id}/members/{userId}` - 移除成员

#### 前端组件
- **WorkspaceMembersPage**: 工作区成员管理界面
- **WorkspaceGroupGrantPanel**: 工作组权限分配
- **AccessDecisionDebugPanel**: 访问决策调试面板

---

## 资源授权模型

### ✅ 已实现的模式

#### 1. 所有者模型 (Owner Model)
- 资源创建者自动成为所有者
- 所有者可以转让所有权
- 所有者有完全控制权

#### 2. 团队模型 (Team Model)
- 工作区级别的团队协作
- 基于角色的权限继承
- 支持多个团队成员

#### 3. 组模型 (Group Model)
- 嵌套的组层次结构
- 组内权限继承
- 批量组成员管理

#### 4. ACL模型 (Access Control List)
- 细粒度的资源级权限控制
- 支持READ, WRITE, DELETE等操作
- 可配置的时间限制

### 权限类型

| 权限类型 | 描述 | 范围 |
|---------|------|------|
| READ | 查看资源内容 | 全局/工作区/项目/资源 |
| WRITE | 修改资源内容 | 全局/工作区/项目/资源 |
| DELETE | 删除资源 | 全局/工作区/项目/资源 |
| ADMIN | 管理权限 | 全局/工作区/项目 |

---

## 后端决策链

### ✅ 完整的决策流程

```java
public AccessDecision makeDecision(AccessCheckRequest request) {
    // 1. 用户身份验证
    User user = authenticationService.verify(request.userId());

    // 2. ACL/组/角色检查
    Set<String> permissions = permissionService.resolvePermissions(
        user.id(), request.workspaceId());

    // 3. ABAC属性检查
    boolean abacResult = abacEngine.evaluate(request);

    // 4. Feature Flag检查
    FeatureFlagAccessResult ffResult =
        featureFlagService.evaluateForAccessDecision(request);

    // 5. 权益验证
    ExportValidationResult entitlementResult =
        entitlementPort.validateExport(...);

    // 6. 配额和预算检查
    QuotaResult quotaResult = quotaService.checkQuota(...);
    BudgetStatus budgetStatus = budgetGuardService.checkBudget(...);

    // 7. 最终决策
    return new AccessDecision(
        permissions, abacResult, ffResult,
        entitlementResult, quotaResult, budgetStatus);
}
```

### 决策服务组件

- **AuthenticationService**: 用户身份验证
- **PermissionService**: ACL和权限解析
- **ABACPolicyEngine**: 属性基访问控制
- **FeatureFlagService**: Feature Flag评估
- **EntitlementService**: 权益策略评估
- **QuotaPolicyService**: 配额策略检查
- **BudgetGuardService**: 预算和成本检查
- **AccessDecisionService**: 最终决策聚合

---

## 管理控制台

### ✅ 已实现的管理功能

#### 1. 角色管理
```typescript
interface RoleManagement {
    createRole(key: string, name: string, scope: 'GLOBAL' | 'WORKSPACE');
    assignRole(userId: string, roleKey: string);
    revokeRole(userId: string, roleKey: string);
    listRoles(): Role[];
}
```

#### 2. 权限管理
```typescript
interface PermissionManagement {
    grantPermission(resourceId: string, permission: string);
    revokePermission(resourceId: string, permission: string);
    checkPermission(resourceId: string): boolean;
}
```

#### 3. 团队管理
```typescript
interface TeamManagement {
    addMember(workspaceId: string, userId: string);
    removeMember(workspaceId: string, userId: string);
    updateMemberRole(workspaceId: string, userId: string, role: string);
}
```

#### 4. ACL管理
```typescript
interface ACLManagement {
    setResourceACL(resourceId: string, acl: AclEntry[]);
    getResourceACL(resourceId: string): AclEntry[];
    auditACLChanges(resourceId: string): AuditLog[];
}
```

### 前端管理页面

- **AdminConsole**: 主管理控制台
- **RoleManagementPanel**: 角色管理面板
- **PermissionEditor**: 权限编辑器
- **TeamManager**: 团队管理器
- **ACLViewer**: ACL查看器
- **AuditLogger**: 审计日志查看器
- **AccessDecisionDebugger**: 访问决策调试器

---

## 用户门户 (MyCapabilitiesPage)

### ✅ 已实现的用户体验

#### 数据模型
```typescript
interface MyCapabilities {
    tier: 'FREE' | 'PRO' | 'TEAM' | 'ENTERPRISE';
    entitlementPolicy: EntitlementPolicy;
    exportCapabilities: ExportCapabilities;
    featureFlags: FeatureFlag[];
    accessibleResources: Resource[];
    permissionStatus: PermissionStatus[];
}

interface EntitlementPolicy {
    monthlyRenderMinutes: number;
    maxConcurrentJobs: number;
    maxResolutionWidth: number;
    maxResolutionHeight: number;
    gpuAllowed: boolean;
    remoteWorkerAllowed: boolean;
    customFontsAllowed: boolean;
    watermark: boolean;
}
```

#### UI组件
- **CurrentPlanPanel**: 当前计划展示
- **UsageSummaryPanel**: 使用量统计
- **UpgradeSuggestionPanel**: 升级建议
- **FeatureFlagsSection**: Feature Flag状态
- **EntitlementPolicySection**: 权益策略详情
- **ExportCapabilitiesSection**: 导出能力展示

#### API接口
- `GET /api/v1/entitlements/me/capabilities` - 获取用户能力信息
- `POST /api/v1/access/check` - 检查特定访问权限
- `GET /api/v1/audit/recent` - 获取最近的审计记录

---

## 审计和日志记录

### ✅ 完整的审计系统

#### 审计事件类型
```java
public enum AuditCategory {
    ACCESS_DECISION,      // 访问决策
    RESOURCE_ACCESS,      // 资源访问
    PERMISSION_CHANGE,    // 权限变更
    ROLE_ASSIGNMENT,      // 角色分配
    TEAM_MANAGEMENT,      // 团队管理
    POLICY_UPDATE,        // 策略更新
    USER_LOGIN,           // 用户登录
    USER_LOGOUT           // 用户登出
}
```

#### 审计记录结构
```java
public class AuditRecord {
    String id;                    // 审计记录ID
    String actorType;             // 执行者类型 (USER, SYSTEM, API_KEY)
    String actorId;               // 执行者ID
    String action;                // 执行的操作
    String resourceType;          // 资源类型
    String resourceId;            // 资源ID
    Object payload;               // 操作负载
    AuditCategory category;       // 审计类别
    OffsetDateTime createdAt;     // 创建时间
}
```

#### 审计服务
```java
@Service
public class AuditService {
    public String record(String actorType, String actorId, String action,
                        String resourceType, String resourceId, Object payload);
    public List<Map<String, Object>> recent(int limit);
    public List<Map<String, Object>> findByCategory(AuditCategory category, int limit);
    public List<Map<String, Object>> findByResource(String resourceType, String resourceId);
}
```

#### 前端审计界面
- **AuditLogViewer**: 审计日志查看器
- **AuditFilterPanel**: 审计筛选面板
- **AuditExportButton**: 审计导出功能
- **RealTimeAuditFeed**: 实时审计流

---

## API接口文档

### 协作API

#### 工作区API
```http
GET /api/v1/workspaces
Authorization: Bearer <token>
Response: Workspace[]

POST /api/v1/workspaces/{id}/members
{
  "userId": "string",
  "role": "OWNER|ADMIN|MEMBER|VIEWER"
}
```

#### 项目分享API
```http
GET /api/v1/projects/shared
Authorization: Bearer <token>
Response: Project[]

POST /api/v1/projects/{id}/share
{
  "userIds": ["string"],
  "groupIds": ["string"],
  "permissions": ["READ", "WRITE"]
}
```

### 授权API

#### 访问检查API
```http
POST /api/v1/access/check
{
  "userId": "string",
  "resourceType": "PROJECT|RENDER_JOB|ARTIFACT",
  "resourceId": "string",
  "action": "READ|WRITE|DELETE"
}

Response: {
  "allowed": boolean,
  "reasonCode": "string",
  "recommendedActions": string[]
}
```

#### 能力查询API
```http
GET /api/v1/entitlements/me/capabilities
Authorization: Bearer <token>

Response: MyCapabilities
```

---

## 错误代码和审计事件

### 常见错误代码

| 错误码 | 描述 | 解决方案 |
|-------|------|----------|
| ACCESS_DENIED_403 | 访问被拒绝 | 联系管理员获取权限 |
| RESOURCE_NOT_FOUND_404 | 资源不存在 | 检查资源ID是否正确 |
| QUOTA_EXCEEDED_429 | 配额超出限制 | 升级计划或等待重置 |
| BUDGET_EXCEEDED_402 | 预算超出限制 | 联系财务部门 |

### 审计事件参考

| 事件类型 | 描述 | 包含字段 |
|---------|------|----------|
| ACCESS_GRANTED | 访问被授予 | userId, resourceId, action |
| ACCESS_DENIED | 访问被拒绝 | userId, resourceId, reason |
| ROLE_CHANGED | 角色变更 | userId, oldRole, newRole |
| PERMISSION_UPDATED | 权限更新 | resourceId, permissions |
| WORKSPACE_CREATED | 工作区创建 | workspaceId, creatorId |

---

## 性能考虑

### 决策性能优化

- **缓存策略**: Redis-backed权限缓存
- **批处理**: 批量权限检查
- **异步处理**: 后台审计日志记录
- **索引优化**: 数据库查询索引

### 可扩展性

- **水平扩展**: 支持多实例部署
- **分片**: 按租户分片数据
- **负载均衡**: 分布式部署支持
- **监控**: Prometheus指标集成

---

## 安全考虑

### 安全措施

- **输入验证**: 全面的数据验证
- **输出编码**: XSS防护
- **CSRF保护**: 跨站请求伪造防护
- **速率限制**: API调用频率限制
- **加密**: 敏感数据加密存储

### 合规性

- **GDPR**: 数据隐私保护
- **SOC2**: 安全合规
- **审计跟踪**: 完整的操作日志
- **访问控制**: 最小权限原则

---

## 未来扩展

### 📋 计划中的功能

#### 高级协作功能
- [ ] 实时协同编辑
- [ ] 冲突解决机制
- [ ] @提及和通知系统
- [ ] 版本控制和历史记录

#### 高级授权功能
- [ ] Casbin/OpenFGA集成
- [ ] 动态策略引擎
- [ ] 机器学习驱动的权限推荐
- [ ] 基于行为的访问控制

#### 移动端支持
- [ ] 移动应用协作
- [ ] 离线同步
- [ ] 推送通知
- [ ] 移动SDK

---

## 故障排除

### 常见问题

#### 1. 无法访问共享资源
**症状**: 收到"Access Denied"错误
**解决方案**:
1. 检查工作区邀请状态
2. 确认用户角色权限
3. 验证资源ACL设置
4. 检查Feature Flag状态

#### 2. 权限变更不生效
**症状**: 权限更新后仍无法访问
**解决方案**:
1. 清除权限缓存
2. 重启相关服务
3. 检查策略继承关系
4. 验证ABAC规则

#### 3. 审计日志缺失
**症状**: 某些操作未记录到审计日志
**解决方案**:
1. 检查审计服务状态
2. 验证数据库连接
3. 检查磁盘空间
4. 查看服务日志

---

## 参考链接

- [工作区管理API文档](docs/06-api/03-graphql.md)
- [Feature Flag治理](docs/05-access-entitlement-billing/03-feature-flag-governance.md)
- [访问决策服务](docs/05-access-entitlement-billing/02-access-decision.md)
- [错误代码参考](docs/05-access-entitlement-billing/10-error-codes-and-audit.md)