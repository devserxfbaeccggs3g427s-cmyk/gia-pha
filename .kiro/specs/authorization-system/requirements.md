# Requirements Document - Hệ Thống Quản Lý Phân Quyền (Authorization System)

## Giới thiệu

Tài liệu này mô tả yêu cầu cho hệ thống quản lý phân quyền toàn diện của ứng dụng Quản lý Gia phả (Family Genealogy Management). Hệ thống được thiết kế để kiểm soát truy cập dựa trên vai trò (RBAC), kiểm soát truy cập cấp chức năng (Functional Access Control), và kiểm soát truy cập cấp dữ liệu (ABAC/Row-Level Security). Hệ thống mở rộng từ cơ chế phân quyền cây gia phả hiện tại (ADMIN/EDITOR/VIEWER) thành một kiến trúc phân quyền linh hoạt, có khả năng mở rộng cao.

## Thuật ngữ (Glossary)

- **Authorization_Engine**: Module trung tâm chịu trách nhiệm đánh giá quyền truy cập khi nhận được yêu cầu từ người dùng.
- **RBAC_Module**: Module quản lý vai trò (Role-Based Access Control), định nghĩa và phân bổ vai trò cho người dùng.
- **Permission_Registry**: Bảng đăng ký quyền, lưu trữ tập hợp các quyền hạn (permissions) có thể gán cho vai trò.
- **Policy_Evaluator**: Thành phần đánh giá chính sách truy cập dựa trên thuộc tính (Attribute-Based Access Control).
- **Access_Context**: Đối tượng chứa thông tin ngữ cảnh của một yêu cầu truy cập (user, resource, action, environment).
- **System_Role**: Vai trò cấp hệ thống áp dụng toàn ứng dụng (SUPER_ADMIN, ADMIN, MEMBER).
- **Tree_Role**: Vai trò cấp cây gia phả (TREE_ADMIN, TREE_EDITOR, TREE_VIEWER).
- **Permission**: Đơn vị quyền hạn nhỏ nhất, đại diện cho một hành động cụ thể trên một tài nguyên cụ thể.
- **Policy**: Quy tắc truy cập định nghĩa điều kiện cho phép hoặc từ chối truy cập dựa trên thuộc tính.
- **Resource_Scope**: Phạm vi dữ liệu mà một người dùng có thể truy cập, được giới hạn bởi thuộc tính (ownership, tree membership, branch).
- **Permission_Cache**: Bộ nhớ đệm quyền để tối ưu hiệu năng đánh giá quyền truy cập.
- **Audit_Logger**: Thành phần ghi log các sự kiện phân quyền cho mục đích kiểm toán.

## Requirements

### Requirement 1: Quản lý Vai trò Hệ thống (System Role Management)

**User Story:** Là một Super Admin, tôi muốn định nghĩa và quản lý các vai trò hệ thống, để có thể phân cấp quyền quản trị rõ ràng trong toàn ứng dụng.

#### Acceptance Criteria

1. THE RBAC_Module SHALL định nghĩa ba System_Role mặc định: SUPER_ADMIN, ADMIN, và MEMBER.
2. THE RBAC_Module SHALL thiết lập hệ thống kế thừa quyền trong đó SUPER_ADMIN kế thừa toàn bộ quyền của ADMIN, và ADMIN kế thừa toàn bộ quyền của MEMBER.
3. WHEN một người dùng được gán System_Role mới, THE RBAC_Module SHALL cập nhật Permission_Cache trong vòng 5 giây.
4. THE RBAC_Module SHALL đảm bảo mỗi người dùng có chính xác một System_Role tại bất kỳ thời điểm nào.
5. WHEN một System_Role bị thay đổi, THE Audit_Logger SHALL ghi lại người thực hiện, vai trò cũ, vai trò mới, và thời gian thay đổi.

---

### Requirement 2: Quản lý Vai trò Cây Gia phả (Tree Role Management)

**User Story:** Là một chủ sở hữu cây gia phả, tôi muốn phân quyền cho từng thành viên trên cây của mình, để kiểm soát ai có thể xem, chỉnh sửa, hoặc quản trị cây gia phả.

