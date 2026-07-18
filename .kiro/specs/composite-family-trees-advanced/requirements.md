# Requirements Document: Composite Family Trees (Advanced / Deferred)

## Status

**DEFERRED — DO NOT IMPLEMENT BEFORE `../composite-family-trees` IS COMPLETE**

This spec is intentionally prepared for a later phase. It extends the MVP
without changing its core contract: standalone trees remain independent,
identity links remain explicit and every operation remains auditable.

## Prerequisites and Activation Gate

The implementation team SHALL first complete and verify every task in
`../composite-family-trees/tasks.md`, including the eight-branch integration,
privacy tests, backup/restore tests and performance budget. This spec may be
activated only after an explicit product decision because several features add
new write paths and operational complexity.

## Introduction

The advanced layer covers the later “tách nhánh như tách hộ khẩu” workflow and
controlled reconciliation between branch trees and overview trees. It provides
branch extraction, origin lineage, source version diffs, human-reviewed field
conflict resolution, materialization of a frozen snapshot, optional nested
composition as a guarded DAG, and mutation routing through an explicitly chosen
authority source.

The design deliberately does not make a globally collaborative tree. It keeps
source ownership and privacy boundaries while reducing repeated manual work.

## Glossary

- **Branch_Extraction**: operation that creates a new Standalone_Tree from a
  selected branch of another tree
- **Origin_Reference**: immutable link from a copied record to its source record
- **Fork**: a new tree copied at a known source revision, with no automatic
  future write propagation until reconciliation
- **Authority_Source**: selected source that is allowed to receive a mutation
  for an Identity_Group or field
- **Source_Version**: version/hash manifest for one standalone tree's relevant
  blobs
- **Change_Diff**: source changes detected since a previous composite resolve
- **Reconciliation**: human-reviewed process for accepting, rejecting or
  deferring a Change_Diff
- **Conflict**: two sources provide incompatible values or relationship facts
- **Materialized_Snapshot**: new Standalone_Tree containing a resolved copy of
  a Composite_Tree at a fixed point in time
- **Nested_Composite**: Composite_Tree that uses another Composite_Tree as a
  source, allowed only under explicit DAG and depth limits

## Requirements

### Requirement 1: Tách Nhánh thành Gia phả Độc lập

**User Story:** Là một Admin, tôi muốn tách một nhánh đông thành gia phả riêng,
để nhánh đó tự quản lý mà không làm mất gia phả gốc.

#### Acceptance Criteria

1. WHEN Admin chọn một Member làm Boundary_Anchor, THE Application SHALL preview
   danh sách Member, Relationship, Event và Media sẽ được tách theo scope
2. THE Branch_Extraction SHALL hỗ trợ `DESCENDANTS`, `DESCENDANTS_WITH_SPOUSES`
   và `SELECTED_MEMBERS` cùng chính sách giữ ancestor context
3. WHEN xác nhận tách, THE Application SHALL tạo một Standalone_Tree mới và
   SHALL NOT sửa hoặc xóa dữ liệu cây gốc
4. THE Application SHALL tạo Origin_Reference cho mọi Member, Relationship,
   Event và Media được sao chép
5. IDs trong cây mới SHALL khác IDs cây gốc; Origin_Reference SHALL là dữ liệu
   tham chiếu, không phải định danh nghiệp vụ thay thế ID mới
6. THE Application SHALL cung cấp manifest về số bản ghi, source revision,
   người tạo, thời gian và chính sách sao chép
7. IF sao chép một phần thất bại, THEN THE Application SHALL rollback toàn bộ
   cây mới hoặc đánh dấu bản nháp không thể công bố; SHALL NOT tạo cây mới
   trông như đã hoàn tất nhưng thiếu quan hệ

### Requirement 2: Liên kết Nguồn sau khi Tách

**User Story:** Là một Admin, tôi muốn gia phả tổng hợp dùng cây nhánh mới làm
nguồn chính, để các cập nhật tương lai của nhánh được phản ánh đúng.

#### Acceptance Criteria

1. WHEN Branch_Extraction hoàn tất, THE Application SHALL đề xuất Identity_Group
   giữa Boundary_Anchor ở cây gốc và bản sao tương ứng ở cây mới
2. THE Application SHALL cho phép xác nhận đề xuất đó trong Composite_Tree mà
   không tự động merge hai Member nguồn
3. THE Application SHALL cho phép cấu hình cây cũ chỉ cung cấp ancestor context
   và cây mới cung cấp hậu duệ nhánh, tránh hiển thị bản sao descendants
4. WHEN Admin detach Origin_Reference, THE Application SHALL tháo đề xuất đồng
   bộ nhưng SHALL NOT xóa bản ghi ở bất kỳ cây nào
