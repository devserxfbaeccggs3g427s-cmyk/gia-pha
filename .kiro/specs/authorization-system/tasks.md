# Kế hoạch Triển khai: Hệ Thống Quản Lý Phân Quyền (Authorization System)

## Tổng quan

Triển khai hệ thống phân quyền đa tầng mở rộng từ cơ chế RBAC hiện có (`src/lib/auth/rbac.ts`). Hệ thống bao gồm 9 module chính: Permission Registry, RBAC Module, Permission Cache, Policy Evaluator, Authorization Engine, Audit Logger, Route Permission Config, Backward Compatibility Layer, và Data Masking Service. Sử dụng TypeScript, Vercel Blob (JSON), Zod validation, và kiểm thử bằng vitest + fast-check.

## Tasks

- [ ] 1. Thiết lập cấu trúc dự án và định nghĩa types/schemas cơ bản
  - [ ] 1.1 Tạo thư mục và file types cho hệ thống phân quyền
    - Tạo `src/lib/auth/types.ts` chứa tất cả interfaces và types: `SystemRole`, `ExtendedTreeRole`, `ResourceType`, `ActionType`, `Permission`, `AccessContext`, `AuthorizationDecision`, `AuthorizationResult`, `PolicyEffect`, `PolicyCondition`, `AccessPolicy`, `CacheEntry`, `CacheConfig`, `AuditLogEntry`, `RoleChangeLogEntry`, `RoutePermission`, `MaskingRule`, `AuthorizationErrorCode`, `AuthorizationError` class
    - Tạo `src/lib/auth/schemas.ts` chứa Zod schemas: `userRoleEntrySchema`, `permissionDefinitionSchema`, `rolePermissionMappingSchema`, `policyConditionSchema`, `accessPolicySchema`, `auditLogEntrySchema`
    - Tạo `src/lib/auth/constants.ts` chứa: `SYSTEM_ROLE_HIERARCHY`, `DEFAULT_SYSTEM_ROLE_PERMISSIONS`, `DEFAULT_TREE_ROLE_PERMISSIONS`, `AUTH_BLOB_PATHS`
    - _Requirements: 1.1, 1.2, 2.1, 3.1, 3.2, 3.3, 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [ ] 1.2 Tạo seed data JSON files cho authorization
    - Tạo `data/authorization/user-roles.json` (mảng rỗng hoặc seed SUPER_ADMIN cho owner)
    - Tạo `data/authorization/permissions.json` với danh sách permissions mặc định (32 permissions: 8 resources × 4 actions)
    - Tạo `data/authorization/role-permissions.json` với default role-permission mappings
    - Tạo `data/authorization/access-policies.json` (mảng rỗng)
    - Mở rộng `BLOB_PATHS` trong `src/lib/blob/client.ts` thêm các paths mới
    - _Requirements: 3.2, 6.1, 6.2, 6.3, 6.4, 6.5_

- [ ] 2. Triển khai Permission Registry và RBAC Module
  - [ ] 2.1 Triển khai Permission Registry (`src/lib/auth/permission-registry.ts`)
    - Implement `getAllPermissions()` đọc từ blob storage
    - Implement `getPermissionsByResource(resource)` lọc theo resource type
    - Implement `registerPermission(definition)` thêm permission mới vào blob
    - Implement `isValidPermission(permission)` kiểm tra format `resource:action` bằng regex
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 10.5_

  - [ ]* 2.2 Viết property test cho Permission Registry
    - **Property 13: Permission Format Invariant**
    - Mọi permission đã đăng ký phải match pattern `^[a-z]+:[a-z]+$`
    - File: `tests/property/authorization/permission-registry.property.test.ts`
    - **Validates: Requirements 3.1**

  - [ ] 2.3 Triển khai RBAC Module (`src/lib/auth/rbac-module.ts`)
    - Implement `getUserSystemRole(userId)` đọc từ user-roles.json blob
    - Implement `hasSystemPermission(role, permission)` kiểm tra permission theo system role hierarchy
    - Implement `hasTreePermission(role, permission)` kiểm tra permission theo tree role
    - Implement `getEffectiveTreeRole(userId, treeId, systemRole)` trả về effective role (SUPER_ADMIN → TREE_ADMIN override)
    - Implement `assignSystemRole(userId, newRole, assignedBy)` gán system role mới
    - Implement `assignTreeRole(userId, treeId, role, invitedBy)` gán tree role
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

  - [ ]* 2.4 Viết property tests cho RBAC Module
    - **Property 1: Role Hierarchy Inheritance** - SUPER_ADMIN kế thừa ADMIN, ADMIN kế thừa MEMBER
    - **Property 2: Single System Role Invariant** - Mỗi user có đúng 1 system role
    - **Property 3: SUPER_ADMIN Tree Override** - SUPER_ADMIN luôn có TREE_ADMIN trên mọi cây
    - **Property 4: Tree Role Determines Permitted Actions** - Mỗi tree role chỉ có đúng set actions
    - File: `tests/property/authorization/role-hierarchy.property.test.ts`
    - **Validates: Requirements 1.2, 1.4, 2.4, 5.1, 5.2, 5.3**

  - [ ]* 2.5 Viết unit tests cho RBAC Module
    - Test MEMBER không truy cập admin pages
    - Test TREE_EDITOR tạo thành viên nhưng không xóa
    - Test SUPER_ADMIN override tree role
    - Test gán system role mới cập nhật đúng
    - File: `tests/unit/authorization/rbac-module.test.ts`
    - _Requirements: 1.1, 1.2, 2.1, 2.4, 2.6_

