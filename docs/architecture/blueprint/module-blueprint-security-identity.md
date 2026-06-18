---
status: blueprint
last_verified: 2026-06-18
scope: all
truth_level: target
owner: platform
---

# Module Blueprint: Security & Identity

## 1. Purpose

The Security & Identity module manages authentication, authorization, tenant isolation, and access control across the platform.

## 2. Responsibilities

- User authentication (JWT, OAuth2, API keys)
- Tenant management and isolation
- Role-based access control (RBAC)
- API key management
- Workspace and project access control
- Security audit logging

## 3. Non-Responsibilities

- Business logic (other modules)
- Data storage (other modules)
- UI rendering (frontend)

## 4. Public Ports / APIs

### Authentication API
- Login/logout endpoints
- Token refresh
- API key management

### Authorization API
- Permission checks
- Role management
- Tenant management

### Identity API
- User profile management
- Workspace membership
- Group management

## 5. Domain Model

### User
- id, tenant_id, username, email
- role, status, created_at

### Tenant
- id, name, status, created_at

### ApiKey
- id, tenant_id, fingerprint
- hashed_key, principal, created_at

### Role
- id, role_key, name, scope
- permissions

### Permission
- id, permission_key
- resource_type, name

## 6. Events Published

- `UserCreated` - When user registers
- `UserAuthenticated` - On successful login
- `TenantCreated` - When tenant is provisioned
- `PermissionChanged` - On role/permission update

## 7. Events Consumed

- None (foundational module)

## 8. Dependencies Allowed

- `shared-kernel` - For common types

## 9. Dependencies Forbidden

- Business logic modules
- Storage modules
- External services

## 10. Extension Points

- `AuthenticationProvider` interface - For auth strategies
- `AuthorizationProvider` interface - For access control
- `IdentityProvider` interface - For user provisioning

## 11. Security / Tenant Rules

- All data is tenant-scoped
- Cross-tenant access forbidden
- Security events logged
- Rate limiting per tenant

## 12. Persistence Ownership

- `tenant` table
- `user` table
- `api_key` table
- `role` table
- `permission` table
- `workspace` table
- `workspace_member` table

## 13. Observability

- Metrics: auth attempts, failures, token usage
- Traces: auth flows, permission checks
- Logs: security events, audit trail

## 14. Current Status

**Status: Partially Implemented**

### Implemented
- JWT authentication
- API key authentication
- Tenant isolation
- Basic RBAC
- Workspace management

### Not Implemented
- OAuth2 resource server (disabled in preview)
- Multi-factor authentication
- SSO integration
- Advanced audit logging

## 15. Gap to Blueprint

| Blueprint Feature | Current Status | Gap |
|-------------------|----------------|-----|
| OAuth2 integration | Disabled in preview | High |
| MFA support | Not implemented | Medium |
| SSO integration | Not implemented | Medium |
| Advanced audit | Basic logging | Medium |
| Fine-grained permissions | Basic RBAC | Low |
