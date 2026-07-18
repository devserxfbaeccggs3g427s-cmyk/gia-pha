# Requirements: Composite Family Trees (MVP)

## Trạng thái

**SẴN SÀNG TRIỂN KHAI**

Spec này mở rộng `../family-genealogy-management/requirements.md` theo hướng
tương thích ngược hoàn toàn. Mọi hành vi hiện có của gia phả độc lập
(`STANDALONE`) **phải tiếp tục hoạt động không thay đổi**. Các tính năng nâng
cao chưa triển khai thuộc `../composite-family-trees-advanced/requirements.md`.

---

## Glossary

| Thuật ngữ | Định nghĩa |
|---|---|
| `Standalone_Tree` | `FamilyTree` với `kind = 'STANDALONE'`; chế độ duy nhất trước khi có spec này |
| `Composite_Tree` | `FamilyTree` với `kind = 'COMPOSITE'`; tổng hợp dữ liệu từ nhiều `Source_Tree` |
| `Source_Tree` | `Standalone_Tree` được đưa vào `Composite_Tree` làm nguồn dữ liệu |
| `Composite_Source` | Cấu hình phạm vi và chính sách đọc một `Source_Tree` trong composite |
| `Source_Reference` | Cặp `{ treeId, memberId }` trỏ đến `Member` gốc trong một `Source_Tree` |
| `Source_Scope` | Phạm vi member được lấy từ một `Source_Tree` |
| `Boundary_Anchor` | `memberId` dùng làm điểm bắt đầu khi lấy hậu duệ (`DESCENDANTS` scope) |
| `Identity_Group` | Nhóm `Source_Reference` được Admin xác nhận là cùng một người thật |
| `Virtual_Member` | Node được tạo khi resolve; không được lưu trữ, chỉ tồn tại trong bộ nhớ |
| `Cross_Tree_Relationship` | Quan hệ chỉ tồn tại trong config composite, nối hai `Source_Reference` của hai `Source_Tree` khác nhau |
| `Resolved_Graph` | Đồ thị phẳng được dựng từ config composite và dữ liệu nguồn tại thời điểm đọc |
| `Provenance` | Siêu dữ liệu cho biết `Virtual_Member` / quan hệ đến từ `Source_Tree` và bản ghi nào |
| `Unavailable_Source` | `Source_Tree` đã bị xóa, người dùng mất quyền, hoặc tạm thời không đọc được |
| `Composite_Config` | Blob JSON `composite-config.json` lưu toàn bộ cấu hình của một `Composite_Tree` |
| `Resolved_Graph` | Kết quả in-memory sau khi `CompositeResolver` xử lý; không được lưu trữ lâu dài |

---

## Requirements

### Requirement 1: Phân loại và Tạo Gia phả Tổng hợp

**User Story:** Là một Admin, tôi muốn tạo gia phả tổng hợp tách biệt với gia
phả độc lập để xem nhiều nhánh gia đình trong một cây mà không thay đổi dữ liệu
nguồn.

#### Acceptance Criteria

1. `FamilyTree.kind` SHALL có hai giá trị hợp lệ: `'STANDALONE'` và
   `'COMPOSITE'`. Bản ghi cũ không có trường `kind` SHALL được normalize thành
   `'STANDALONE'` tại lớp đọc; **không cần migration dữ liệu hàng loạt**.

2. WHEN Admin tạo `Composite_Tree`, THE Application SHALL:
   - Tạo bản ghi `FamilyTree` với `kind = 'COMPOSITE'` trong `trees.json`.
   - Tạo blob `data/trees/{treeId}/composite-config.json` với `CompositeTreeConfig`
     rỗng (`sources: []`, `identityGroups: []`, `crossTreeRelationships: []`,
     `schemaVersion: 1`, `revision: 0`).
   - **KHÔNG** tạo `members.json`, `relationships.json`, `events.json` hay
     `media-metadata.json` trong thư mục composite.

3. THE Application SHALL từ chối đưa `Composite_Tree` làm `Source_Tree` của bất
   kỳ composite nào trong MVP (nested composite thuộc advanced spec).

4. THE Application SHALL phát hiện và từ chối vòng lặp tham chiếu: một
   `Composite_Tree` không được trực tiếp hoặc gián tiếp tham chiếu chính nó.

5. WHEN liệt kê gia phả, THE Application SHALL phân biệt rõ loại bằng badge
   "Độc lập" / "Tổng hợp" và cho phép lọc theo `kind`.

