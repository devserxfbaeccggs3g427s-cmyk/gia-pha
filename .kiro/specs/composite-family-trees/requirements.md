# Requirements Document: Composite Family Trees (MVP)

## Status

**READY FOR IMPLEMENTATION**

Spec này là phần mở rộng tương thích ngược của
`../family-genealogy-management`. Mọi hành vi hiện có của gia phả độc lập
phải tiếp tục hoạt động. Spec nâng cao và chưa triển khai nằm tại
`../composite-family-trees-advanced`.

## Introduction

Composite Family Trees cho phép tạo một gia phả tổng hợp từ nhiều gia phả độc
lập mà không sao chép hoặc nhập lại dữ liệu thành viên. Mỗi gia phả nguồn vẫn
là nơi sở hữu và chỉnh sửa dữ liệu gốc. Gia phả tổng hợp là một live projection
chỉ đọc, hợp nhất các node và cạnh từ những nguồn được cấp quyền, đồng thời cho
phép người dùng xác nhận các bản ghi ở nhiều cây là cùng một người và tạo các
quan hệ nối giữa các cây.

MVP ưu tiên tính toàn vẹn dữ liệu, quyền riêng tư và khả năng quay lui. MVP
không thực hiện tách nhánh, đồng bộ hai chiều, chỉnh sửa Member trực tiếp trong
composite hoặc composite lồng nhau.

## Glossary

- **Standalone_Tree**: Family_Tree độc lập đang được hỗ trợ bởi spec gốc
- **Composite_Tree**: Family_Tree tổng hợp dữ liệu từ nhiều Standalone_Tree
- **Source_Tree**: Standalone_Tree được đưa vào Composite_Tree
- **Composite_Source**: Cấu hình phạm vi và chính sách đọc một Source_Tree
- **Source_Reference**: Cặp định danh `(treeId, memberId)` trỏ đến Member gốc
- **Identity_Group**: Nhóm Source_Reference đã được xác nhận là cùng một người
- **Virtual_Member**: Node được tạo khi resolve Composite_Tree; không phải bản
  sao Member được lưu trữ
- **Cross_Tree_Relationship**: Relationship chỉ tồn tại trong cấu hình
  Composite_Tree và nối hai Source_Reference thuộc hai Source_Tree
- **Resolved_Graph**: Đồ thị phẳng được dựng từ cấu hình composite và dữ liệu
  nguồn tại thời điểm đọc
- **Provenance**: Thông tin cho biết Virtual_Member hoặc Relationship đến từ
  cây và bản ghi nguồn nào
- **Source_Scope**: Phạm vi dữ liệu được lấy từ Source_Tree
- **Boundary_Anchor**: Member dùng làm điểm bắt đầu khi lấy hậu duệ của một
  Source_Tree
- **Unavailable_Source**: Source_Tree đã bị xóa, mất quyền hoặc tạm thời không
  đọc được

## Requirements

### Requirement 1: Phân loại và tạo Gia phả Tổng hợp

**User Story:** Là một Admin, tôi muốn tạo gia phả tổng hợp tách biệt với gia
phả độc lập, để xem nhiều nhánh gia đình trong một cây mà không thay đổi dữ
liệu nguồn.

#### Acceptance Criteria

1. THE Application SHALL hỗ trợ `FamilyTree.kind` với hai giá trị `STANDALONE`
   và `COMPOSITE`; dữ liệu cũ không có `kind` SHALL được hiểu là `STANDALONE`
2. WHEN một User tạo Composite_Tree, THE Application SHALL tạo FamilyTree và
   Composite_Config rỗng mà không tạo `members.json` hoặc sao chép Member từ
   bất kỳ cây nào
3. THE Application SHALL không cho phép một Composite_Tree làm Source_Tree của
   Composite_Tree khác trong MVP
4. THE Application SHALL không cho phép Composite_Tree tham chiếu chính nó,
   trực tiếp hoặc gián tiếp
5. WHEN liệt kê gia phả, THE Application SHALL phân biệt rõ gia phả độc lập và
   gia phả tổng hợp

### Requirement 2: Quản lý Nguồn và Phạm vi Dữ liệu

