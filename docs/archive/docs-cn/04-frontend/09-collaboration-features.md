# 协作功能前端组件文档

**最后更新:** 2026-05-19T22:30:00+08:00
**状态标记:** ✅ 已实现 / ⚠️ 部分实现 / 🔧 Stub / 📋 Future / 🔴 Blocker / 🧪 需人工复核

---

## 概述

媒体平台的协作功能前端组件提供了完整的工作区管理、权限控制、实时协作和用户能力展示界面。这些组件基于Vue 3和TypeScript构建，使用Pinia进行状态管理和Tailwind CSS进行样式设计。

---

## 主要页面组件

### 1. MyCapabilitiesPage

#### 功能描述
显示用户的当前计划、可用功能、权限状态和资源使用情况。

#### 组件结构
```vue
<script setup lang="ts">
import CurrentPlanPanel from '@/pages/entitlement/CurrentPlanPanel.vue'
import UsageSummaryPanel from '@/pages/entitlement/UsageSummaryPanel.vue'
import UpgradeSuggestionPanel from '@/pages/entitlement/UpgradeSuggestionPanel.vue'

const capabilities = ref<MyCapabilities | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

async function loadCapabilities() {
  try {
    capabilities.value = await MeEntitlementAPI.getMyCapabilities()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>
```

#### API接口
```typescript
interface MeEntitlementAPI {
  static async getMyCapabilities(): Promise<MyCapabilities>
}
```

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

#### UI布局
```
┌─────────────────────────────────────────────────────────────┐
│ MyCapabilitiesPage                                          │
├─────────────────┬─────────────────┬─────────────────────────┤
│ CurrentPlanPanel │ UsageSummaryPanel │ UpgradeSuggestionPanel │
├─────────────────┴─────────────────┴─────────────────────────┤
│ Plan Overview                                               │
├─────────────────┬───────────────────────────────────────────┤
│ Feature Flags   │                                           │
├─────────────────┼─────────────────┬─────────────────────────┤
│ Entitlement     │ Export         │                         │
│ Policy          │ Capabilities   │                         │
└─────────────────┴─────────────────┴─────────────────────────┘
```

---

### 2. AdminConsole

#### 功能描述
管理员控制台，提供完整的策略管理、用户管理、审计查看和系统监控功能。

#### 主要功能模块
```typescript
interface AdminConsoleModules {
  workspaceManagement: boolean;
  roleManagement: boolean;
  permissionEditor: boolean;
  auditLogger: boolean;
  systemMonitor: boolean;
  extensionManager: boolean;
}
```

#### 页面路由
```
/admin/dashboard           - 管理仪表板
/admin/workspaces          - 工作区管理
/admin/users               - 用户管理
/admin/roles               - 角色管理
/admin/permissions         - 权限管理
/admin/audit              - 审计日志
/admin/monitoring         - 系统监控
/admin/extensions         - 扩展管理
/admin/feature-flags      - Feature Flag管理
```

#### 核心组件
- **AdminSidebar**: 管理侧边栏导航
- **PolicyManagementPanel**: 策略管理面板
- **UserGrantPanel**: 用户授权面板
- **RoleManagementPanel**: 角色管理面板
- **WorkspaceMembersPage**: 工作区成员管理
- **AuditLogViewer**: 审计日志查看器

---

### 3. WorkspaceMembersPage

#### 功能描述
管理工作区成员的角色分配和权限设置。

#### 组件特性
```typescript
interface WorkspaceMember {
  id: string;
  workspaceId: string;
  userId: string;
  username: string;
  email: string;
  role: 'OWNER' | 'ADMIN' | 'MEMBER' | 'VIEWER';
  status: 'ACTIVE' | 'INACTIVE' | 'REMOVED';
  joinedAt: Date;
  lastActive: Date;
}
```

#### 操作功能
- **添加成员**: 邀请新用户加入工作区
- **角色分配**: 设置不同级别的访问权限
- **批量操作**: 批量更新多个成员的权限
- **状态管理**: 激活/停用成员账户
- **移除成员**: 从工作区删除成员

