# Requirements Document

## Introduction

Ứng dụng quản lý gia phả (Family Genealogy Management) là một hệ thống web được xây dựng trên nền tảng Next.js và triển khai trên Vercel, cho phép người dùng tạo, quản lý và chia sẻ cây gia phả một cách chuyên nghiệp. Ứng dụng sử dụng Vercel Blob làm giải pháp lưu trữ dữ liệu (không sử dụng database truyền thống), lưu trữ dữ liệu ứng dụng dưới dạng JSON files và media files trên cloud storage. Ứng dụng hỗ trợ đa thiết bị (responsive), đa ngôn ngữ, và cung cấp các tính năng trực quan hóa, quản lý thành viên, sự kiện, tài liệu gia đình với giao diện hiện đại.

## Glossary

- **Application**: Ứng dụng quản lý gia phả Next.js
- **Family_Tree**: Cấu trúc dữ liệu cây gia phả biểu diễn mối quan hệ giữa các thành viên
- **Member**: Một cá nhân trong gia phả, có thông tin cá nhân và mối quan hệ với các thành viên khác
- **Relationship**: Mối quan hệ giữa hai Member (cha-con, vợ-chồng, anh-chị-em)
- **Tree_Viewer**: Component hiển thị trực quan cây gia phả
- **Auth_System**: Hệ thống xác thực và phân quyền người dùng
- **Media_Manager**: Module quản lý tệp đa phương tiện (ảnh, tài liệu)
- **Event**: Sự kiện hoặc cột mốc quan trọng của gia đình hoặc thành viên
- **Search_Engine**: Module tìm kiếm và lọc thành viên trong gia phả
- **Export_Service**: Module xuất dữ liệu gia phả ra các định dạng khác nhau
- **Import_Service**: Module nhập dữ liệu gia phả từ các nguồn bên ngoài
- **Report_Generator**: Module tạo báo cáo và thống kê gia phả
- **Layout_System**: Hệ thống bố cục responsive hỗ trợ đa thiết bị
- **I18n_Service**: Module đa ngôn ngữ (internationalization)
- **User**: Người dùng đã xác thực sử dụng Application
- **Admin**: Người dùng có quyền quản trị cao nhất trong một gia phả
- **GEDCOM**: Định dạng chuẩn quốc tế để trao đổi dữ liệu phả hệ
- **Blob_Storage**: Vercel Blob storage service dùng để lưu trữ dữ liệu ứng dụng dưới dạng JSON files và media files trên cloud

## Requirements

### Requirement 1: Xác thực và Phân quyền

**User Story:** Là một người dùng, tôi muốn đăng ký, đăng nhập và quản lý quyền truy cập gia phả, để bảo mật thông tin gia đình.

#### Acceptance Criteria

1. WHEN một người dùng cung cấp email và mật khẩu hợp lệ, THE Auth_System SHALL tạo tài khoản mới và gửi email xác nhận trong vòng 5 giây
2. WHEN một người dùng cung cấp thông tin đăng nhập hợp lệ, THE Auth_System SHALL xác thực và cấp access token trong vòng 2 giây
3. WHEN một người dùng chọn đăng nhập qua mạng xã hội (Google, Facebook), THE Auth_System SHALL xác thực thông qua OAuth 2.0 và tạo phiên làm việc
4. IF thông tin đăng nhập không hợp lệ sau 5 lần thử liên tiếp, THEN THE Auth_System SHALL khóa tài khoản trong 15 phút và thông báo cho người dùng
5. THE Auth_System SHALL hỗ trợ 3 vai trò: Admin (toàn quyền), Editor (chỉnh sửa), Viewer (chỉ xem)
6. WHEN một Admin gán vai trò cho User, THE Auth_System SHALL cập nhật quyền truy cập ngay lập tức mà không cần đăng nhập lại

### Requirement 2: Quản lý Thành viên Gia phả