---

### Requirement 2: Quản lý Nguồn và Phạm vi Dữ liệu

**User Story:** Là một Admin của `Composite_Tree`, tôi muốn chọn cây nguồn và
phạm vi của từng cây để chỉ đưa đúng nhánh cần thiết vào tổng quan.

#### Acceptance Criteria

1. WHEN Admin thêm `Source_Tree`, THE Application SHALL xác nhận:
   - `Source_Tree` tồn tại và có `kind = 'STANDALONE'`.
   - Admin có quyền `READ` trên `Source_Tree` đó tại thời điểm thêm.
   - Số lượng `Composite_Source` hiện tại < 20 (giới hạn MVP).

2. THE Application SHALL hỗ trợ ba giá trị `Source_Scope`:
   - `FULL_TREE`: lấy toàn bộ member của `Source_Tree`.
   - `DESCENDANTS`: lấy mọi hậu duệ qua cạnh `PARENT_CHILD` chuẩn từ mọi
     `Boundary_Anchor`; union các tập reachable từ nhiều anchor.
   - `SELECTED_MEMBERS`: chỉ lấy đúng danh sách `selectedMemberIds`.

3. IF `scope = 'DESCENDANTS'`, THEN THE Application SHALL yêu cầu ít nhất một
   `Boundary_Anchor` hợp lệ (tồn tại trong `Source_Tree`).

4. WHEN `includeSpouses = true`, THE Application SHALL thêm vợ/chồng trực tiếp
   của mọi member trong scope làm ngữ cảnh cùng đời. Vợ/chồng được thêm vào
   scope BUT tổ tiên hoặc hậu duệ riêng của họ SHALL NOT được lấy thêm nếu họ
   chưa thuộc scope.

5. IF `scope = 'SELECTED_MEMBERS'`, THE Application SHALL chỉ đưa vào
   `Resolved_Graph`:
   - Các member có ID trong `selectedMemberIds`.
   - Các quan hệ có **cả hai** endpoint thuộc tập đã chọn.

6. WHEN Admin sửa scope hoặc xóa `Composite_Source`, THE Application SHALL chỉ
   ghi vào `composite-config.json`. **Không sửa, không xóa bất kỳ blob nào của
   `Source_Tree`**.

7. THE Application SHALL cung cấp endpoint preview trả về số lượng member, quan
   hệ, event, media dự kiến **trước khi** Admin xác nhận thêm nguồn.

---

### Requirement 3: Xác nhận Cùng một Người giữa Nhiều Cây

**User Story:** Là một Admin, tôi muốn xác nhận các hồ sơ ở nhiều cây là cùng
một người để người đó chỉ xuất hiện một lần trong gia phả tổng hợp.

#### Acceptance Criteria

1. THE Application SHALL gợi ý cặp `Source_Reference` tương đồng dựa trên tiêu
   chí: họ tên đã normalize (bao gồm không dấu), ngày sinh, nơi sinh và quan hệ
   gia đình lân cận. Gợi ý có trạng thái `PROPOSED`.

2. THE Application SHALL **không** tự động xác nhận bất kỳ cặp nào là cùng
   người mà không có hành động rõ ràng của Admin.

3. WHEN Admin xác nhận cùng người, THE Application SHALL:
   - Tạo hoặc cập nhật `CompositeIdentityGroup` với `status = 'CONFIRMED'`.
   - **Không** merge, sửa hoặc xóa `Member` nguồn.

4. `IdentityLinkStatus` có ba giá trị: `'PROPOSED'`, `'CONFIRMED'`,
   `'REJECTED'`. Chỉ `'CONFIRMED'` ảnh hưởng `Resolved_Graph`.

5. Một `Source_Reference` SHALL thuộc **tối đa một** `Identity_Group` đã xác
   nhận trong cùng `Composite_Tree`. Hệ thống SHALL từ chối tạo group thứ hai
   chứa cùng `Source_Reference`.

6. WHEN Admin gỡ xác nhận (xóa `Identity_Group` hoặc set `REJECTED`), THE
   Application SHALL:
   - Khôi phục `Virtual_Member` riêng biệt trong lần resolve tiếp theo.
   - Không làm mất dữ liệu trong bất kỳ `Source_Tree` nào.