**User Story:** Là một Admin của Composite_Tree, tôi muốn chọn cây nguồn và
phạm vi của từng cây, để chỉ đưa đúng nhánh cần thiết vào gia phả tổng quan.

#### Acceptance Criteria

1. WHEN Admin thêm Source_Tree, THE Application SHALL xác nhận Source_Tree tồn
   tại, có kind `STANDALONE`, và Admin có quyền `READ` trên cây đó
2. THE Application SHALL hỗ trợ ba Source_Scope: `FULL_TREE`, `DESCENDANTS` và
   `SELECTED_MEMBERS`
3. IF Source_Scope là `DESCENDANTS`, THEN THE Application SHALL yêu cầu ít nhất
   một Boundary_Anchor hợp lệ và lấy mọi hậu duệ qua cạnh cha/mẹ→con chuẩn
4. WHEN `includeSpouses` được bật, THE Application SHALL thêm vợ/chồng trực
   tiếp của Member trong scope làm ngữ cảnh cùng đời nhưng SHALL NOT tự động lấy
   tổ tiên hoặc hậu duệ riêng của người vợ/chồng nếu họ chưa thuộc scope
5. IF Source_Scope là `SELECTED_MEMBERS`, THEN THE Application SHALL chỉ lấy
   Member đã chọn và Relationship có cả hai đầu mút nằm trong tập đã chọn
6. WHEN Admin sửa scope hoặc xóa Composite_Source, THE Application SHALL chỉ
   cập nhật cấu hình composite và SHALL NOT sửa hoặc xóa dữ liệu Source_Tree
7. THE Application SHALL cung cấp preview số Member, Relationship, Event và
   Media sẽ được đưa vào trước khi xác nhận thêm nguồn

### Requirement 3: Xác nhận Cùng một Người giữa Nhiều Cây

**User Story:** Là một Admin, tôi muốn xác nhận các hồ sơ ở nhiều cây là cùng
một người, để người đó chỉ xuất hiện một lần trong gia phả tổng hợp.

#### Acceptance Criteria

1. THE Application SHALL có thể gợi ý cặp Source_Reference tương đồng dựa trên
   họ tên, ngày sinh, nơi sinh và quan hệ gia đình
2. THE Application SHALL NOT tự động đưa hai Source_Reference vào cùng một
   Identity_Group nếu chưa có xác nhận rõ ràng của Admin
3. WHEN Admin xác nhận cùng người, THE Application SHALL tạo hoặc cập nhật một
   Identity_Group và SHALL NOT merge, sửa hoặc xóa Member nguồn
4. THE Application SHALL hỗ trợ trạng thái gợi ý `PROPOSED`, `CONFIRMED` và
   `REJECTED`; chỉ `CONFIRMED` SHALL ảnh hưởng Resolved_Graph
5. A Source_Reference SHALL thuộc tối đa một Identity_Group đã xác nhận trong
   cùng Composite_Tree
6. WHEN Admin gỡ xác nhận, THE Application SHALL khôi phục các Virtual_Member
   riêng biệt trong lần resolve tiếp theo mà không làm mất dữ liệu nguồn
7. WHEN dữ liệu của các Source_Reference đã xác nhận mâu thuẫn, THE Application
   SHALL dùng nguồn hiển thị chính do Admin chọn và hiển thị cảnh báo xung đột
   cùng Provenance; MVP SHALL NOT ghi đè dữ liệu giữa các nguồn

### Requirement 4: Quan hệ Nối giữa các Cây

**User Story:** Là một Admin, tôi muốn tạo quan hệ giữa thành viên của các cây
nguồn, để các nhánh riêng tạo thành một đồ thị gia phả hoàn chỉnh.

#### Acceptance Criteria

1. THE Application SHALL cho phép tạo Cross_Tree_Relationship giữa hai
   Source_Reference đang thuộc các Composite_Source khác nhau
2. Cross_Tree_Relationship SHALL hỗ trợ cùng loại và metadata quan hệ chuẩn của
   spec gốc
3. WHEN tạo Cross_Tree_Relationship, THE Application SHALL xác nhận hai đầu mút
   tồn tại, nằm trong scope hiện hành và User có quyền quản trị Composite_Tree