**User Story:** Là một người dùng, tôi muốn thêm, sửa, xóa và xem thông tin chi tiết thành viên gia phả, để duy trì hồ sơ gia đình đầy đủ và chính xác.

#### Acceptance Criteria

1. WHEN một User tạo Member mới, THE Application SHALL lưu thông tin bao gồm: họ tên, ngày sinh, giới tính, nơi sinh, nghề nghiệp, tiểu sử, và ảnh đại diện
2. WHEN một User cập nhật thông tin Member, THE Application SHALL lưu lịch sử thay đổi và hiển thị thông tin mới trong vòng 1 giây
3. WHEN một User xóa Member, THE Application SHALL hiển thị cảnh báo xác nhận và liệt kê các Relationship bị ảnh hưởng trước khi xóa
4. THE Application SHALL hỗ trợ lưu trữ thông tin mở rộng cho Member: địa chỉ hiện tại, số điện thoại, email, học vấn, thành tựu, và ghi chú
5. IF Member có ngày mất, THEN THE Application SHALL hiển thị trạng thái đã mất và tính toán thọ của Member
6. WHEN một User tìm kiếm Member trùng lặp, THE Application SHALL gợi ý các Member có thông tin tương tự để hợp nhất

### Requirement 3: Quản lý Mối quan hệ

**User Story:** Là một người dùng, tôi muốn thiết lập và quản lý mối quan hệ giữa các thành viên, để cây gia phả phản ánh đúng cấu trúc gia đình.

#### Acceptance Criteria

1. THE Application SHALL hỗ trợ các loại Relationship: cha-con, mẹ-con, vợ-chồng, anh-chị-em, con nuôi, và quan hệ tùy chỉnh
2. WHEN một User tạo Relationship giữa hai Member, THE Application SHALL chỉ lưu một Relationship chuẩn cho một quan hệ nghiệp vụ và SHALL suy ra cách nhìn ngược khi đọc dữ liệu hoặc hiển thị (ví dụ bản ghi cha→con được hiển thị từ phía người con là con→cha mà không lưu thêm một cạnh PARENT_CHILD đảo chiều)
3. IF một User tạo Relationship mâu thuẫn logic (người là cha của chính mình, target đã là tổ tiên của source, hoặc quan hệ mới làm đồ thị cha/mẹ→con có chu trình), THEN THE Application SHALL từ chối và hiển thị thông báo lỗi cụ thể; nhiều cha/mẹ cùng trỏ tới một người con SHALL được chấp nhận khi không tạo chu trình
4. WHEN một Relationship vợ-chồng được tạo, THE Application SHALL cho phép ghi nhận ngày kết hôn, ngày ly hôn (nếu có), và trạng thái hôn nhân
5. THE Application SHALL tính toán và hiển thị đời (generation) theo đồ thị quan hệ chuẩn: các Member nối với nhau bằng quan hệ vợ-chồng SHALL thuộc cùng một nhóm đời; nhóm không nhận cạnh cha/mẹ→con là đời gốc; nhóm con SHALL có đời bằng nhóm cha/mẹ cộng một
6. WHEN một User xóa Relationship, THE Application SHALL xóa bản ghi quan hệ chuẩn, cập nhật Family_Tree và loại bỏ các cách nhìn hoặc Relationship phái sinh liên quan
7. THE Application SHALL suy ra các quan hệ thông gia như con dâu/con rể từ quan hệ vợ-chồng và cha/mẹ-con; các quan hệ thông gia SHALL NOT được lưu dưới dạng PARENT_CHILD và SHALL NOT tham gia đường tổ tiên
8. IF một Member không khai báo cha/mẹ nhưng có vợ/chồng đã xác định được đời, THEN THE Application SHALL gán Member đó cùng đời với vợ/chồng thay vì coi Member đó là một tổ tiên gốc độc lập

### Requirement 4: Trực quan hóa Cây Gia phả