- [ ] 3. Triển khai Permission Cache
  - [ ] 3.1 Triển khai Permission Cache (`src/lib/auth/permission-cache.ts`)
    - Implement in-memory TTL cache với `Map<string, CacheEntry>`
    - Implement `generateCacheKey(userId, resource, action, treeId?)` tạo cache key duy nhất
    - Implement `get(userId, permissionKey)` trả về entry nếu chưa hết hạn
    - Implement `set(userId, permissionKey, decision, ttl)` lưu entry với TTL
    - Implement `invalidateUser(userId)` xóa tất cả entries của user
    - Implement `invalidateRole(role)` xóa entries liên quan đến role
    - Implement `invalidateAll()` xóa toàn bộ cache
    - Implement LRU eviction khi vượt `maxEntries` (default 10000)
    - Default TTL: systemRoleTTL = 300s, treeRoleTTL = 60s
    - _Requirements: 7.3, 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_

  - [ ]* 3.2 Viết unit tests cho Permission Cache
    - Test TTL expiration (entry hết hạn trả về null)
    - Test cache hit trả về đúng decision
    - Test invalidateUser xóa đúng entries
    - Test invalidateRole xóa entries liên quan
    - Test LRU eviction khi vượt maxEntries
    - File: `tests/unit/authorization/permission-cache.test.ts`
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

- [ ] 4. Checkpoint - Đảm bảo các module cơ sở hoạt động
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 5. Triển khai Policy Evaluator và Backward Compatibility
  - [ ] 5.1 Triển khai Policy Evaluator (`src/lib/auth/policy-evaluator.ts`)
    - Implement `matchesCondition(condition, context)` đánh giá từng condition (eq, neq, in, nin, exists, gt, lt)
    - Implement `getApplicablePolicies(context)` đọc policies từ blob và lọc theo resource type
    - Implement `evaluatePolicies(context, policies)` đánh giá tập policies theo deny-overrides strategy (policy DENY ưu tiên ALLOW)
    - Sắp xếp policies theo priority (cao → thấp) trước khi đánh giá
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 7.5_

  - [ ]* 5.2 Viết property tests cho Policy Evaluator
    - **Property 9: Deny-Overrides Conflict Resolution** - Nếu có ít nhất 1 policy DENY thì kết quả là DENY
    - **Property 10: Default Deny (Least Privilege)** - Không có rule nào → DENY
    - File: `tests/property/authorization/policy-evaluator.property.test.ts`
    - **Validates: Requirements 7.5, 10.1**

  - [ ] 5.3 Triển khai Backward Compatibility Layer (`src/lib/auth/compatibility.ts`)
    - Implement `mapLegacyTreeRole(legacyRole)` map ADMIN→TREE_ADMIN, EDITOR→TREE_EDITOR, VIEWER→TREE_VIEWER
    - Implement `mapToLegacyTreeRole(role)` map ngược về legacy role
    - Implement `mapLegacyPermission(legacy)` map legacy permission strings sang Permission[]
    - Đảm bảo existing code (`rbac.ts`) vẫn hoạt động bình thường
    - _Requirements: 2.1 (backward compatible)_

  - [ ]* 5.4 Viết unit tests cho Compatibility Layer
    - Test mapping đúng chiều Legacy → Extended
    - Test mapping đúng chiều Extended → Legacy
    - Test mapLegacyPermission cho tất cả legacy permissions
    - File: `tests/unit/authorization/compatibility.test.ts`
    - _Requirements: 2.1_