7. WHEN dữ liệu của các `Source_Reference` đã xác nhận mâu thuẫn nhau, THE
   Application SHALL:
   - Dùng `preferredReference` để chọn nguồn hiển thị chính.
   - Đặt `VirtualMember.hasConflictingFields = true`.
   - Hiển thị cảnh báo xung đột kèm `Provenance` từng trường.
   - **Không** ghi đè dữ liệu giữa các nguồn trong MVP.

---

### Requirement 4: Quan hệ Nối giữa các Cây

**User Story:** Là một Admin, tôi muốn tạo quan hệ giữa thành viên của các cây
nguồn để các nhánh riêng tạo thành một đồ thị gia phả hoàn chỉnh.

#### Acceptance Criteria

1. THE Application SHALL cho phép tạo `Cross_Tree_Relationship` giữa hai
   `Source_Reference` **thuộc hai `Composite_Source` khác nhau** đang hoạt động
   trong composite.

2. `Cross_Tree_Relationship` SHALL hỗ trợ cùng `RelationType` và metadata quan
   hệ chuẩn (`PARENT_CHILD`, `SPOUSE`, `SIBLING`, `ADOPTED`, `CUSTOM`,
   `marriageDate`, `divorceDate`, `marriageStatus`).

3. WHEN tạo `Cross_Tree_Relationship`, THE Application SHALL xác nhận:
   - Cả hai endpoint tồn tại trong `Source_Tree` tương ứng.
   - Cả hai endpoint nằm trong scope đang hoạt động.
   - Actor có quyền Admin/quản trị `Composite_Tree`.

4. THE Application SHALL chuẩn hóa mỗi quan hệ nghiệp vụ thành một cạnh canonical
   và loại bỏ cạnh trùng sau khi áp dụng `Identity_Group` mapping.

5. IF quan hệ `PARENT_CHILD` mới tạo chu trình trong toàn `Resolved_Graph`
   (bao gồm cả quan hệ từ `Source_Tree`), THEN THE Application SHALL từ chối và
   trả về danh sách ID các node tạo thành chu trình.

6. WHEN xóa `Cross_Tree_Relationship`, THE Application SHALL chỉ xóa entry
   trong `composite-config.json`. **Không xóa quan hệ nào trong `Source_Tree`**.

---

### Requirement 5: Phân giải Đồ thị Tổng hợp

**User Story:** Là một User, tôi muốn xem một đồ thị thống nhất và nhất quán để
không cần biết dữ liệu đang nằm trong cây nguồn nào.

#### Acceptance Criteria

1. WHEN đọc `Composite_Tree`, `CompositeResolver` SHALL:
   - Tải `CompositeTreeConfig`.
   - Kiểm tra quyền `READ` của viewer **độc lập** với từng `Source_Tree`.
   - Tải các blob nguồn được phép song song (bounded concurrency).
   - Áp dụng scope, `Identity_Group`, `Cross_Tree_Relationship`.
   - Trả về `ResolvedTreeData`.

2. Virtual ID SHALL được tạo bằng hash ổn định, không phụ thuộc thứ tự array
   hay trường có thể thay đổi:
   ```
   grouped:   vm_<sha256(compositeTreeId + identityGroupId)[:12]>
   ungrouped: vm_<sha256(compositeTreeId + sourceTreeId + memberId)[:12]>
   relation:  vr_<sha256(compositeTreeId + canonicalLogicalKey)[:12]>
   event:     ve_<sha256(compositeTreeId + sourceTreeId + eventId)[:12]>
   media:     vx_<sha256(compositeTreeId + sourceTreeId + mediaId)[:12]>
   ```
   Hash dùng encoding URL-safe. Không phải ranh giới bảo mật.

3. WHEN nhiều quan hệ nguồn trở thành cùng một cạnh canonical sau khi áp dụng
   `Identity_Group`, `CompositeResolver` SHALL trả về **đúng một cạnh** và gộp
   toàn bộ `Provenance` vào cạnh đó.

4. `CompositeResolver` SHALL **không** tạo self-edge khi hai endpoint của một
   quan hệ được ánh xạ về cùng `Virtual_Member`.

5. `CompositeResolver` SHALL chạy kiểm tra chu trình `PARENT_CHILD` trên đồ thị
   sau hợp nhất. Config không hợp lệ SHALL không được publish.

6. Thuật toán tính generation và ancestry SHALL được áp dụng trên `Resolved_Graph`
   theo đúng quy tắc trong Requirement 3 và 4 của
   `../family-genealogy-management/requirements.md`.

