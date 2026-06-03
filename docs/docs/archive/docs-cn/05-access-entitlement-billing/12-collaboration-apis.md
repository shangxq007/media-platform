# 协作API接口文档

**最后更新:** 2026-05-19T22:30:00+08:00
**状态标记:** ✅ 已实现 / ⚠️ 部分实现 / 🔧 Stub / 📋 Future / 🔴 Blocker / 🧪 需人工复核

---

## 概述

媒体平台的协作API提供了完整的工作区管理、用户授权、权限检查和审计日志功能。这些API支持前端组件与后端服务的安全通信，确保多用户协作环境的数据一致性和安全性。

---

## 认证和授权

### 认证方式
```http
Authorization: Bearer <access_token>
X-Tenant-ID: <tenant_id>
```

### 错误响应格式
```json
{
  "error": {
    "code": "ACCESS_DENIED_403",
    "message": "Access denied. Insufficient permissions.",
    "details": {
      "requiredPermission": "WRITE",
      "resourceId": "proj_123"
    },
    "timestamp": "2026-05-19T22:30:00Z"
  }
}
```

---

## 工作区协作API

### 1. 工作区管理

#### GET /api/v1/workspaces
获取当前用户的所有工作区列表。

**请求参数**: 无

**响应示例**:
```json
[
  {
    "id": "ws_123",
    "name": "设计团队",
    "description": "创意设计和视频制作团队",
    "ownerId": "user_456",
    "memberCount": 8,
    "createdAt": "2026-05-10T10:30:00Z",
    "updatedAt": "2026-05-19T15:20:00Z"
  }
]
```

#### POST /api/v1/workspaces
创建新的工作区。

**请求体**:
```json
{
  "name": "新工作区名称",
  "description": "工作区描述",
  "template": "default|design|video-editing"
}
```

**响应**: `201 Created`
```json
{
  "id": "ws_789",
  "name": "新工作区名称",
  "description": "工作区描述",
  "ownerId": "user_current",
  "memberCount": 1,
  "createdAt": "2026-05-19T22:30:00Z",
  "updatedAt": "2026-05-19T22:30:00Z"
}
```

### 2. 工作区成员管理

#### GET /api/v1/workspaces/{workspaceId}/members
获取工作区的所有成员。

**路径参数**:
- `workspaceId`: 工作区ID

**响应示例**:
```json
[
  {
    "id": "member_123",
    "workspaceId": "ws_123",
    "userId": "user_456",
    "username": "张三",
    "email": "zhangsan@example.com",
    "role": "OWNER",
    "status": "ACTIVE",
    "joinedAt": "2026-05-10T10:30:00Z",
    "lastActive": "2026-05-19T15:20:00Z",
    "permissions": ["READ", "WRITE", "DELETE", "ADMIN"]
  }
]
```

#### POST /api/v1/workspaces/{workspaceId}/members
邀请用户加入工作区。

**路径参数**:
- `workspaceId`: 工作区ID

**请求体**:
```json
{
  "userId": "user_789",
  "role": "MEMBER|ADMIN|VIEWER",
  "inviteMessage": "欢迎加入我们的团队！"
}
```

**成功响应**: `201 Created`
```json
{
  "id": "member_456",
  "workspaceId": "ws_123",
  "userId": "user_789",
  "username": "李四",
  "email": "lisi@example.com",
  "role": "MEMBER",
  "status": "INVITED",
  "joinedAt": null,
  "lastActive": null,
  "permissions": ["READ", "WRITE"]
}
```

#### PUT /api/v1/workspaces/{workspaceId}/members/{memberId}/role
更新成员的权限角色。

**路径参数**:
- `workspaceId`: 工作区ID
- `memberId`: 成员ID

**请求体**:
```json
{
  "role": "OWNER|ADMIN|MEMBER|VIEWER",
  "reason": "提升为管理员"
}
```

**响应**: `200 OK`
```json
{
  "id": "member_123",
  "workspaceId": "ws_123",
  "userId": "user_456",
  "username": "张三",
  "email": "zhangsan@example.com",
  "role": "ADMIN",
  "status": "ACTIVE",
  "joinedAt": "2026-05-10T10:30:00Z",
  "lastActive": "2026-05-19T15:20:00Z",
  "permissions": ["READ", "WRITE", "DELETE", "ADMIN"]
}
```

#### DELETE /api/v1/workspaces/{workspaceId}/members/{memberId}
从工作区移除成员。