- [ ] 6. Triển khai Audit Logger
  - [ ] 6.1 Triển khai Audit Logger (`src/lib/auth/audit-logger.ts`)
    - Implement `logAuthorizationDecision(entry)` ghi log quyết định DENY vào blob (partitioned theo tháng: `audit-logs/YYYY-MM.json`)
    - Implement `logRoleChange(entry)` ghi log thay đổi vai trò
    - Implement `queryLogs(filter)` truy vấn log theo userId, resourceId, action, khoảng thời gian
    - Sử dụng nanoid cho log entry IDs
    - Đảm bảo mỗi entry chứa: timestamp, userId, action, resourceType, resourceId, decision, reason
    - _Requirements: 1.5, 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_

  - [ ]* 6.2 Viết property test cho Audit Logger
    - **Property 14: Audit Log Entry Completeness** - Mọi entry phải có đủ trường bắt buộc
    - File: `tests/property/authorization/audit-logger.property.test.ts`
    - **Validates: Requirements 1.5, 9.4**

  - [ ]* 6.3 Viết unit tests cho Audit Logger
    - Test ghi log DENY decision đúng format
    - Test ghi log role change đầy đủ thông tin
    - Test queryLogs lọc đúng theo filter
    - Test partition theo tháng (YYYY-MM)
    - File: `tests/unit/authorization/audit-logger.test.ts`
    - _Requirements: 9.1, 9.2, 9.4, 9.6_

- [ ] 7. Triển khai Authorization Engine
  - [ ] 7.1 Triển khai Authorization Engine (`src/lib/auth/authorization-engine.ts`)
    - Implement `buildAccessContext(request, token)` xây dựng context từ request + JWT
    - Implement `evaluate(context)` pipeline: (1) check cache → (2) check system role → (3) check tree role → (4) evaluate policies
    - Implement deny-fast short-circuit: nếu bước nào DENY thì dừng ngay
    - Implement fail-closed: mọi lỗi trong pipeline → trả về DENY
    - Tích hợp Permission Cache: check cache trước, nếu miss thì evaluate rồi cache kết quả
    - Tích hợp Audit Logger: log mọi DENY decisions
    - _Requirements: 4.1, 4.4, 4.5, 7.1, 7.2, 7.3, 7.4, 7.6, 10.1, 10.3_

  - [ ]* 7.2 Viết property tests cho Authorization Engine
    - **Property 8: Deny-Fast Short-Circuit** - DENY ở bước K → không thực thi bước K+1..N
    - **Property 10: Default Deny** - Không có permission → DENY
    - **Property 11: Fail-Closed on Error** - Lỗi trong evaluation → DENY, không bao giờ ALLOW
    - File: `tests/property/authorization/evaluation-pipeline.property.test.ts`
    - **Validates: Requirements 7.4, 10.1, 10.3**

  - [ ]* 7.3 Viết unit tests cho Authorization Engine
    - Test evaluate trả về ALLOW cho user có đủ quyền
    - Test evaluate trả về DENY cho user không có quyền
    - Test cache hit trả về kết quả ngay (< 50ms)
    - Test fail-closed khi storage error
    - File: `tests/unit/authorization/authorization-engine.test.ts`
    - _Requirements: 7.1, 7.2, 7.4, 7.6, 10.3_

- [ ] 8. Checkpoint - Đảm bảo core engine hoạt động đúng
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 9. Triển khai Route Permission Config và Data Masking
  - [ ] 9.1 Triển khai Route Permission Config (`src/lib/auth/route-permissions.ts`)
    - Định nghĩa `ROUTE_PERMISSIONS` array với patterns cho tất cả API routes hiện có
    - Implement `getRoutePermission(path, method)` tìm route permission match
    - Implement `matchRoute(pattern, path)` pattern matching hỗ trợ `:param` và `*` wildcards
    - Bao gồm routes: `/api/admin/*`, `/api/trees/:treeId/members`, `/api/trees/:treeId/media`, `/api/trees/:treeId/events`, v.v.
    - _Requirements: 4.2, 4.6_

  - [ ]* 9.2 Viết unit tests cho Route Permissions
    - Test match đúng route pattern với param extraction
    - Test wildcard matching
    - Test không match trả về null
    - Test method filtering (GET vs POST vs *)
    - File: `tests/unit/authorization/route-permissions.test.ts`
    - _Requirements: 4.2, 4.6_

  - [ ] 9.3 Triển khai Data Masking Service (`src/lib/auth/data-masking.ts`)
    - Implement `applyMasking<T>(data, context, rules)` áp dụng masking rules lên data
    - Implement `hasDetailViewPermission(context)` kiểm tra quyền xem chi tiết
    - Định nghĩa `LIVING_MEMBER_MASKING` rule: ẩn dateOfBirth, currentAddress, phone, email cho thành viên còn sống
    - Masking thay thế giá trị bằng null hoặc "[REDACTED]"
    - _Requirements: 5.6_

  - [ ]* 9.4 Viết property test cho Data Masking
    - **Property 7: Sensitive Data Masking** - Thành viên sống + user không có quyền → trường nhạy cảm bị mask
    - File: `tests/property/authorization/data-masking.property.test.ts`
    - **Validates: Requirements 5.6**