5. THE Application SHALL hiển thị rõ cây nào là nguồn chính của mỗi nhánh và
   cảnh báo khi hai cây cùng được chọn là authority

### Requirement 3: Phiên bản và Phát hiện Thay đổi

**User Story:** Là một Editor, tôi muốn biết một cây nhánh đã thay đổi gì kể từ
last sync, để kiểm tra trước khi đưa thay đổi vào overview.

#### Acceptance Criteria

1. THE Application SHALL lưu Source_Version manifest tại mỗi lần resolve hoặc
   reconciliation thành công
2. WHEN version hiện tại khác version đã lưu, THE Application SHALL tạo
   Change_Diff theo Member, Relationship, Event, Media và field
3. THE Change_Diff SHALL phân biệt CREATE, UPDATE, DELETE, LINK_CHANGE và
   PERMISSION_CHANGE
4. THE Application SHALL không coi thay đổi từ một Source_Tree mà User không còn
   quyền đọc là dữ liệu đã xóa; thay đổi đó SHALL là `SOURCE_UNAVAILABLE`
5. THE Application SHALL cho phép xem trước diff có Provenance và thời gian,
   không hiển thị field riêng tư nếu người xem không có quyền
6. THE Application SHALL giữ diff idempotent: đọc lại cùng source versions không
   tạo thêm diff mới

### Requirement 4: Đối soát và Giải quyết Xung đột

**User Story:** Là một Admin, tôi muốn duyệt các khác biệt giữa các cây, để
kiểm soát dữ liệu nào được chấp nhận mà không ghi đè âm thầm.

#### Acceptance Criteria

1. THE Application SHALL cung cấp workflow `OPEN`, `IN_REVIEW`, `ACCEPTED`,
   `REJECTED`, `DEFERRED` cho mỗi Change_Diff
2. WHEN nhiều nguồn thay đổi cùng field của một Identity_Group, THE Application
   SHALL tạo Conflict thay vì chọn ngầm theo thứ tự đọc
3. Admin SHALL có thể chọn giá trị của một nguồn, giữ giá trị hiện tại, thêm
   ghi chú hoặc đánh dấu chưa đủ bằng chứng
4. Mọi quyết định SHALL lưu actor, timestamp, reason, source references, source
   versions và dữ liệu trước/sau
5. ACCEPTED diff SHALL chỉ ghi vào Authority_Source đã cấu hình và SHALL yêu
   cầu quyền ghi phù hợp; nếu không có quyền ghi, quyết định SHALL chuyển thành
   `PENDING_SOURCE_ACTION`
6. REJECTED hoặc DEFERRED diff SHALL không sửa dữ liệu nguồn và SHALL không làm
   mất diff gốc
7. Relationship conflict SHALL được xử lý độc lập với field conflict và phải
   chạy lại cycle/integrity validation trước khi publish

### Requirement 5: Authority Source và Ghi Có Kiểm Soát

**User Story:** Là chủ nhiều cây, tôi muốn chỉ định nơi nhận chỉnh sửa, để một
người không bị sửa ở hai nơi cùng lúc.

#### Acceptance Criteria

1. THE Application SHALL cho phép cấu hình Authority_Source cho Identity_Group
   và tùy chọn theo field/relationship domain
2. Một Authority_Source SHALL phải là Source_Tree standalone và User thực hiện
   ghi SHALL có permission tương ứng tại thời điểm mutation
3. Khi chỉnh sửa từ Composite_Tree, THE Application SHALL hiển thị trước cây
   đích, fields sẽ thay đổi và version kỳ vọng
4. Mutation SHALL dùng optimistic concurrency; version cũ SHALL tạo conflict
   thay vì last-write-wins
5. Nếu Authority_Source không khả dụng hoặc User mất quyền, THE Application
   SHALL tạo PENDING_SOURCE_ACTION và SHALL NOT ghi sang nguồn thay thế
6. Các mutation được route từ composite SHALL ghi audit log cả ở composite và
   source tree

### Requirement 6: Materialize Overview thành Snapshot

**User Story:** Là một Admin, tôi muốn đóng băng gia phả tổng quan để in, lưu
trữ hoặc chuyển giao mà không còn phụ thuộc nguồn trực tiếp.

#### Acceptance Criteria

1. WHEN Admin materialize, THE Application SHALL preview số node, edge, event,
   media, conflicts và unavailable sources trước khi xác nhận
2. THE Application SHALL tạo Standalone_Tree mới với IDs mới, Origin_Reference
   và manifest của composite revision cùng source versions
3. Snapshot SHALL preserve confirmed identities as merged records only within
   the new tree; source trees SHALL remain unchanged
4. THE Application SHALL cho phép chọn policy field: preferred source,
   non-empty merge hoặc chọn thủ công trước khi materialize