**User Story:** Là một người dùng, tôi muốn xem cây gia phả dưới nhiều dạng trực quan, để dễ dàng hiểu và trình bày cấu trúc gia đình.

#### Acceptance Criteria

1. THE Tree_Viewer SHALL hiển thị Family_Tree ở 3 chế độ: dạng cây dọc, dạng cây ngang, và dạng vòng tròn (fan chart)
2. WHEN một User chọn Member trên Tree_Viewer, THE Tree_Viewer SHALL highlight Member đó và hiển thị thông tin tóm tắt
3. THE Tree_Viewer SHALL hỗ trợ zoom in/out, kéo thả (pan), và điều hướng bằng minimap
4. WHEN Family_Tree có hơn 100 Member, THE Tree_Viewer SHALL áp dụng lazy loading và chỉ render các node trong viewport
5. THE Tree_Viewer SHALL sử dụng màu sắc phân biệt giới tính, đời, và trạng thái (còn sống/đã mất) của Member
6. WHEN một User chọn chế độ xem "dòng dõi" cho một Member, THE Tree_Viewer SHALL hiển thị đường từ tổ tiên gốc đến Member đó

### Requirement 5: Responsive Design và Đa thiết bị

**User Story:** Là một người dùng, tôi muốn sử dụng ứng dụng trên điện thoại, máy tính bảng và máy tính để bàn, để truy cập gia phả mọi lúc mọi nơi.

#### Acceptance Criteria

1. THE Layout_System SHALL hiển thị giao diện tối ưu cho 3 breakpoint: mobile (< 768px), tablet (768px - 1024px), và desktop (> 1024px)
2. WHILE User sử dụng thiết bị mobile, THE Tree_Viewer SHALL chuyển sang chế độ điều hướng bằng cử chỉ (pinch-to-zoom, swipe)
3. THE Application SHALL đạt điểm Lighthouse Performance tối thiểu 90 trên cả mobile và desktop
4. WHEN User xoay thiết bị (portrait/landscape), THE Layout_System SHALL điều chỉnh bố cục trong vòng 300ms mà không mất trạng thái hiện tại
5. THE Application SHALL hỗ trợ Progressive Web App (PWA) cho phép cài đặt trên thiết bị và truy cập offline với dữ liệu đã cache
6. WHILE User không có kết nối mạng, THE Application SHALL hiển thị dữ liệu đã cache và đồng bộ thay đổi khi có kết nối trở lại

### Requirement 6: Tìm kiếm và Lọc

**User Story:** Là một người dùng, tôi muốn tìm kiếm nhanh thành viên và lọc theo nhiều tiêu chí, để dễ dàng tìm thông tin trong gia phả lớn.

#### Acceptance Criteria

1. WHEN một User nhập từ khóa, THE Search_Engine SHALL trả về kết quả trong vòng 500ms, hỗ trợ tìm theo tên, biệt danh, nghề nghiệp, và nơi sinh
2. THE Search_Engine SHALL hỗ trợ tìm kiếm tiếng Việt có dấu và không dấu (fuzzy matching)
3. WHEN một User áp dụng bộ lọc, THE Search_Engine SHALL lọc Member theo: giới tính, đời, khoảng năm sinh, trạng thái, và địa phương
4. THE Search_Engine SHALL hiển thị gợi ý tự động (autocomplete) khi User nhập từ 2 ký tự trở lên
5. WHEN kết quả tìm kiếm được chọn, THE Application SHALL điều hướng đến Member trên Tree_Viewer và highlight vị trí

### Requirement 7: Quản lý Media

**User Story:** Là một người dùng, tôi muốn tải lên và quản lý ảnh, tài liệu gia đình, để lưu giữ kỷ niệm và tư liệu quý.

#### Acceptance Criteria