4. THE Application SHALL chuẩn hóa mỗi quan hệ nghiệp vụ thành một cạnh chuẩn
   và loại bỏ cạnh trùng sau khi áp dụng Identity_Group
5. IF quan hệ cha/mẹ→con mới tạo chu trình trong toàn Resolved_Graph, THEN THE
   Application SHALL từ chối và trả về đường dẫn chu trình có liên quan
6. WHEN xóa Cross_Tree_Relationship, THE Application SHALL chỉ xóa cấu hình nối
   và SHALL NOT xóa Relationship trong bất kỳ Source_Tree nào

### Requirement 5: Phân giải Đồ thị Tổng hợp

**User Story:** Là một User, tôi muốn xem một đồ thị thống nhất và nhất quán,
để không cần biết dữ liệu đang nằm trong cây nguồn nào.

#### Acceptance Criteria

1. WHEN đọc Composite_Tree, THE Composite_Resolver SHALL đọc cấu hình và các
   Source_Tree được phép truy cập, áp dụng scope, Identity_Group và
   Cross_Tree_Relationship để tạo Resolved_Graph
2. THE Composite_Resolver SHALL tạo Virtual_Member ID xác định và ổn định từ
   composite ID cùng identity ID hoặc Source_Reference; ID SHALL không xung đột
   giữa các cây
3. WHEN nhiều Relationship nguồn trở thành cùng một cạnh sau khi hợp nhất định
   danh, THE Composite_Resolver SHALL trả về đúng một cạnh và giữ toàn bộ
   Provenance
4. THE Composite_Resolver SHALL không tạo self-edge khi hai đầu mút của một
   Relationship được ánh xạ về cùng Virtual_Member
5. THE Composite_Resolver SHALL chạy kiểm tra chu trình cha/mẹ→con trên đồ thị
   sau hợp nhất và SHALL không công bố cấu hình không hợp lệ
6. THE Application SHALL tính generation và ancestry trên Resolved_Graph theo
   đúng quy tắc quan hệ chuẩn của Requirements 3 và 4 trong spec gốc
7. FOR ALL cấu hình hợp lệ và cùng một phiên bản dữ liệu nguồn, nhiều lần
   resolve SHALL trả về đồ thị tương đương và ID ổn định

### Requirement 6: Quyền Chỉnh sửa và Điều hướng về Nguồn

**User Story:** Là một Editor, tôi muốn biết phải chỉnh sửa dữ liệu ở đâu, để
tránh tạo nhiều nguồn sự thật cho cùng một người.

#### Acceptance Criteria

1. THE Application SHALL coi Member, Relationship, Event và Media được resolve
   từ Source_Tree là dữ liệu chỉ đọc trong Composite_Tree
2. IF User cố tạo, sửa hoặc xóa dữ liệu nguồn qua API của Composite_Tree, THEN
   THE Application SHALL trả lỗi `COMPOSITE_READ_ONLY`
3. WHEN User xem Virtual_Member, THE Application SHALL hiển thị Provenance và
   liên kết mở hồ sơ trong từng Source_Tree
4. WHEN Virtual_Member có nhiều Source_Reference, THE Application SHALL hiển
   thị nguồn chính và danh sách các nguồn còn lại
5. Composite Admin SHALL được phép sửa cấu hình nguồn, Identity_Group và
   Cross_Tree_Relationship mà không cần quyền ghi trên Source_Tree

### Requirement 7: Phân quyền và Quyền riêng tư Xuyên Cây

**User Story:** Là chủ sở hữu cây nguồn, tôi muốn composite không làm lộ dữ
liệu cho người không được phép, để quyền riêng tư của từng nhánh được giữ
nguyên.

#### Acceptance Criteria

1. THE Application SHALL NOT suy ra quyền đọc Source_Tree từ membership của
   Composite_Tree
2. WHEN một User xem Composite_Tree, THE Application SHALL chỉ resolve đầy đủ
   những Source_Tree mà User có quyền `READ`
3. IF User không có quyền trên một Source_Tree, THEN THE Application SHALL ẩn
   dữ liệu nguồn đó và hiển thị placeholder tổng quát nếu cần giữ tính liên tục
   của đồ thị; placeholder SHALL không chứa thông tin cá nhân