#### Acceptance Criteria

1. THE RBAC_Module SHALL định nghĩa ba Tree_Role: TREE_ADMIN, TREE_EDITOR, và TREE_VIEWER.
2. THE RBAC_Module SHALL cho phép một người dùng giữ các Tree_Role khác nhau trên các cây gia phả khác nhau.
3. WHEN chủ sở hữu cây tạo mới một cây gia phả, THE RBAC_Module SHALL tự động gán Tree_Role TREE_ADMIN cho chủ sở hữu đó.
4. WHEN một người dùng có System_Role SUPER_ADMIN truy cập bất kỳ cây gia phả nào, THE Authorization_Engine SHALL cấp quyền TREE_ADMIN cho người dùng đó trên cây đang truy cập.
5. WHEN một TREE_ADMIN mời thành viên mới, THE RBAC_Module SHALL cho phép chọn Tree_Role cho thành viên được mời (TREE_EDITOR hoặc TREE_VIEWER).
6. IF một TREE_EDITOR hoặc TREE_VIEWER cố gắng thay đổi Tree_Role của thành viên khác, THEN THE Authorization_Engine SHALL từ chối yêu cầu và trả về mã lỗi FORBIDDEN.

---

### Requirement 3: Đăng ký và Quản lý Quyền hạn (Permission Registration)

**User Story:** Là một System Architect, tôi muốn có một hệ thống đăng ký quyền tập trung, để quản lý và mở rộng quyền hạn một cách nhất quán khi ứng dụng phát triển.

#### Acceptance Criteria

1. THE Permission_Registry SHALL định nghĩa mỗi Permission theo cấu trúc: `resource:action` (ví dụ: `tree:read`, `member:create`, `member:update`, `member:delete`).
2. THE Permission_Registry SHALL nhóm các Permission theo resource, bao gồm tối thiểu: `tree`, `member`, `media`, `report`, `user`, `system`.
3. THE Permission_Registry SHALL hỗ trợ bốn action cơ bản cho mỗi resource: `create`, `read`, `update`, `delete`.
4. WHEN một Permission mới được thêm vào Permission_Registry, THE RBAC_Module SHALL cho phép gán Permission đó cho các vai trò hiện tại mà không cần khởi động lại ứng dụng.
5. THE Permission_Registry SHALL cung cấp API để truy vấn danh sách toàn bộ Permission hiện có trong hệ thống.

---

### Requirement 4: Kiểm soát Truy cập Chức năng (Functional Access Control)

**User Story:** Là một Developer, tôi muốn kiểm soát truy cập đến các tính năng và menu dựa trên vai trò và quyền hạn, để người dùng chỉ thấy và sử dụng những chức năng phù hợp.

#### Acceptance Criteria

1. THE Authorization_Engine SHALL đánh giá quyền truy cập chức năng dựa trên tổ hợp System_Role và Permission được gán cho vai trò đó.
2. WHEN một người dùng truy cập trang quản trị hệ thống, THE Authorization_Engine SHALL chỉ cho phép người dùng có System_Role ADMIN hoặc SUPER_ADMIN.
3. WHEN giao diện người dùng render menu điều hướng, THE Authorization_Engine SHALL cung cấp danh sách menu items mà người dùng hiện tại có quyền truy cập.
4. WHEN một người dùng gửi yêu cầu API đến endpoint được bảo vệ, THE Authorization_Engine SHALL kiểm tra Permission tương ứng trước khi xử lý yêu cầu.
5. IF một người dùng truy cập chức năng không được phép, THEN THE Authorization_Engine SHALL trả về HTTP status 403 kèm mã lỗi PERMISSION_DENIED.
6. THE Authorization_Engine SHALL hỗ trợ khai báo quyền yêu cầu cho mỗi route thông qua cấu hình tập trung hoặc decorator pattern.

---

### Requirement 5: Kiểm soát Truy cập cấp Dữ liệu (Data-Level Access Control)

**User Story:** Là một chủ sở hữu cây gia phả, tôi muốn kiểm soát phạm vi dữ liệu mà mỗi thành viên có thể xem hoặc chỉnh sửa, để bảo vệ thông tin nhạy cảm của gia đình.