1. THE Media_Manager SHALL hỗ trợ tải lên ảnh (JPEG, PNG, WebP), tài liệu (PDF), với kích thước tối đa 10MB mỗi tệp
2. WHEN một User tải ảnh lên, THE Media_Manager SHALL lưu trữ file gốc trên Blob_Storage và tự động tạo thumbnail tối ưu cho hiển thị web
3. THE Media_Manager SHALL cho phép gắn Media với một hoặc nhiều Member và Event
4. WHEN một User xem gallery của Member, THE Media_Manager SHALL hiển thị ảnh dạng lưới với lightbox xem chi tiết
5. THE Media_Manager SHALL hỗ trợ tạo album ảnh theo chủ đề và sắp xếp theo thời gian
6. IF tệp tải lên vượt quá giới hạn kích thước hoặc không đúng định dạng, THEN THE Media_Manager SHALL từ chối và hiển thị thông báo lỗi rõ ràng
7. THE Media_Manager SHALL lưu trữ media files (ảnh, PDF) trực tiếp trên Blob_Storage và lưu metadata (tên file, kích thước, liên kết Member/Event) trong JSON data blob riêng biệt

### Requirement 8: Sự kiện và Cột mốc

**User Story:** Là một người dùng, tôi muốn ghi nhận các sự kiện quan trọng của gia đình, để lưu giữ lịch sử và kỷ niệm.

#### Acceptance Criteria

1. THE Application SHALL hỗ trợ tạo Event với các loại: sinh nhật, đám cưới, tang lễ, họp mặt, lễ giỗ, và sự kiện tùy chỉnh
2. WHEN một User tạo Event, THE Application SHALL lưu thông tin: tên sự kiện, ngày, địa điểm, mô tả, và danh sách Member tham gia
3. THE Application SHALL hiển thị timeline sự kiện của gia đình theo trục thời gian tương tác
4. WHEN ngày sinh nhật hoặc ngày giỗ của Member đến gần (trong 7 ngày), THE Application SHALL hiển thị nhắc nhở trên dashboard
5. THE Application SHALL cho phép gắn Media (ảnh, tài liệu) vào Event
6. WHEN một User xem Event, THE Application SHALL hiển thị danh sách Member liên quan với link điều hướng đến profile

### Requirement 9: Import và Export Dữ liệu

**User Story:** Là một người dùng, tôi muốn nhập dữ liệu từ các nguồn khác và xuất gia phả ra nhiều định dạng, để chia sẻ và sao lưu dữ liệu.

#### Acceptance Criteria

1. THE Import_Service SHALL hỗ trợ nhập dữ liệu từ định dạng GEDCOM 5.5, JSON, và CSV
2. WHEN một User import file GEDCOM, THE Import_Service SHALL parse file và hiển thị bản xem trước dữ liệu trước khi xác nhận nhập
3. THE Export_Service SHALL hỗ trợ xuất Family_Tree ra định dạng: GEDCOM, JSON, PDF, và hình ảnh (PNG/SVG)
4. WHEN một User xuất Family_Tree ra PDF, THE Export_Service SHALL tạo tài liệu có bố cục đẹp bao gồm cây gia phả, danh sách thành viên, và thống kê
5. FOR ALL đối tượng Family_Tree hợp lệ, THE Application SHALL đảm bảo export ra JSON rồi import lại tạo ra Family_Tree tương đương (round-trip property)
6. IF file import có lỗi cú pháp hoặc dữ liệu không hợp lệ, THEN THE Import_Service SHALL hiển thị báo cáo lỗi chi tiết theo từng dòng

### Requirement 10: Đa ngôn ngữ

**User Story:** Là một người dùng, tôi muốn sử dụng ứng dụng bằng tiếng Việt và các ngôn ngữ khác, để chia sẻ gia phả với người thân ở nước ngoài.

#### Acceptance Criteria