4. WHEN quyền Source_Tree bị thu hồi hoặc Source_Tree bị xóa, THE Application
   SHALL đánh dấu nguồn `UNAVAILABLE` trong lần đọc tiếp theo mà không sử dụng
   cache để vượt qua quyền hiện tại
5. WHEN chia sẻ Composite_Tree bằng share link, THE Application SHALL chỉ đưa
   Source_Tree đã được chủ nguồn cho phép dùng trong composite sharing
6. Share link SHALL ẩn mặc định phone, email, currentAddress, notes và dữ liệu
   riêng tư của người còn sống; việc mở thêm trường SHALL cần consent rõ ràng
   của từng Source_Tree
7. THE Application SHALL ghi audit log cho mọi thay đổi cấu hình composite,
   gồm actor, thời gian, dữ liệu trước/sau và Source_Reference liên quan

### Requirement 8: Trực quan hóa và Trải nghiệm Người dùng

**User Story:** Là một Viewer, tôi muốn Composite_Tree có trải nghiệm xem giống
gia phả thường nhưng thể hiện rõ nguồn và trạng thái liên kết.

#### Acceptance Criteria

1. THE Tree_Viewer SHALL hiển thị Resolved_Graph bằng các chế độ được hỗ trợ
   cho Standalone_Tree trong spec gốc
2. THE Tree_Viewer SHALL hỗ trợ highlight, zoom, pan, minimap, lazy loading và
   chế độ dòng dõi trên Resolved_Graph
3. WHEN hiển thị Virtual_Member, THE Tree_Viewer SHALL có thể hiển thị badge
   nguồn mà không làm thay đổi quy tắc màu giới tính, đời và trạng thái
4. THE Application SHALL cung cấp wizard theo thứ tự: chọn nguồn, chọn scope,
   xem preview, xử lý gợi ý trùng, kiểm tra tính hợp lệ và công bố
5. IF Composite_Tree có gợi ý trùng chưa xử lý, nguồn không khả dụng hoặc xung
   đột dữ liệu, THEN THE Application SHALL hiển thị trạng thái cần xem xét
6. THE UI SHALL hỗ trợ tiếng Việt và tiếng Anh, responsive và WCAG 2.1 AA như
   spec gốc

### Requirement 9: Search, Report, Event, Media và Export

**User Story:** Là một User, tôi muốn các tính năng đọc hiện có hoạt động trên
gia phả tổng hợp, để composite thực sự là một overview đầy đủ.

#### Acceptance Criteria

1. THE Search_Engine SHALL tìm và lọc Virtual_Member trên Resolved_Graph và trả
   Provenance cùng kết quả
2. THE Report_Generator SHALL thống kê mỗi Virtual_Member đúng một lần sau khi
   áp dụng Identity_Group
3. Event và Media từ Source_Tree SHALL được ánh xạ đến Virtual_Member tương ứng
   và tuân theo quyền đọc của nguồn
4. WHEN xuất Composite_Tree ra PDF, PNG, SVG hoặc GEDCOM, THE Export_Service
   SHALL xuất snapshot phẳng của Resolved_Graph tại thời điểm yêu cầu
5. WHEN xuất `COMPOSITE_JSON`, THE Export_Service SHALL lưu cấu hình liên kết,
   Provenance, version/hash nguồn và SHALL NOT nhúng dữ liệu nguồn riêng tư nếu
   không có quyền phù hợp
6. Import `COMPOSITE_JSON` SHALL khôi phục cấu hình tham chiếu; nguồn không tồn
   tại hoặc không được cấp quyền SHALL được đánh dấu `UNAVAILABLE`, không được
   tự động sao chép dữ liệu snapshot thành dữ liệu nguồn

### Requirement 10: Cập nhật, Cache và Offline

**User Story:** Là một User, tôi muốn overview phản ánh thay đổi từ các nhánh
và vẫn xem được khi mất mạng, để dữ liệu tổng quan hữu ích nhưng không gây hiểu
nhầm về độ mới.

#### Acceptance Criteria