#### Acceptance Criteria

1. THE Policy_Evaluator SHALL giới hạn truy cập dữ liệu cây gia phả dựa trên Tree_Role và membership status của người dùng.
2. WHILE một người dùng có Tree_Role TREE_VIEWER trên một cây, THE Policy_Evaluator SHALL chỉ cho phép đọc dữ liệu và từ chối mọi thao tác ghi (create, update, delete) trên cây đó.
3. WHILE một người dùng có Tree_Role TREE_EDITOR trên một cây, THE Policy_Evaluator SHALL cho phép tạo và chỉnh sửa thành viên nhưng từ chối xóa thành viên và thay đổi cấu hình cây.
4. THE Policy_Evaluator SHALL hỗ trợ Resource_Scope dựa trên ownership, trong đó chủ sở hữu tài nguyên có toàn quyền trên tài nguyên đó.
5. WHEN một cây gia phả composite tham chiếu dữ liệu từ cây nguồn, THE Policy_Evaluator SHALL đánh giá quyền truy cập dựa trên cả vai trò composite và trạng thái consent sharing của cây nguồn.
6. THE Policy_Evaluator SHALL hỗ trợ ẩn thông tin nhạy cảm (ngày sinh, nơi ở chi tiết) của thành viên còn sống đối với người dùng không có quyền xem chi tiết.

---

### Requirement 6: Mô hình Dữ liệu Phân quyền (Authorization Data Model)

**User Story:** Là một Developer, tôi muốn có mô hình dữ liệu rõ ràng cho hệ thống phân quyền, để triển khai lưu trữ và truy vấn hiệu quả.

#### Acceptance Criteria

1. THE Authorization_Engine SHALL lưu trữ thông tin vai trò hệ thống trong bảng `user_roles` với cấu trúc: user_id, system_role, assigned_by, assigned_at.
2. THE Authorization_Engine SHALL lưu trữ thông tin vai trò cây trong bảng `tree_memberships` với cấu trúc: user_id, tree_id, tree_role, invited_by, joined_at.
3. THE Permission_Registry SHALL lưu trữ quyền hạn trong bảng `permissions` với cấu trúc: id, resource, action, description.
4. THE Authorization_Engine SHALL lưu trữ mapping giữa vai trò và quyền trong bảng `role_permissions` với cấu trúc: role_type, role_name, permission_id.
5. THE Policy_Evaluator SHALL lưu trữ chính sách truy cập trong bảng `access_policies` với cấu trúc: id, name, resource, conditions (JSONB), effect (ALLOW/DENY), priority.
6. THE Audit_Logger SHALL lưu trữ log phân quyền trong bảng `authorization_logs` với cấu trúc: id, user_id, action, resource, resource_id, result (ALLOW/DENY), evaluated_policies, timestamp.

---

### Requirement 7: Quy trình Đánh giá Quyền (Authorization Evaluation Workflow)

**User Story:** Là một Developer, tôi muốn hiểu rõ quy trình đánh giá quyền khi một yêu cầu đến, để triển khai logic kiểm tra nhất quán và hiệu quả.

#### Acceptance Criteria

1. WHEN một yêu cầu truy cập đến, THE Authorization_Engine SHALL xây dựng Access_Context bao gồm: user identity, requested resource, requested action, và environment attributes.
2. THE Authorization_Engine SHALL đánh giá quyền theo thứ tự: (1) kiểm tra authentication, (2) kiểm tra System_Role permission, (3) kiểm tra Tree_Role permission nếu là tài nguyên cây, (4) áp dụng Policy rules.
3. WHEN Permission_Cache có dữ liệu hợp lệ cho Access_Context hiện tại, THE Authorization_Engine SHALL sử dụng kết quả từ cache thay vì truy vấn database.
4. IF bất kỳ bước đánh giá nào trả về DENY, THEN THE Authorization_Engine SHALL dừng đánh giá ngay lập tức và từ chối yêu cầu (deny-fast principle).
5. WHEN có nhiều Policy áp dụng cho cùng một Access_Context, THE Policy_Evaluator SHALL ưu tiên policy có hiệu lực DENY trước ALLOW (deny-overrides strategy).
6. THE Authorization_Engine SHALL hoàn tất đánh giá quyền trong thời gian dưới 50ms cho các yêu cầu có cache hit.