- [ ] 10. Tích hợp vào Middleware và API handlers
  - [ ] 10.1 Tạo Authorization Middleware (`src/lib/auth/authorization-middleware.ts`)
    - Implement middleware wrapper tích hợp Authorization Engine vào Next.js request pipeline
    - Sử dụng `buildAccessContext()` từ request + JWT token
    - Gọi `evaluate()` và trả về 403 nếu DENY
    - Không tiết lộ thông tin cấu trúc quyền trong error response (generic message)
    - _Requirements: 4.4, 4.5, 10.2, 10.6, 10.7_

  - [ ] 10.2 Tạo helper `requireAuthorization()` cho API route handlers
    - Tạo `src/lib/auth/require-authorization.ts` cung cấp function cho server actions/API handlers
    - Sử dụng pattern tương tự `requireAuthenticatedUserId()` và `requireTreePermission()` hiện có
    - Tích hợp Route Permission Config để tự động map route → required permission
    - Export từ `src/lib/auth/guards.ts` để giữ single entry point
    - _Requirements: 4.4, 10.2, 10.7_

  - [ ] 10.3 Cập nhật `src/lib/auth/guards.ts` export mới
    - Thêm re-exports cho authorization engine, compatibility layer
    - Đảm bảo backward compatibility: existing imports vẫn hoạt động
    - Thêm `requireAuthorization()` vào public API
    - _Requirements: 10.2 (middleware + service layer)_

- [ ] 11. Triển khai Owner Full Access và Composite Authorization
  - [ ] 11.1 Thêm Owner Full Access logic vào Policy Evaluator
    - Implement kiểm tra ownership: nếu user là owner của resource → ALLOW mọi action
    - Tích hợp vào evaluation pipeline trước policy evaluation
    - _Requirements: 5.4_

  - [ ]* 11.2 Viết property test cho Owner Full Access
    - **Property 5: Owner Full Access** - Owner luôn có toàn quyền CRUD trên resource
    - File: `tests/property/authorization/policy-evaluator.property.test.ts` (append)
    - **Validates: Requirements 5.4**

  - [ ] 11.3 Triển khai Composite Dual Authorization
    - Mở rộng Policy Evaluator hỗ trợ kiểm tra dual authorization cho composite trees
    - Kiểm tra (1) user có quyền đọc composite + (2) source tree đã consent sharing
    - Tích hợp với `requireSourceReadPermission()` và `requireSourceAdminConsent()` hiện có
    - _Requirements: 5.5_

  - [ ]* 11.4 Viết property test cho Composite Dual Authorization
    - **Property 6: Composite Dual Authorization** - Chỉ ALLOW khi cả hai điều kiện thỏa mãn
    - File: `tests/property/authorization/policy-evaluator.property.test.ts` (append)
    - **Validates: Requirements 5.5**

  - [ ]* 11.5 Viết property test cho Non-Admin Role Change Denied
    - **Property 12: Non-Admin Role Change Denied** - TREE_EDITOR/TREE_VIEWER thay đổi role → FORBIDDEN
    - File: `tests/property/authorization/role-hierarchy.property.test.ts` (append)
    - **Validates: Requirements 2.6**

- [ ] 12. Final Checkpoint - Đảm bảo toàn bộ hệ thống hoạt động
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks đánh dấu `*` là optional và có thể bỏ qua cho MVP nhanh hơn
- Mỗi task tham chiếu đến requirements cụ thể để traceability
- Checkpoints đảm bảo kiểm tra tăng dần
- Property tests kiểm tra tính đúng đắn toàn cục (14 properties)
- Unit tests kiểm tra các ví dụ cụ thể và edge cases
- Sử dụng vitest + fast-check (đã có trong devDependencies)
- Tất cả data lưu dưới dạng JSON blob qua Vercel Blob (Supabase Storage)
- Backward compatibility được đảm bảo: existing `rbac.ts` và `guards.ts` vẫn hoạt động

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["2.1", "2.3", "3.1", "5.3"] },
    { "id": 2, "tasks": ["2.2", "2.4", "2.5", "3.2", "5.4"] },
    { "id": 3, "tasks": ["5.1", "6.1"] },
    { "id": 4, "tasks": ["5.2", "6.2", "6.3"] },
    { "id": 5, "tasks": ["7.1", "9.1", "9.3"] },
    { "id": 6, "tasks": ["7.2", "7.3", "9.2", "9.4"] },
    { "id": 7, "tasks": ["10.1", "10.2", "11.1", "11.3"] },
    { "id": 8, "tasks": ["10.3", "11.2", "11.4", "11.5"] }
  ]
}
```