7. FOR ALL cấu hình hợp lệ và cùng phiên bản dữ liệu nguồn, mọi lần resolve
   SHALL tạo đồ thị tương đương và **ID ổn định** bất kể thứ tự fetch source.

---

### Requirement 6: Quyền Chỉnh sửa và Điều hướng về Nguồn

**User Story:** Là một Editor, tôi muốn biết phải chỉnh sửa dữ liệu ở đâu để
tránh tạo nhiều nguồn sự thật cho cùng một người.

#### Acceptance Criteria

1. `Member`, `Relationship`, `Event` và `MediaMetadata` được resolve từ
   `Source_Tree` SHALL là **dữ liệu chỉ đọc** trong `Composite_Tree`.

2. IF User cố tạo, sửa hoặc xóa dữ liệu nguồn qua mutation endpoint của
   `Composite_Tree`, THEN THE Application SHALL trả lỗi
   `COMPOSITE_READ_ONLY` (HTTP 422).

3. WHEN User xem `Virtual_Member`, THE Application SHALL hiển thị `Provenance`
   và liên kết "Mở trong cây nguồn" trỏ đến `Source_Tree` tương ứng.

4. WHEN `Virtual_Member` có nhiều `Source_Reference`, THE Application SHALL hiển
   thị nguồn chính (`preferredReference`) và danh sách các nguồn còn lại.

5. Composite Admin SHALL được phép sửa `CompositeTreeConfig` (nguồn, scope,
   `Identity_Group`, `Cross_Tree_Relationship`) **mà không cần** quyền ghi trên
   `Source_Tree`.

---

### Requirement 7: Phân quyền và Quyền riêng tư Xuyên Cây

**User Story:** Là chủ sở hữu cây nguồn, tôi muốn composite không làm lộ dữ
liệu cho người không được phép để quyền riêng tư của từng nhánh được giữ nguyên.

#### Acceptance Criteria

1. THE Application SHALL **không** suy ra quyền đọc `Source_Tree` từ việc là
   thành viên của `Composite_Tree`. Quyền Source phải được cấp độc lập.

2. WHEN User xem `Composite_Tree`, THE Application SHALL chỉ resolve đầy đủ
   những `Source_Tree` mà User có quyền `READ`.

3. IF User không có quyền trên một `Source_Tree`, THE Application SHALL:
   - Ẩn hoàn toàn dữ liệu từ nguồn đó.
   - Hiển thị anonymous boundary placeholder (chỉ chứa virtual ID và nhãn chung
     như "Nhánh riêng tư") **khi và chỉ khi** cần giữ tính liên tục của đồ thị
     cho một `Cross_Tree_Relationship` bị đứt endpoint.
   - Placeholder **không** chứa tên, ngày tháng, giới tính, số đếm hoặc thông
     tin cá nhân bất kỳ.

4. WHEN quyền `Source_Tree` bị thu hồi hoặc `Source_Tree` bị xóa, THE Application
   SHALL đánh dấu nguồn `UNAVAILABLE` trong lần đọc tiếp theo. **Không dùng
   cache để vượt qua quyền hiện tại**.

5. WHEN chia sẻ `Composite_Tree` bằng share link, THE Application SHALL chỉ đưa
   `Source_Tree` vào response khi **cả hai** điều kiện thỏa:
   - `CompositeSource.allowCompositeSharing = true`.
   - Actor bật flag này có quyền `ADMIN` trên `Source_Tree` tại thời điểm bật.

6. Share link SHALL ẩn mặc định các trường nhạy cảm của người còn sống:
   `phone`, `email`, `currentAddress`, `notes`. Mở thêm trường SHALL cần
   consent rõ ràng (`CompositeSource.shareLivingDetails = true`) từ source
   Admin.

7. THE Application SHALL ghi audit log cho **mọi** thay đổi cấu hình composite,
   bao gồm: actor ID, timestamp, action, dữ liệu trước/sau và `Source_Reference`
   liên quan. Log SHALL không chứa trường nhạy cảm của người còn sống.

---

### Requirement 8: Trực quan hóa và Trải nghiệm Người dùng

**User Story:** Là một Viewer, tôi muốn `Composite_Tree` có trải nghiệm xem giống
gia phả thường nhưng thể hiện rõ nguồn và trạng thái liên kết.

#### Acceptance Criteria