**路径参数**:
- `workspaceId`: 工作区ID
- `memberId`: 成员ID

**响应**: `204 No Content`

### 3. 项目分享

#### GET /api/v1/projects/shared
获取当前用户有权访问的所有项目。

**查询参数**:
- `type=owned` - 仅拥有的项目
- `type=shared` - 仅共享的项目
- `type=all` - 所有项目（默认）

**响应示例**:
```json
[
  {
    "id": "proj_123",
    "name": "产品宣传视频",
    "description": "2026年第一季度产品发布宣传视频",
    "workspaceId": "ws_123",
    "ownerId": "user_456",
    "sharedWith": [
      {
        "userId": "user_789",
        "role": "EDITOR",
        "permission": "READ_WRITE"
      }
    ],
    "createdAt": "2026-05-15T09:00:00Z",
    "updatedAt": "2026-05-19T14:30:00Z"
  }
]
```

#### POST /api/v1/projects/{projectId}/share
分享项目给其他用户或组。

**路径参数**:
- `projectId`: 项目ID

**请求体**:
```json
{
  "shares": [
    {
      "targetType": "USER|GROUP",
      "targetId": "user_789",
      "permission": "READ|READ_WRITE|FULL_CONTROL",
      "expiresAt": "2026-06-19T22:30:00Z"
    }
  ]
}
```

**响应**: `200 OK`
```json
{
  "success": true,
  "sharedWith": 3,
  "failedShares": []
}
```

---

## 权限和访问控制API

### 1. 访问决策

#### POST /api/v1/access/check
检查用户对特定资源的访问权限。

**请求体**:
```json
{
  "userId": "user_456",
  "resourceType": "PROJECT|RENDER_JOB|ARTIFACT|TEMPLATE",
  "resourceId": "proj_123",
  "action": "READ|WRITE|DELETE|SHARE|MANAGE_PERMISSIONS"
}
```

**响应示例**:
```json
{
  "allowed": true,
  "reasonCode": "PERMISSION_GRANTED",
  "reasons": [
    "用户是项目所有者",
    "用户具有写权限"
  ],
  "recommendedActions": [],
  "metadata": {
    "currentRole": "OWNER",
    "availableActions": ["READ", "WRITE", "DELETE", "SHARE", "MANAGE_PERMISSIONS"]
  }
}
```

#### POST /api/v1/workspaces/{workspaceId}/debug/decision
调试工作区的访问决策流程（管理员专用）。

**路径参数**:
- `workspaceId`: 工作区ID

**请求体**:
```json
{
  "memberId": "user_789",
  "featureKey": "gpu_rendering",
  "context": {
    "projectId": "proj_123",
    "renderPreset": "4k_high_quality"
  }
}
```

**响应示例**:
```json
{
  "decision": "ALLOW",
  "reasonCode": "GPU_RENDERING_ALLOWED",
  "steps": [
    {
      "name": "身份验证",
      "result": "SUCCESS",
      "details": {
        "userId": "user_789",
        "workspaceId": "ws_123",
        "authenticated": true
      }
    },
    {
      "name": "ACL检查",
      "result": "PASS",
      "details": {
        "hasReadPermission": true,
        "hasWritePermission": false
      }
    },
    {
      "name": "Feature Flag检查",
      "result": "ENABLED",
      "details": {
        "flagKey": "gpu_rendering",
        "enabled": true
      }
    }
  ],
  "timestamp": "2026-05-19T22:30:00Z"
}
```

### 2. 用户能力查询

#### GET /api/v1/entitlements/me/capabilities
获取当前用户的完整能力信息。

**请求头**:
```
Authorization: Bearer <access_token>
```

