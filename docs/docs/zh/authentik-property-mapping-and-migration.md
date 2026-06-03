# Authentik Property Mapping 示例与 `user-1` 迁移指南

> **最后更新:** 2026-05-21  
> **前置:** [authentik-oidc-resource-server.md](authentik-oidc-resource-server.md)  
> **Blueprint 文件:** [platform/docs/authentik/blueprint-media-platform-claims.yaml](../../platform/docs/authentik/blueprint-media-platform-claims.yaml)

本文提供 **可复制** 的 Authentik Scope Mapping 表达式、组/用户属性约定，以及从开发态 `user-1` / `tenant-1` 迁移到 OIDC 的步骤。

---

## 1. 平台期望的 JWT Claims

| Claim | 必填 | 说明 |
|-------|------|------|
| `sub` | 是 | Authentik 用户 UUID（默认） |
| `platform_user_id` | 否（**迁移推荐**） | 平台用户主键；有则 JIT / API 用其代替 `sub` |
| `tenantId` | 联调推荐 | 租户 ID，对应 DB `tenant.id` |
| `roles` 或 `groups` | 推荐 | 字符串数组，如 `["ADMIN"]`；见 [OidcRoleMapping](../../platform/platform-app/src/main/java/com/example/platform/security/OidcRoleMapping.java) |
| `email` | 推荐 | JIT 创建用户邮箱 |

后端配置（已实现）：

```yaml
app.security.oauth2:
  tenant-claim: tenantId
  roles-claim: roles
  user-id-claim: platform_user_id
```

---

## 2. Authentik 组与用户属性（推荐布局）

### 2.1 组（Directory → Groups）

| 组名 | 用途 | 建议属性 `attributes` |
|------|------|------------------------|
| `mp-tenant-1-admin` | 租户 1 管理员 | `tenant_id: tenant-1` |
| `mp-tenant-1-editor` | 租户 1 编辑 | `tenant_id: tenant-1` |
| `mp-tenant-1-viewer` | 租户 1 只读 | `tenant_id: tenant-1` |

组名前缀 `mp-` 便于识别；**角色**由组名推导（见 §3.2 `roles` 映射）。

### 2.2 用户属性（Directory → Users → Attributes）

迁移 **`user-1`** 时，在 Authentik 用户上设置：

```json
{
  "platform_user_id": "user-1",
  "tenant_id": "tenant-1"
}
```

并将用户加入 `mp-tenant-1-admin`（或对应角色组）。

---

## 3. Scope Mapping 表达式（复制到控制台）

路径：**Customization → Property Mappings → Create → Scope Mapping**，创建后挂到 OAuth2 Provider → **Property mappings**。

### 3.1 `tenantId`（Scope name: `media-platform` 或 `profile`）

**Name:** `Media Platform — tenantId`  
**Scope name:** `media-platform`（需在 Provider 的 Scopes 与前端 `VITE_OIDC_SCOPE` 中加入 `media-platform`）

```python
tenant_id = request.user.attributes.get("tenant_id", "tenant-1")
for group in request.user.all_groups():
    if group.attributes.get("tenant_id"):
        tenant_id = group.attributes.get("tenant_id")
        break
return {
    "tenantId": tenant_id,
}
```

### 3.2 `roles`（可与上合并为同一 Mapping）

若单独建 Mapping，Scope name 仍可用 `media-platform`（同一 scope 返回多个 claim 需合并为一个 dict）：

**推荐：单条 Mapping 同时返回 `tenantId` + `roles` + `platform_user_id`：**

```python
tenant_id = request.user.attributes.get("tenant_id", "tenant-1")
for group in request.user.all_groups():
    if group.attributes.get("tenant_id"):
        tenant_id = group.attributes.get("tenant_id")
        break

roles = []
for group in request.user.all_groups():
    name = (group.name or "").upper()
    if "ADMIN" in name:
        roles.append("ADMIN")
    elif "EDITOR" in name:
        roles.append("EDITOR")
    elif "VIEWER" in name:
        roles.append("VIEWER")
if not roles:
    roles = ["VIEWER"]

platform_user_id = request.user.attributes.get("platform_user_id", str(request.user.pk))

return {
    "tenantId": tenant_id,
    "roles": roles,
    "platform_user_id": platform_user_id,
}
```

### 3.3 Provider 挂载