1. `Tree_Viewer` SHALL hiển thị `Resolved_Graph` bằng tất cả chế độ xem được hỗ
   trợ cho `Standalone_Tree` (cây dọc, cây ngang, fan chart).

2. `Tree_Viewer` SHALL hỗ trợ highlight, zoom, pan, minimap, lazy loading (> 100
   node) và chế độ dòng dõi trên `Resolved_Graph`.

3. WHEN hiển thị `Virtual_Member`, badge nguồn có thể được bật/tắt mà không làm
   thay đổi quy tắc màu giới tính, generation layer và trạng thái còn sống/đã
   mất.

4. THE Application SHALL cung cấp wizard tạo composite theo thứ tự:
   1. Đặt tên và mô tả.
   2. Chọn nguồn và cấu hình scope.
   3. Xem preview số lượng member/quan hệ/event/media.
   4. Xử lý gợi ý trùng lặp người (`Identity_Group`).
   5. Thêm và xem xét `Cross_Tree_Relationship`.
   6. Validate đồ thị.
   7. Publish.

   Draft config có thể lưu giữa các bước. Draft chưa publish SHALL không được
   truy cập qua route xem thông thường.

5. IF `Composite_Tree` có gợi ý trùng chưa xử lý, nguồn không khả dụng hoặc
   xung đột dữ liệu, THE Application SHALL hiển thị trạng thái cần xem xét rõ
   ràng (không chặn xem, nhưng có indicator).

6. UI SHALL hỗ trợ tiếng Việt và tiếng Anh, responsive và WCAG 2.1 AA như
   `../family-genealogy-management/requirements.md` Requirement 10 và 13.

---

### Requirement 9: Search, Report, Event, Media và Export

**User Story:** Là một User, tôi muốn các tính năng đọc hiện có hoạt động đầy đủ
trên gia phả tổng hợp.

#### Acceptance Criteria

1. `Search_Engine` SHALL tìm và lọc `Virtual_Member` trên `Resolved_Graph`,
   trả về `Provenance` cùng mỗi kết quả.

2. `Report_Generator` SHALL đếm mỗi `Virtual_Member` **đúng một lần** sau khi
   áp dụng `Identity_Group` (không đếm trùng người đã confirmed).

3. Event và Media từ `Source_Tree` SHALL được ánh xạ đến `Virtual_Member` tương
   ứng qua virtual ID và tuân theo quyền đọc của nguồn.

4. WHEN xuất `Composite_Tree` ra PDF, PNG, SVG hoặc GEDCOM, `Export_Service`
   SHALL xuất snapshot phẳng của `Resolved_Graph` tại thời điểm yêu cầu, kèm
   timestamp.

5. WHEN xuất `COMPOSITE_JSON`, `Export_Service` SHALL lưu:
   - `CompositeTreeConfig` (cấu hình liên kết).
   - `Provenance` của từng entity.
   - Version/hash nguồn tại thời điểm export.
   - **Không** nhúng dữ liệu cá nhân của nguồn nếu actor không có quyền đọc
     nguồn đó.

6. Import `COMPOSITE_JSON` SHALL khôi phục cấu hình tham chiếu. Nguồn không tồn
   tại hoặc chưa được cấp quyền SHALL được đánh dấu `UNAVAILABLE`. **Không tự
   động sao chép snapshot thành dữ liệu nguồn**.

---

### Requirement 10: Cập nhật, Cache và Offline

**User Story:** Là một User, tôi muốn overview phản ánh thay đổi từ các nhánh
và vẫn xem được khi mất mạng mà không gây hiểu nhầm về độ mới dữ liệu.

#### Acceptance Criteria

1. WHEN `Source_Tree` thay đổi, THE Application SHALL làm mất hiệu lực cache
   liên quan hoặc phát hiện source version/hash khác trong lần refresh tiếp theo.

2. `ResolvedTreeData` SHALL chứa: `resolvedAt` (ISO timestamp), `configRevision`,
   `stale` (boolean) và `sourceManifest` (version của từng nguồn).

3. WHILE offline, THE Application SHALL cho phép xem cache gần nhất **nếu và
   chỉ nếu** phiên hiện tại đã từng có quyền truy cập, và SHALL hiển thị rõ ràng
   rằng dữ liệu có thể cũ (`stale = true`).

4. THE Application SHALL **không** cho phép thay đổi `CompositeTreeConfig` khi
   offline trong MVP.