**响应示例**:
```json
{
  "tier": "TEAM",
  "entitlementPolicy": {
    "monthlyRenderMinutes": 3600,
    "maxConcurrentJobs": 5,
    "maxResolutionWidth": 3840,
    "maxResolutionHeight": 2160,
    "gpuAllowed": true,
    "remoteWorkerAllowed": true,
    "customFontsAllowed": true,
    "watermark": false
  },
  "exportCapabilities": {
    "allowedFormats": ["mp4", "mov", "avi", "mkv", "webm"],
    "allowedPresets": ["720p", "1080p", "4k", "8k"],
    "maxResolutionWidth": 3840,
    "maxResolutionHeight": 2160,
    "gpuExportAllowed": true,
    "maxConcurrentExports": 3
  },
  "featureFlags": [
    {
      "flagKey": "gpu_rendering",
      "displayName": "GPU渲染",
      "description": "使用硬件加速进行视频渲染",
      "enabled": true,
      "category": "PERFORMANCE"
    },
    {
      "flagKey": "remote_worker",
      "displayName": "远程渲染器",
      "description": "使用远程分布式渲染器",
      "enabled": true,
      "category": "SCALABILITY"
    }
  ],
  "accessibleResources": [
    {
      "type": "WORKSPACE",
      "id": "ws_123",
      "name": "设计团队",
      "permissionLevel": "ADMIN"
    },
    {
      "type": "PROJECT",
      "id": "proj_456",
      "name": "产品演示",
      "permissionLevel": "READ_WRITE"
    }
  ],
  "permissionStatus": [
    {
      "resourceType": "RENDER_JOB",
      "resourceId": "*",
      "canCreate": true,
      "canEdit": true,
      "canDelete": false,
      "canShare": true
    }
  ]
}
```

---

## 审计API

### 1. 审计日志查询

#### GET /api/v1/audit/logs
查询审计日志记录。

**查询参数**:
- `limit=50` - 返回的记录数量（默认50，最大1000）
- `offset=0` - 偏移量
- `actorId=user_123` - 按执行者筛选
- `action=CREATE|UPDATE|DELETE|ACCESS` - 按操作类型筛选
- `resourceType=PROJECT|WORKSPACE|USER` - 按资源类型筛选
- `startDate=2026-05-01` - 开始日期
- `endDate=2026-05-31` - 结束日期

**响应示例**:
```json
{
  "total": 1234,
  "logs": [
    {
      "id": "audit_123",
      "actorType": "USER",
      "actorId": "user_456",
      "actorName": "张三",
      "action": "UPDATE",
      "resourceType": "PROJECT",
      "resourceId": "proj_123",
      "resourceName": "产品宣传视频",
      "details": {
        "field": "description",
        "oldValue": "旧描述",
        "newValue": "新描述"
      },
      "ipAddress": "192.168.1.100",
      "userAgent": "Mozilla/5.0...",
      "timestamp": "2026-05-19T15:20:00Z"
    }
  ]
}
```

#### GET /api/v1/audit/stats
获取审计统计信息。

**响应示例**:
```json
{
  "totalLogs": 15678,
  "byAction": {
    "CREATE": 3421,
    "UPDATE": 8934,
    "DELETE": 123,
    "ACCESS": 3190
  },
  "byResourceType": {
    "PROJECT": 7823,
    "WORKSPACE": 2341,
    "USER": 1567,
    "RENDER_JOB": 3947
  },
  "recentActivity": [
    {
      "date": "2026-05-19",
      "count": 234
    },
    {
      "date": "2026-05-18",
      "count": 189
    }
  ]
}
```

---

## 管理员API

### 1. 角色管理

#### GET /api/v1/admin/roles
获取所有可用的角色定义。

**响应示例**:
```json
[
  {
    "id": "role_123",
    "key": "WORKSPACE_ADMIN",
    "name": "工作区管理员",
    "description": "可以管理工作区设置和成员",
    "scope": "WORKSPACE",
    "permissions": [
      "WORKSPACE_READ",
      "WORKSPACE_WRITE",
      "MEMBER_MANAGE",
      "PROJECT_SHARE"
    ],
    "createdAt": "2026-05-10T10:00:00Z"
  }
]
```

#### POST /api/v1/admin/roles
创建新角色。

**请求体**:
```json
{
  "key": "CUSTOM_ROLE",
  "name": "自定义角色",
  "description": "特定用途的自定义角色",
  "scope": "GLOBAL|WORKSPACE",
  "permissions": ["READ", "WRITE"]
}
```

### 2. 系统监控

#### GET /api/v1/admin/system/status
获取系统运行状态。

**响应示例**:
```json
{
  "services": {
    "database": "healthy",
    "cache": "healthy",
    "storage": "healthy",
    "auth": "healthy"
  },
  "metrics": {
    "activeUsers": 156,
    "totalWorkspaces": 45,
    "totalProjects": 234,
    "totalRenderJobs": 892
  },
  "alerts": [
    {
      "level": "warning",
      "message": "存储空间使用率超过80%",
      "timestamp": "2026-05-19T14:30:00Z"
    }
  ]
}
```

---

## WebSocket实时通知

### 连接URL
```
wss://api.media-platform.com/ws/notifications
```