1. **Applications → Providers →** 你的 `media-platform-dev` Provider  
2. **Property mappings** → 添加上述 Scope Mapping（保留默认 `openid` / `profile` / `email`）  
3. **Advanced settings → Scopes** 确保包含自定义 scope（若使用 `media-platform`）

### 3.4 前端 Scope

`.env.local` 增加自定义 scope：

```env
VITE_OIDC_SCOPE=openid profile email media-platform
```

---

## 4. 用 Blueprint 导入（可选）

将 [blueprint-media-platform-claims.yaml](../../platform/docs/authentik/blueprint-media-platform-claims.yaml) 挂载到 Authentik worker：

```yaml
# docker-compose.authentik.yml 中 server/worker 增加：
volumes:
  - ./docs/authentik:/blueprints/custom:ro
```

启动后 **Customization → Blueprints** 中应出现 `media-platform-claims`，点击 **Apply**。  
Provider / Application 仍需在 UI 中绑定 Property mappings（Blueprint 仅预建 Groups + Scope Mapping）。

---

## 5. 从 `user-1` / dev JWT 迁移

### 5.1 三种策略

| 策略 | 做法 | 适用 |
|------|------|------|
| **A. 保留平台 ID（推荐）** | Authentik 用户属性 `platform_user_id=user-1` + Mapping §3 | 已有项目/渲染数据绑在 `user-1` |
| **B. 新 ID** | 仅用 Authentik `sub`，JIT 建新用户 |  greenfield，无历史数据 |
| **C. 迁移期双模** | `APP_SECURITY_OAUTH2_LEGACY_HMAC_JWT_ENABLED=true` + OIDC | 本地并行验证 |

### 5.2 策略 A 操作清单

1. 平台 DB 已有 `tenant-1`（`oidc` profile 下 [OidcDevBootstrapRunner](../../platform/platform-app/src/main/java/com/example/platform/security/OidcDevBootstrapRunner.java) 会自动创建）。  
2. 确认存在 `user-1`（同上，或历史数据已创建）。  
3. Authentik 创建用户 `dev@local`，属性 `platform_user_id=user-1`，加入 `mp-tenant-1-admin`。  
4. 登录后 JWT 含 `platform_user_id`，JIT 更新 `user-1` 的角色分配，**不会**新建 UUID 用户。  
5. 前端去掉依赖：生产禁用裸 `X-Tenant-ID`；`trust-jwt-tenant-only=true`。

### 5.3 验证 JWT

```bash
# 登录后从浏览器 session 或 Authentik 测试工具复制 access_token
export TOKEN="<access_token>"
./platform/scripts/verify-oidc-jwt.sh "$TOKEN"
curl -sS -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/me/dashboard | jq .
```

脚本会解码 payload（不验证签名，仅开发排错）；API 调用以服务端 JWKS 为准。

---

## 6. 与内置 RBAC 的关系

- JIT 写入 `user` 表 + `user_role_assignment`（角色键 `ADMIN` / `EDITOR` / `VIEWER`）。  
- [BuiltinDataInitializer](../../platform/identity-access-module/src/main/java/com/example/platform/identity/app/BuiltinDataInitializer.java) 会链接 `ADMIN` → 权限 `ADMIN` / `WRITE` / `MEMBER_MANAGE`，供 `/api/v1/me` 与导航使用。  
- **套餐 tier** 仍由 `entitlement-module` 控制（与 IdP 组独立）。

---

## 7. 常见问题

| 现象 | 处理 |
|------|------|
| 登录成功但 403 / 无权限 | JWT 是否含 `roles`；组名是否含 ADMIN/EDITOR/VIEWER |
| 仍创建 UUID 用户 | 检查 `platform_user_id` Mapping 与 `APP_SECURITY_OAUTH2_USER_ID_CLAIM` |
| `tenantId` 为空 | 用户/组 `tenant_id` 属性；或配置 `default-tenant-id`（仅 dev） |
| JIT 未建用户 | 租户不存在；`jit-provisioning-enabled=false`；看日志 `OIDC JIT skipped` |

---

## 8. 相关文件

| 文件 | 说明 |
|------|------|
| `platform/scripts/verify-oidc-jwt.sh` | 本地解码 JWT payload |
| `platform/frontend/.env.oidc.example` | 前端 OIDC 变量 |
| `platform/docker-compose.authentik.yml` | 本地 Authentik |
| `application-oidc.yml` | 后端 `oidc` profile |