1. THE I18n_Service SHALL hỗ trợ tối thiểu 2 ngôn ngữ: Tiếng Việt (mặc định) và Tiếng Anh
2. WHEN một User chuyển ngôn ngữ, THE I18n_Service SHALL cập nhật toàn bộ giao diện trong vòng 500ms mà không cần tải lại trang
3. THE I18n_Service SHALL hỗ trợ định dạng ngày tháng, số, và tiền tệ theo locale của ngôn ngữ được chọn
4. THE Application SHALL lưu trữ dữ liệu Member (tên, tiểu sử) bằng ngôn ngữ gốc mà không bị thay đổi khi chuyển ngôn ngữ giao diện
5. THE I18n_Service SHALL hỗ trợ chiều văn bản RTL (right-to-left) cho khả năng mở rộng ngôn ngữ trong tương lai

### Requirement 11: Báo cáo và Thống kê

**User Story:** Là một người dùng, tôi muốn xem thống kê tổng quan về gia phả, để hiểu rõ quy mô và đặc điểm gia đình.

#### Acceptance Criteria

1. THE Report_Generator SHALL hiển thị dashboard thống kê bao gồm: tổng số thành viên, số đời, phân bố giới tính, phân bố độ tuổi, và phân bố địa lý
2. THE Report_Generator SHALL hiển thị biểu đồ phân bố thành viên theo nghề nghiệp và học vấn
3. WHEN một User yêu cầu báo cáo, THE Report_Generator SHALL tạo báo cáo trong vòng 3 giây cho gia phả có dưới 1000 Member
4. THE Report_Generator SHALL hỗ trợ xuất báo cáo ra PDF với biểu đồ và bảng số liệu
5. THE Report_Generator SHALL hiển thị timeline tăng trưởng thành viên theo thời gian
6. WHEN một User chọn xem thống kê cho một nhánh, THE Report_Generator SHALL tính toán và hiển thị thống kê riêng cho nhánh đó

### Requirement 12: In và Xuất Cây Gia phả

**User Story:** Là một người dùng, tôi muốn in cây gia phả hoặc xuất hình ảnh chất lượng cao, để treo tại nhà hoặc chia sẻ cho gia đình.

#### Acceptance Criteria

1. THE Export_Service SHALL hỗ trợ xuất Tree_Viewer ra hình ảnh PNG với độ phân giải tối thiểu 300 DPI
2. THE Export_Service SHALL hỗ trợ xuất Tree_Viewer ra SVG để in ở mọi kích thước mà không mất chất lượng
3. WHEN một User chọn in Family_Tree, THE Export_Service SHALL tạo bản xem trước (print preview) với tùy chọn khổ giấy (A4, A3, A2, A1)
4. THE Export_Service SHALL hỗ trợ tùy chỉnh kiểu hiển thị khi in: chọn thông tin hiển thị, màu sắc, font chữ, và hướng giấy
5. WHEN Family_Tree quá lớn cho một trang, THE Export_Service SHALL tự động chia thành nhiều trang với chỉ dẫn ghép nối

### Requirement 13: Giao diện Người dùng Hiện đại

**User Story:** Là một người dùng, tôi muốn giao diện đẹp, trực quan và dễ sử dụng, để có trải nghiệm tốt khi quản lý gia phả.

#### Acceptance Criteria

1. THE Application SHALL hỗ trợ 2 chế độ: Dark mode và Light mode, với khả năng tự động chuyển theo cài đặt hệ thống
2. THE Application SHALL sử dụng hệ thống thiết kế nhất quán với typography, spacing, và color palette thống nhất
3. THE Application SHALL hỗ trợ điều hướng bằng bàn phím và tuân thủ WCAG 2.1 Level AA cho accessibility
4. WHEN một thao tác yêu cầu thời gian xử lý trên 1 giây, THE Application SHALL hiển thị loading indicator hoặc skeleton screen
5. THE Application SHALL sử dụng animation và transition mượt mà (60fps) cho các tương tác UI
6. THE Application SHALL hiển thị breadcrumb navigation cho phép User biết vị trí hiện tại và điều hướng nhanh

### Requirement 14: Bảo mật Dữ liệu