5. WHEN trở lại online, THE Application SHALL xác thực lại quyền trước khi làm
   mới hoặc tiếp tục hiển thị dữ liệu nguồn đã cache.

---

### Requirement 11: Vòng đời, Backup và Khả năng Phục hồi

**User Story:** Là một Admin, tôi muốn tháo liên kết và khôi phục cấu hình an
toàn mà không vô tình làm hỏng dữ liệu các nhánh.

#### Acceptance Criteria

1. WHEN xóa `Composite_Tree`, THE Application SHALL xóa:
   - `FamilyTree` metadata.
   - `composite-config.json`.
   - Composite audit log.
   - Share link của composite.
   - Cache/manifest đã tạo.

   THE Application SHALL **không** xóa bất kỳ `Source_Tree`, `Source_Tree` blob
   hay media nào.

2. Backup `Composite_Tree` SHALL lưu: `FamilyTree` metadata, `CompositeTreeConfig`,
   audit log và `sourceManifest` (version/hash nguồn). Dữ liệu domain của
   `Source_Tree` SHALL tiếp tục được backup riêng theo từng `Source_Tree`.

3. Restore `Composite_Tree` SHALL:
   - Khôi phục cấu hình.
   - Kiểm tra lại quyền, sự tồn tại của nguồn và tính hợp lệ của `Resolved_Graph`.
   - **Không** tự động publish cho đến khi validation pass.

4. IF một hoặc nhiều nguồn tạm thời lỗi khi resolve, THE Application SHALL trả
   về `ResolvedTreeData` partial với `sourceManifest` đánh dấu nguồn lỗi là
   `UNAVAILABLE` và `warnings`. **Không** trình bày cache cũ như dữ liệu mới.

5. THE Application SHALL hỗ trợ undo `Identity_Group` và `Cross_Tree_Relationship`
   thông qua audit history (xem lại lịch sử, khôi phục bằng cách xóa/sửa config).

---

### Requirement 12: Tương thích Ngược, Hiệu năng và Lưu trữ

**User Story:** Là đội phát triển, tôi muốn bổ sung composite mà không phá vỡ
spec gốc để triển khai an toàn trên kiến trúc Vercel Blob hiện tại.

#### Acceptance Criteria

1. FOR ALL `Standalone_Tree` hiện có, mọi API endpoint và service SHALL trả kết
   quả **tương đương** trước và sau khi thêm composite support.

2. THE Application SHALL lưu `CompositeTreeConfig` tại blob riêng
   `data/trees/{treeId}/composite-config.json`. **Không** thêm bản sao
   `Member`, `Relationship`, `Event` hay `MediaMetadata` vào thư mục composite.

3. `CompositeResolver` SHALL đọc các `Source_Tree` song song với giới hạn
   concurrency cấu hình được. **Không** thực hiện N+1 read theo từng member.

4. FOR `Composite_Tree` có tổng cộng < 1.000 `Virtual_Member` và ≤ 20 nguồn,
   THE Application SHALL resolve và trả metadata chính trong vòng **3 giây**
   trong điều kiện vận hành bình thường.

5. `Tree_Viewer` SHALL tiếp tục lazy render khi `Resolved_Graph` có hơn 100 node.

6. THE Application SHALL giới hạn MVP ở tối đa **20** `Composite_Source` và từ
   chối cấu hình vượt giới hạn với error code `SOURCE_LIMIT_EXCEEDED`.

7. `CompositeTreeConfig` SHALL dùng optimistic concurrency qua trường `revision`.
   Mọi mutation phải gửi `revision` hiện tại; mutation với `revision` cũ SHALL
   bị từ chối với error code `STALE_CONFIG_REVISION` thay vì last-write-wins.

---

## Ngoài phạm vi MVP

Các tính năng sau được đặc tả trong
`../composite-family-trees-advanced/requirements.md` và **không được triển khai**
trong MVP:

- Tách một nhánh thành `Standalone_Tree` mới (Branch Extraction).
- Sao chép hoặc materialize `Composite_Tree` thành `Standalone_Tree`.
- Chỉnh sửa `Member` trực tiếp qua `Composite_Tree`.
- Đồng bộ dữ liệu hai chiều giữa các `Source_Tree`.
- Tự động xác nhận hai hồ sơ là cùng một người.
- `Composite_Tree` làm `Source_Tree` của `Composite_Tree` khác (nested).
- Workflow duyệt nhiều người và giải quyết conflict theo field.