#### UI组件
```vue
<template>
  <div class="workspace-members-container">
    <h2 class="text-xl font-semibold mb-4">工作区成员</h2>
    
    <!-- 搜索和筛选 -->
    <div class="mb-4 flex gap-4">
      <input v-model="searchQuery" placeholder="搜索成员..." />
      <select v-model="filterRole">
        <option value="">所有角色</option>
        <option value="OWNER">所有者</option>
        <option value="ADMIN">管理员</option>
        <option value="MEMBER">成员</option>
        <option value="VIEWER">查看者</option>
      </select>
    </div>

    <!-- 成员列表 -->
    <div class="members-list">
      <WorkspaceMemberCard 
        v-for="member in filteredMembers"
        :key="member.id"
        :member="member"
        @role-change="handleRoleChange"
        @remove="handleRemoveMember"
      />
    </div>

    <!-- 添加成员按钮 -->
    <button @click="showInviteModal = true" class="invite-button">
      + 邀请成员
    </button>
  </div>
</template>
```

---

### 4. AccessDecisionDebugPanel

#### 功能描述
调试访问决策流程，帮助管理员理解权限检查的详细过程。

#### 调试功能
```typescript
interface DebugRequest {
  memberId: string;
  featureKey: string;
  context?: Record<string, any>;
}

interface DebugResult {
  decision: 'ALLOW' | 'DENY';
  reasonCode: string;
  steps: DecisionStep[];
  timestamp: Date;
}

interface DecisionStep {
  name: string;
  result: boolean | string;
  details: Record<string, any>;
}
```

#### 决策步骤
1. **身份验证**: 验证用户身份
2. **ACL检查**: 检查访问控制列表
3. **ABAC评估**: 属性基访问控制
4. **Feature Flag**: Feature Flag检查
5. **权益验证**: 权益策略验证
6. **配额检查**: 资源配额检查
7. **预算检查**: 成本预算检查
8. **最终决策**: 生成最终结果

#### UI布局
```
┌─────────────────────────────────────────────────────────────┐
│ Access Decision Debug Panel                                 │
├─────────────────┬─────────────────┬─────────────────────────┤
│ Input Form      │ Decision Steps  │ Result Summary          │
├─────────────────┴─────────────────┴─────────────────────────┤
│ Common Features List                                        │
├─────────────────┬─────────────────┬─────────────────────────┤
│ gpu_rendering   │ 4k_export       │ remote_worker           │
├─────────────────┴─────────────────┴─────────────────────────┤
│ Custom Feature Input                                        │
└─────────────────────────────────────────────────────────────┘
```

---

## 通用UI组件

### 1. StatusBadge

#### 用途
显示各种状态的徽章，用于权限、Feature Flag、资源状态等。

#### 变体类型
```typescript
type BadgeVariant = 'success' | 'warning' | 'danger' | 'neutral' | 'premium' | 'enterprise';

interface StatusBadgeProps {
  variant: BadgeVariant;
  label: string;
  size?: 'sm' | 'md' | 'lg';
}
```

#### 使用示例
```vue
<StatusBadge 
  variant="success" 
  label="已启用" 
/>
<StatusBadge 
  variant="warning" 
  label="需要升级" 
/>
<StatusBadge 
  variant="danger" 
  label="已禁用" 
/>
```

### 2. FeatureBadge

#### 用途
显示Feature Flag的状态和功能描述。

#### 数据模型
```typescript
interface FeatureBadgeData {
  feature: string;
  enabled: boolean;
  description: string;
  tier?: 'FREE' | 'PRO' | 'TEAM' | 'ENTERPRISE';
}
```

### 3. HelpTooltip

#### 用途
提供功能的详细说明和帮助信息。

#### 配置选项
```typescript
interface TooltipConfig {
  content: string;
  position?: 'top' | 'bottom' | 'left' | 'right';
  delay?: number;
}
```

---

## API客户端

### 1. WorkspaceEntitlementAPI

```typescript
class WorkspaceEntitlementAPI {
  static async debugAccessDecision(
    workspaceId: string,
    memberId: string,
    featureKey: string
  ): Promise<AccessDecisionDebug> {
    // POST /api/v1/workspaces/{workspaceId}/debug/decision
  }

  static async getWorkspaceMembers(workspaceId: string): Promise<WorkspaceMember[]> {
    // GET /api/v1/workspaces/{workspaceId}/members
  }

  static async updateMemberRole(
    workspaceId: string,
    memberId: string,
    role: string
  ): Promise<void> {
    // PUT /api/v1/workspaces/{workspaceId}/members/{memberId}/role
  }
}
```

### 2. MeEntitlementAPI