---

### Requirement 8: Bộ nhớ đệm Quyền (Permission Caching)

**User Story:** Là một System Architect, tôi muốn hệ thống phân quyền có hiệu năng cao, để không ảnh hưởng đến trải nghiệm người dùng khi đánh giá quyền trên mỗi request.

#### Acceptance Criteria

1. THE Permission_Cache SHALL lưu trữ kết quả đánh giá quyền theo cặp (user_id, permission_key) với thời gian sống (TTL) cấu hình được.
2. THE Permission_Cache SHALL có TTL mặc định là 300 giây (5 phút) cho quyền cấp hệ thống.
3. THE Permission_Cache SHALL có TTL mặc định là 60 giây (1 phút) cho quyền cấp cây gia phả.
4. WHEN vai trò của một người dùng bị thay đổi, THE Permission_Cache SHALL invalidate toàn bộ cache entries liên quan đến người dùng đó trong vòng 5 giây.
5. WHEN cấu hình role-permission mapping bị thay đổi, THE Permission_Cache SHALL invalidate toàn bộ cache entries liên quan đến role bị ảnh hưởng.
6. THE Permission_Cache SHALL sử dụng chiến lược cache-aside: kiểm tra cache trước, nếu miss thì đánh giá và lưu kết quả vào cache.

---

### Requirement 9: Kiểm toán và Ghi log (Audit Logging)

**User Story:** Là một Super Admin, tôi muốn theo dõi mọi quyết định phân quyền quan trọng, để có thể kiểm toán và phát hiện truy cập bất thường.

#### Acceptance Criteria

1. THE Audit_Logger SHALL ghi log cho mọi quyết định DENY từ Authorization_Engine.
2. THE Audit_Logger SHALL ghi log cho mọi thay đổi vai trò (role assignment, role removal, role change).
3. THE Audit_Logger SHALL ghi log cho mọi thay đổi cấu hình phân quyền (policy creation, policy update, permission mapping change).
4. WHEN một sự kiện phân quyền xảy ra, THE Audit_Logger SHALL ghi lại: timestamp, user_id, action, resource_type, resource_id, decision, và reason.
5. THE Audit_Logger SHALL lưu trữ log tối thiểu 90 ngày trước khi được phép archival hoặc xóa.
6. THE Audit_Logger SHALL hỗ trợ truy vấn log theo user_id, resource_id, action, hoặc khoảng thời gian.

---

### Requirement 10: Bảo mật và Phi chức năng (Security and Non-Functional)

**User Story:** Là một System Architect, tôi muốn hệ thống phân quyền đảm bảo an toàn, có khả năng mở rộng, và hoạt động ổn định, để hỗ trợ tăng trưởng ứng dụng lâu dài.

#### Acceptance Criteria

1. THE Authorization_Engine SHALL áp dụng nguyên tắc least privilege: mặc định từ chối mọi truy cập nếu không có quyền hạn được cấp rõ ràng.
2. THE Authorization_Engine SHALL thực thi kiểm tra phân quyền ở cả tầng middleware (cho routes) và tầng service (cho business logic).
3. IF Authorization_Engine phát hiện lỗi trong quá trình đánh giá quyền (database timeout, cache failure), THEN THE Authorization_Engine SHALL từ chối yêu cầu (fail-closed) và ghi log lỗi.
4. THE Authorization_Engine SHALL hỗ trợ tối thiểu 100 concurrent authorization evaluations mà không suy giảm hiệu năng đáng kể.
5. THE Permission_Registry SHALL cho phép thêm resource và permission mới mà không yêu cầu thay đổi schema database (extensible design).
6. THE Authorization_Engine SHALL không tiết lộ thông tin chi tiết về cấu trúc quyền trong response trả về client khi từ chối truy cập.
7. WHEN triển khai kiểm tra phân quyền server-side, THE Authorization_Engine SHALL sử dụng session token đã được xác thực và không tin tưởng bất kỳ dữ liệu phân quyền nào từ client.