1. WHEN Source_Tree thay đổi, THE Application SHALL làm mất hiệu lực cache liên
   quan hoặc phát hiện source version/hash khác trong lần refresh tiếp theo
2. THE Application SHALL hiển thị `resolvedAt`, version của từng nguồn và trạng
   thái fresh/stale của Resolved_Graph
3. WHILE offline, THE Application SHALL cho phép xem cache gần nhất nếu phiên
   hiện tại đã từng có quyền truy cập và SHALL hiển thị rõ dữ liệu có thể cũ
4. THE Application SHALL không cho phép thay đổi cấu hình composite khi offline
   trong MVP
5. WHEN trở lại online, THE Application SHALL xác thực lại quyền trước khi làm
   mới hoặc tiếp tục hiển thị dữ liệu nguồn đã cache

### Requirement 11: Vòng đời, Backup và Khả năng Phục hồi

**User Story:** Là một Admin, tôi muốn tháo liên kết và khôi phục cấu hình an
toàn, để không vô tình làm hỏng dữ liệu các nhánh.

#### Acceptance Criteria

1. WHEN xóa Composite_Tree, THE Application SHALL xóa cấu hình, cache và share
   link của composite nhưng SHALL NOT xóa bất kỳ Source_Tree hoặc media nguồn
2. Backup Composite_Tree SHALL lưu FamilyTree metadata, Composite_Config,
   audit log và manifest version/hash nguồn; dữ liệu nguồn SHALL tiếp tục được
   backup theo Source_Tree của nó
3. Restore Composite_Tree SHALL khôi phục cấu hình và kiểm tra lại quyền, sự
   tồn tại của nguồn và tính hợp lệ trước khi công bố
4. IF một hoặc nhiều nguồn tạm thời lỗi, THEN THE Application SHALL trả kết quả
   partial có cảnh báo thay vì trình bày cache cũ như dữ liệu mới
5. THE Application SHALL hỗ trợ thao tác gỡ Identity_Group và
   Cross_Tree_Relationship có thể quay lui thông qua audit history

### Requirement 12: Tương thích Ngược, Hiệu năng và Lưu trữ

**User Story:** Là đội phát triển, tôi muốn bổ sung composite mà không phá vỡ
spec gốc, để triển khai an toàn trên kiến trúc Vercel Blob hiện tại.

#### Acceptance Criteria

1. FOR ALL Standalone_Tree hiện có, API và service SHALL trả kết quả tương
   đương trước và sau khi thêm Composite_Tree support
2. THE Application SHALL lưu Composite_Config ở blob riêng theo composite ID
   và không thêm bản sao Member, Relationship, Event hoặc Media vào thư mục dữ
   liệu composite
3. THE Composite_Resolver SHALL đọc các Source_Tree song song với giới hạn
   concurrency cấu hình được và SHALL không thực hiện N+1 read theo Member
4. FOR Composite_Tree có tổng cộng dưới 1.000 Virtual_Member và tối đa 20 nguồn,
   THE Application SHALL resolve và trả metadata chính trong vòng 3 giây ở
   điều kiện vận hành bình thường
5. THE Tree_Viewer SHALL tiếp tục lazy render khi Resolved_Graph có hơn 100 node
6. THE Application SHALL giới hạn MVP ở tối đa 20 Composite_Source và từ chối
   cấu hình vượt giới hạn với lỗi có thể xử lý
7. THE Application SHALL dùng optimistic concurrency cho Composite_Config bằng
   revision hoặc ETag; update với revision cũ SHALL bị từ chối thay vì
   last-write-wins âm thầm

## Out of Scope for MVP

- Tách một nhánh từ cây hiện tại thành cây mới
- Sao chép hoặc materialize Composite_Tree thành Standalone_Tree
- Chỉnh sửa Member trực tiếp từ Composite_Tree
- Đồng bộ dữ liệu hai chiều hoặc tự động ghi đè giữa các Source_Tree
- Tự động xác nhận hai hồ sơ là cùng một người
- Composite_Tree làm nguồn của Composite_Tree khác
- Workflow duyệt nhiều người và giải quyết conflict theo từng field

Các nội dung trên được đặc tả trong `../composite-family-trees-advanced`.