5. Snapshot SHALL ghi rõ `materializedFromCompositeId` và SHALL NOT tự động trở
   thành source của composite cũ
6. Nếu materialize thất bại, THE Application SHALL rollback cây mới hoặc giữ ở
   trạng thái draft không thể truy cập như một cây hoàn tất

### Requirement 7: Composite Lồng nhau có Kiểm soát

**User Story:** Là một tổ chức lớn, tôi muốn tái sử dụng overview của một vùng
để xây overview cấp cao hơn, nhưng không muốn tạo vòng lặp hoặc tải vô hạn.

#### Acceptance Criteria

1. THE Application SHALL cho phép Composite_Tree làm source chỉ khi feature flag
   được bật và source đã publish
2. THE Application SHALL xây dependency DAG và từ chối self-cycle hoặc cycle
   giữa nhiều composite
3. THE Application SHALL giới hạn depth mặc định ở 3 và tổng số expanded source
   tree ở 100
4. Quyền đọc SHALL được kiểm tra xuyên từng source; composite trung gian không
   được dùng để vượt qua quyền cây gốc
5. Provenance SHALL giữ toàn bộ chuỗi nguồn và UI SHALL cho phép mở nguồn
   standalone cuối cùng
6. Khi composite con không khả dụng, composite cha SHALL thể hiện trạng thái
   dependency unavailable thay vì dùng dữ liệu cũ như dữ liệu mới

### Requirement 8: Thông báo, Review và Vận hành

**User Story:** Là quản trị viên, tôi muốn được thông báo khi nhánh có thay đổi
hoặc quyền bị mất, để overview không âm thầm lỗi thời.

#### Acceptance Criteria

1. THE Application SHALL tạo notification khi phát hiện source version mới,
   conflict, source unavailable hoặc pending source action
2. Notification SHALL không chứa dữ liệu Member nhạy cảm trong nội dung preview
3. Admin SHALL có thể assign review cho thành viên có quyền phù hợp và ghi chú
4. Dashboard SHALL hiển thị số diff mở, conflict mở, source stale và source
   unavailable theo từng composite
5. Các job diff/reconciliation SHALL idempotent, có retry giới hạn và audit log
6. THE Application SHALL cung cấp metrics về độ trễ resolve, số conflict,
   nguồn lỗi, số mutation pending và kích thước snapshot

### Requirement 9: Bảo mật, Retention và Khôi phục Nâng cao

**User Story:** Là chủ dữ liệu gia đình, tôi muốn lịch sử đối soát có thể truy
vết và khôi phục nhưng vẫn tôn trọng quyền riêng tư.

#### Acceptance Criteria

1. History của merge/reconciliation SHALL immutable đối với Viewer và chỉ Admin
   mới được thực hiện thao tác undo trong thời hạn policy
2. Undo SHALL tạo một mutation mới có liên kết đến quyết định cũ, không sửa log
   lịch sử trực tiếp
3. Backup SHALL bao gồm source manifests, extraction manifests, diffs,
   decisions, conflicts và pending actions theo retention policy
4. Restore SHALL kiểm tra lại permission, source version và DAG trước khi
   activate
5. Export/share SHALL không làm lộ Origin_Reference hoặc chain provenance nhạy
   cảm nếu audience không được phép
6. Media copied bởi Branch_Extraction SHALL có ownership và cleanup policy rõ;
   xóa bản sao SHALL không xóa media nguồn

### Requirement 10: Hiệu năng và Giới hạn

**User Story:** Là đội vận hành, tôi muốn tính năng nâng cao dự đoán được chi
phí và độ trễ khi dữ liệu lớn.

#### Acceptance Criteria

1. Branch_Extraction SHALL dùng job có progress, resumability và checksum thay
   vì giữ một request HTTP dài
2. Diff SHALL đọc theo manifest/batch và SHALL không tải lại media binary để
   phát hiện thay đổi metadata
3. Reconciliation SHALL xử lý theo page/batch và có giới hạn mutation mỗi job
4. Nested composite SHALL dùng memoized dependency resolution và phát hiện vòng
   lặp trước khi tải toàn bộ nguồn
5. Materialization SHALL có estimate dung lượng và từ chối khi vượt quota trước
   khi bắt đầu copy binary
6. Metrics SHALL cảnh báo khi p95 resolve, extraction hoặc materialization vượt
   ngưỡng đã cấu hình

## Out of Scope for Advanced Spec

- Một global person registry dùng chung cho mọi tài khoản
- Tự động merge không cần con người duyệt
- Xóa dữ liệu nguồn như một phần của merge hoặc split
- Đồng bộ realtime hai chiều không có optimistic concurrency/conflict review
- Cho phép viewer ghi dữ liệu hoặc vượt qua source privacy