**User Story:** Là một người dùng, tôi muốn dữ liệu gia phả được bảo mật, để thông tin gia đình không bị truy cập trái phép.

#### Acceptance Criteria

1. THE Application SHALL mã hóa dữ liệu truyền tải bằng HTTPS/TLS 1.3
2. THE Auth_System SHALL lưu trữ mật khẩu sử dụng thuật toán bcrypt với cost factor tối thiểu 12
3. WHEN một User chia sẻ Family_Tree, THE Application SHALL tạo link chia sẻ có thời hạn và quyền truy cập giới hạn (chỉ xem)
4. THE Application SHALL tự động sao lưu dữ liệu hàng ngày dưới dạng JSON snapshot lưu trữ trên Blob_Storage và cho phép khôi phục từ bản sao lưu trong 30 ngày gần nhất
5. WHEN phiên đăng nhập không hoạt động quá 30 phút, THE Auth_System SHALL tự động đăng xuất và yêu cầu xác thực lại
6. THE Application SHALL ghi log tất cả thao tác thay đổi dữ liệu (audit trail) bao gồm: User thực hiện, thời gian, và nội dung thay đổi

### Requirement 15: Hiệu năng và Tối ưu

**User Story:** Là một người dùng, tôi muốn ứng dụng tải nhanh và phản hồi mượt mà, để sử dụng hiệu quả mà không phải chờ đợi.

#### Acceptance Criteria

1. THE Application SHALL đạt Time to First Contentful Paint (FCP) dưới 1.5 giây trên kết nối 4G
2. THE Application SHALL sử dụng Server-Side Rendering (SSR) cho trang đầu tiên và Client-Side Rendering cho tương tác tiếp theo
3. THE Application SHALL áp dụng code splitting và lazy loading cho các module không cần thiết ở lần tải đầu
4. WHEN User điều hướng giữa các trang, THE Application SHALL sử dụng prefetching để tải trước dữ liệu trong vòng 200ms
5. THE Application SHALL tối ưu hình ảnh bằng next/image với format WebP và responsive srcset
6. THE Application SHALL cache dữ liệu tĩnh và API response phù hợp để giảm tải server

### Requirement 16: Lưu trữ Dữ liệu (Data Storage)

**User Story:** Là một người dùng, tôi muốn dữ liệu gia phả được lưu trữ an toàn trên cloud và truy cập được từ mọi thiết bị, để quản lý gia phả mà không cần cài đặt database riêng.

#### Acceptance Criteria

1. THE Application SHALL sử dụng Blob_Storage để lưu trữ toàn bộ dữ liệu ứng dụng dưới dạng JSON files, không sử dụng database truyền thống
2. THE Application SHALL tổ chức dữ liệu thành các JSON blob riêng biệt: members.json, relationships.json, events.json, và media-metadata.json
3. WHEN một User thực hiện thao tác đọc hoặc ghi dữ liệu, THE Application SHALL truy cập Blob_Storage thông qua API routes trên Vercel
4. THE Application SHALL tuân thủ giới hạn free tier của Vercel Blob: 500MB dung lượng lưu trữ, 1000 lượt ghi mỗi tháng, và 10000 lượt đọc mỗi tháng
5. WHEN một User thực hiện thay đổi dữ liệu, THE Application SHALL áp dụng optimistic update trên client trước và đồng bộ với Blob_Storage qua API route
6. IF Blob_Storage API trả về lỗi (network failure, rate limit, storage full), THEN THE Application SHALL hiển thị thông báo lỗi cụ thể cho User và giữ lại thay đổi trên client để retry
7. THE Application SHALL lưu trữ backup snapshot dưới dạng JSON file riêng trên Blob_Storage với tên chứa timestamp, cho phép rollback về phiên bản trước
8. WHEN nhiều thiết bị truy cập cùng dữ liệu, THE Application SHALL đảm bảo tất cả thiết bị đọc/ghi thông qua cùng API routes đến Blob_Storage để duy trì tính nhất quán dữ liệu