### 认证
```javascript
const socket = new WebSocket('wss://api.media-platform.com/ws/notifications')
socket.onopen = () => {
  socket.send(JSON.stringify({
    type: 'AUTH',
    token: '<access_token>'
  }))
}
```

### 消息类型

#### 工作区事件
```json
{
  "type": "WORKSPACE_EVENT",
  "event": "MEMBER_ADDED|MEMBER_REMOVED|ROLE_CHANGED",
  "data": {
    "workspaceId": "ws_123",
    "memberId": "user_456",
    "changes": { /* 变更详情 */ }
  }
}
```

#### 权限变更
```json
{
  "type": "PERMISSION_CHANGE",
  "event": "PERMISSION_GRANTED|PERMISSION_REVOKED",
  "data": {
    "resourceId": "proj_123",
    "userId": "user_789",
    "permission": "READ|WRITE|DELETE"
  }
}
```

#### 审计事件
```json
{
  "type": "AUDIT_EVENT",
  "event": "NEW_AUDIT_LOG",
  "data": {
    "logId": "audit_123",
    "summary": "用户创建了项目"
  }
}
```

---

## 速率限制

### API限制
- **普通用户**: 1000请求/小时
- **管理员**: 5000请求/小时
- **系统服务**: 无限制

### 限制头信息
```http
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 998
X-RateLimit-Reset: 1621440000
```

---

## 错误代码参考

### 协作相关错误码

| 错误码 | HTTP状态 | 描述 |
|--------|----------|------|
| WORKSPACE_NOT_FOUND | 404 | 工作区不存在 |
| MEMBER_NOT_IN_WORKSPACE | 403 | 用户不在工作区中 |
| INVALID_ROLE | 400 | 无效的角色分配 |
| DUPLICATE_INVITE | 409 | 重复的邀请 |
| INVITE_EXPIRED | 410 | 邀请已过期 |

### 权限相关错误码

| 错误码 | HTTP状态 | 描述 |
|--------|----------|------|
| ACCESS_DENIED | 403 | 访问被拒绝 |
| INSUFFICIENT_PERMISSIONS | 403 | 权限不足 |
| RESOURCE_LOCKED | 423 | 资源被锁定 |
| QUOTA_EXCEEDED | 429 | 配额超出限制 |

---

## SDK使用示例

### JavaScript/TypeScript
```typescript
import { WorkspaceEntitlementAPI, MeEntitlementAPI } from './api'

// 获取用户能力
const capabilities = await MeEntitlementAPI.getMyCapabilities()

// 调试访问决策
const debugResult = await WorkspaceEntitlementAPI.debugAccessDecision(
  'ws_123',
  'user_456', 
  'gpu_rendering'
)

// 管理工作区成员
await WorkspaceEntitlementAPI.updateMemberRole(
  'ws_123',
  'member_789',
  'ADMIN'
)
```

### Python
```python
import requests

# 获取用户能力
response = requests.get(
  'https://api.media-platform.com/api/v1/entitlements/me/capabilities',
  headers={'Authorization': f'Bearer {token}'}
)
capabilities = response.json()

# 检查访问权限
check_response = requests.post(
  'https://api.media-platform.com/api/v1/access/check',
  json={
    'userId': 'user_456',
    'resourceType': 'PROJECT',
    'resourceId': 'proj_123',
    'action': 'WRITE'
  }
)
```

---

## 最佳实践

### 1. 错误处理
```typescript
try {
  const result = await apiCall()
} catch (error) {
  if (error.code === 'ACCESS_DENIED') {
    showPermissionDialog()
  } else if (error.code === 'QUOTA_EXCEEDED') {
    showUpgradeDialog()
  }
}
```

### 2. 缓存策略
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

### 3. 批量操作
```typescript
// 批量更新多个成员的权限
async function updateMembersRoles(workspaceId: string, updates: MemberUpdate[]) {
  const results = await Promise.allSettled(
    updates.map(update => 
      WorkspaceEntitlementAPI.updateMemberRole(
        workspaceId, 
        update.memberId, 
        update.role
      )
    )
  )
  return results
}
```

---

## 参考链接

- [GraphQL API文档](docs/06-api/03-graphql.md)
- [REST API策略](docs/06-api/01-api-strategy.md)
- [错误代码和审计参考](docs/05-access-entitlement-billing/10-error-codes-and-audit.md)
- [安全策略](docs/11-development/03-security.md)