```typescript
class MeEntitlementAPI {
  static async getMyCapabilities(): Promise<MyCapabilities> {
    // GET /api/v1/entitlements/me/capabilities
  }

  static async checkPermission(
    resourceType: string,
    resourceId: string,
    action: string
  ): Promise<boolean> {
    // POST /api/v1/access/check
  }
}
```

### 3. AdminEntitlementAPI

```typescript
class AdminEntitlementAPI {
  static async createRole(role: RoleCreateRequest): Promise<Role> {
    // POST /api/v1/admin/roles
  }

  static async assignRole(userId: string, roleId: string): Promise<void> {
    // POST /api/v1/admin/users/{userId}/roles
  }

  static async listAuditLogs(filters: AuditFilters): Promise<AuditRecord[]> {
    // GET /api/v1/admin/audit/logs
  }
}
```

---

## 状态管理

### Pinia Store

#### useAuthStore
```typescript
interface AuthState {
  user: User | null;
  token: string | null;
  permissions: string[];
  isAuthenticated: boolean;
}

const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    user: null,
    token: null,
    permissions: [],
    isAuthenticated: false
  })
})
```

#### useWorkspaceStore
```typescript
interface WorkspaceState {
  currentWorkspace: Workspace | null;
  members: WorkspaceMember[];
  groups: WorkspaceGroup[];
  loading: boolean;
}

const useWorkspaceStore = defineStore('workspace', {
  state: (): WorkspaceState => ({
    currentWorkspace: null,
    members: [],
    groups: [],
    loading: false
  })
})
```

---

## 测试覆盖

### 单元测试
- **MyCapabilitiesPage.spec.ts**: 用户能力页面测试
- **AdminConsole.spec.ts**: 管理控制台测试
- **WorkspaceMembersPage.spec.ts**: 工作区成员管理测试
- **AccessDecisionDebugPanel.spec.ts**: 访问决策调试测试

### 集成测试
- **Feature Flag集成测试**: Feature Flag与权限系统的集成
- **API端点测试**: 所有协作相关API的测试
- **权限流转测试**: 权限变更的完整流程测试

---

## 性能优化

### 1. 懒加载
```typescript
// 动态导入大型组件
const AdminConsole = defineAsyncComponent(() => import('@/pages/admin/AdminConsole.vue'))
```

### 2. 虚拟滚动
```vue
<VirtualList 
  :items="largeMemberList"
  :item-height="60"
  :buffer-size="5"
/>
```

### 3. 缓存策略
```typescript
// 缓存用户能力信息30分钟
const useUserCapabilities = () => {
  const cached = useLocalStorage('user-capabilities', null, {
    maxAge: 30 * 60 * 1000 // 30分钟
  })
  
  return computed(() => {
    if (!cached.value) {
      refreshCapabilities()
    }
    return cached.value
  })
}
```

---

## 国际化支持

### 中文文案
```typescript
const messages = {
  zh: {
    'capabilities.title': '我的能力',
    'capabilities.currentPlan': '当前计划',
    'capabilities.featureFlags': '功能开关',
    'admin.console.title': '管理控制台',
    'admin.workspace.members': '工作区成员',
    'admin.role.management': '角色管理',
    'audit.log.recent': '最近的审计记录'
  }
}
```

---

## 故障排除

### 常见问题

#### 1. 权限不更新
**症状**: 更改权限后页面未刷新
**解决方案**:
```typescript
// 手动触发重新加载
await refreshUserCapabilities()
// 清除缓存
clearPermissionCache()
```

#### 2. API调用失败
**症状**: 网络错误或认证失败
**解决方案**:
```typescript
try {
  const response = await apiCall()
} catch (error) {
  if (error.code === 401) {
    // 重新登录
    await logoutAndRedirectToLogin()
  }
}
```

#### 3. 组件渲染问题
**症状**: UI显示异常或空白
**解决方案**:
```typescript
// 检查数据加载状态
if (loading.value) {
  return <LoadingSpinner />
}

// 检查错误状态
if (error.value) {
  return <ErrorState message={error.value} />
}
```

---

## 参考链接

- [用户门户文档](docs/04-frontend/05-user-portal.md)
- [管理控制台文档](docs/04-frontend/06-admin-console.md)
- [API策略文档](docs/06-api/01-api-strategy.md)
- [GraphQL文档](docs/06-api/03-graphql.md)