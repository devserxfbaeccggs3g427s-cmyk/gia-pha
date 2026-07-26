# Requirements Document: Spring Boot Backend Migration

## Status

**READY FOR IMPLEMENTATION PLANNING**

Spec này định nghĩa việc thay thế backend Next.js App Router hiện tại bằng Spring Boot, đồng thời giữ Next.js làm frontend trong giai đoạn strangler migration. Thiết kế chi tiết nằm tại `design.md`; thứ tự triển khai và Definition of Done nằm tại `tasks.md`.

## Introduction

Hệ thống hiện tại lưu cả dữ liệu có cấu trúc và file nhị phân trong private Vercel Blob. Users, trees, memberships, members, relationships, events, albums, media metadata, share links, change logs và backups được ghi dưới dạng các JSON collection bị overwrite toàn bộ. Mô hình này tạo nguy cơ lost update, thiếu transaction xuyên collection, thiếu referential integrity, truy vấn phải scan toàn bộ dữ liệu và khó quan sát vận hành.

Mục tiêu là chuyển backend sang modular monolith dùng Spring Boot 4.1.0, Java 25 LTS và MySQL 8.4 LTS. MySQL trở thành nguồn sự thật duy nhất cho dữ liệu quan hệ và dữ liệu bảo mật. Private Vercel Blob chỉ giữ file nhị phân, thumbnail và generated artifact. Quá trình migration phải giữ dữ liệu, URL, hành vi nghiệp vụ và khả năng rollback.

## Glossary

- **Legacy_Backend**: Backend Next.js App Router hiện tại
- **Target_Backend**: Backend Spring Boot mới
- **Compatibility_API**: API giữ nguyên URL, request, response và status hiện tại
- **V2_API**: API version mới hỗ trợ contract nhất quán, optimistic concurrency và idempotency
- **Structured_Data**: User, tree, membership, member, relationship, event, album, media metadata, share link, audit và backup metadata
- **Binary_Object**: File gốc, thumbnail hoặc generated artifact
- **Blob_Control_Gateway**: Vercel Function nhỏ dùng official `@vercel/blob` SDK để phát hành exact-path Signed URLs và chạy control operations
- **Signed_Data_Plane**: Luồng truyền bytes trực tiếp giữa browser/Spring và Vercel Blob qua short-lived Signed URL
- **Upload_Intent**: Bản ghi durable mô tả upload, quarantine path, final path, constraint và trạng thái
- **Tree_Revision**: Revision tăng sau mỗi mutation, dùng cho ETag và cache invalidation
- **Single_Writer**: Quy tắc chỉ một backend được phép ghi một tree hoặc identity tại một thời điểm
- **Shadow_Read**: Gửi read tương đương sang Target_Backend để so sánh nhưng vẫn trả kết quả Legacy_Backend
- **Blocking_Discrepancy**: Sai lệch dữ liệu/contract ngăn cutover
- **Application_Snapshot**: Backup logic theo tree dành cho người dùng
- **PITR**: Point-in-time recovery của MySQL phục vụ disaster recovery

## Current-State Baseline

- Dependencies hiện tại được khai báo tại `package.json:13`.
- Domain types nằm tại `src/data/types.ts:11`.
- Validation và giới hạn input nằm tại `src/data/schemas.ts:41`.
- Blob path map và overwrite behavior nằm tại `src/lib/blob/client.ts:26` và `src/lib/blob/client.ts:92`.
- Authentication dùng NextAuth JWT, credentials, Google và Facebook tại `src/lib/auth/options.ts:9`.
- RBAC theo tree nằm tại `src/lib/auth/rbac.ts:4`.
- Scheduled backup hiện tại chạy theo `vercel.json:1`.

## Requirements

### Requirement 1: API Contract Compatibility

**User Story:** Là người dùng frontend hiện tại, tôi muốn backend mới giữ các API contract đang dùng, để quá trình migration không làm gián đoạn ứng dụng.

#### Acceptance Criteria

1. THE Target_Backend SHALL có OpenAPI baseline cho mọi method, path, query, body, multipart field, response, error, redirect, cookie, cache và download header hiện tại
2. DURING strangler migration, THE Application SHALL giữ nguyên các URL `/api/**`
3. THE Compatibility_API SHALL giữ mixed success envelope hiện tại khi client phụ thuộc vào nó
4. THE Compatibility_API SHALL giữ error envelope `{ok:false,error:{code,message,details?}}`
5. THE Application SHALL chạy differential contract tests giữa Legacy_Backend và Target_Backend
6. EXCEPT FOR security-breaking corrections được phê duyệt, THE Application SHALL không tạo breaking change ngoài versioned V2_API
7. THE V2_API SHALL dùng response contract nhất quán, tree scope rõ ràng, pagination, ETag/If-Match và idempotency cho retriable mutations

### Requirement 2: Authentication and Identity Migration

**User Story:** Là người dùng hiện tại, tôi muốn tiếp tục đăng nhập bằng mật khẩu hoặc OAuth mà không reset tài khoản.

#### Acceptance Criteria

1. THE Target_Backend SHALL giữ khả năng xác thực các BCrypt hash hiện tại và rehash sau login khi policy thay đổi
2. THE Application SHALL giữ registration validation hiện tại: name 2–100, normalized email tối đa 254 và password 12–72 với upper/lower/number/special
3. THE Application SHALL giữ email verification TTL 24 giờ, năm lần đăng nhập lỗi, lockout 15 phút và idle session 30 phút
4. THE Application SHALL hỗ trợ Google và Facebook OAuth account linkage
5. DURING transition, Next.js SHALL validate NextAuth session server-side và phát hành internal JWT bất đối xứng tối đa năm phút cho Target_Backend
6. THE Browser SHALL NOT lưu internal token trong localStorage hoặc persistent client storage
7. THE Target_Backend SHALL pin issuer, audience, algorithm, signature, expiry và replay controls
8. THE migration SHALL có global identity single-writer cutover riêng; NextAuth/Blob và Spring/MySQL SHALL NOT cùng mutate một user
9. BEFORE identity cutover, THE Application SHALL reconcile normalized emails, OAuth provider keys, verification và lockout state
10. AFTER final cutover, Spring Security SHALL sở hữu registration, verification, credentials, OAuth, lockout, opaque sessions, logout và revocation
11. Cookie session SHALL dùng `HttpOnly`, `Secure`, `SameSite=Lax`, rotation, idle/absolute expiry và CSRF protection cho unsafe methods

### Requirement 3: Tree and Membership Management

**User Story:** Là tree owner hoặc collaborator, tôi muốn toàn bộ hành vi tree và role tiếp tục hoạt động nhất quán.

#### Acceptance Criteria

1. THE Application SHALL list trees do User sở hữu hoặc được chia sẻ
2. WHEN tạo tree, THE Target_Backend SHALL atomically tạo owner `ADMIN` membership
3. THE Application SHALL hỗ trợ read, update name/description và delete tree
4. THE Application SHALL hỗ trợ `ADMIN`, `EDITOR`, `VIEWER` theo permission matrix hiện tại
5. THE owner SHALL luôn có effective `ADMIN` và SHALL NOT bị demote
6. Tree deletion SHALL tạo durable cleanup records cho mọi Binary_Object liên quan trước khi relational data bị xóa
7. Final tree deletion SHALL được giới hạn cho owner sau khi compatibility/security rollout được phê duyệt
8. Every successful mutation SHALL increment Tree_Revision

### Requirement 4: Member Lifecycle

**User Story:** Là editor, tôi muốn quản lý thành viên với đầy đủ validation, cascade và audit mà không tạo partial state.

#### Acceptance Criteria

1. THE Application SHALL hỗ trợ list, create, detail, partial update, delete preview và delete Member
2. Member detail SHALL gồm relationship, related member, event, media, status và lifespan
3. Death date SHALL NOT trước birth date và SHALL buộc `isAlive=false`
4. Avatar SHALL tham chiếu image media cùng tree
5. Legacy `avatarUrl` SHALL được giữ như read-only compatibility fallback khi không có `avatarMediaId`; migration SHALL không quarantine hoặc làm mất avatar chỉ vì nó dùng legacy URL
6. Member deletion SHALL atomically xóa relationships, gỡ event/media/avatar links và xác định media nào thực sự không còn reference
7. Binary deletion SHALL được enqueue sau database commit, không nằm trong relational transaction
8. Duplicate detection và merge SHALL tồn tại như internal use cases; public API chỉ được thêm sau khi có contract, authorization, idempotency và audit được phê duyệt
9. V2 updates SHALL dùng optimistic concurrency và trả conflict khi version stale

### Requirement 5: Relationships and Genealogy Algorithms

**User Story:** Là editor, tôi muốn tạo quan hệ hợp lệ và giữ đồ thị gia phả không có chu trình cha/mẹ-con.

#### Acceptance Criteria

1. THE Application SHALL hỗ trợ `PARENT_CHILD`, `SPOUSE`, `SIBLING`, `ADOPTED`, `CUSTOM`
2. Relationship endpoints SHALL khác nhau và thuộc cùng tree
3. Symmetric relationships SHALL dùng canonical endpoint ordering
4. THE Database SHALL ngăn duplicate logical relationship bằng unique constraint
5. Divorce date SHALL NOT trước marriage date
6. Graph-changing commands SHALL lock owning tree row trước khi đọc graph và giữ lock đến commit
7. IF hai concurrent edges chỉ tạo cycle khi kết hợp, THEN tối đa một transaction SHALL commit
8. Generation, ancestry, spouse-component, adoption và normalization SHALL có property-test parity với implementation hiện tại
9. Read operations SHALL NOT thực hiện lazy migration write

### Requirement 6: Events

**User Story:** Là editor, tôi muốn quản lý sự kiện và recurrence nhất quán với member/media links.

#### Acceptance Criteria

1. THE Application SHALL hỗ trợ event list, create, detail, update và delete
2. Events SHALL được sort deterministic theo date và title
3. Upcoming query SHALL chấp nhận 0–366 ngày
4. Birthday và anniversary SHALL recur hàng năm; February 29 SHALL map sang February 28 ở non-leap year
5. Member/media references SHALL tồn tại trong cùng tree
6. `event_members` và `event_media` SHALL là normalized source of truth duy nhất
7. Event mutation và link mutation SHALL commit trong cùng MySQL transaction

### Requirement 7: Media and Albums

**User Story:** Là user, tôi muốn upload và xem media riêng tư an toàn mà không để lộ Blob credentials hoặc private URLs.

#### Acceptance Criteria

1. THE Application SHALL tiếp tục dùng private Vercel Blob cho Binary_Object
2. THE Application SHALL chấp nhận JPEG, PNG, WebP và PDF tối đa 10 MiB
3. Upload SHALL validate filename, declared MIME, magic bytes, actual size, decoded image dimensions và parser limits
4. Upload SHALL dùng server-generated exact quarantine/final path và `allowOverwrite=false`
5. Vercel Function SHALL NOT proxy 10 MiB payload vì request/response limit là 4.5 MB
6. THE Blob_Control_Gateway SHALL phát hành exact-path, short-lived Signed PUT URL với content-type allowlist và maximum size
7. Browser SHALL upload trực tiếp tới private quarantine path
8. Completion callback và client claims SHALL được coi là untrusted cho đến khi Target_Backend HEAD/GET và kiểm tra object
9. Malware scanning SHALL bắt buộc và fail-closed; object SHALL ở `PENDING_SCAN` cho đến khi scan, MIME, checksum và parser validation thành công
10. ACTIVE media SHALL lưu object key, opaque ETag, independently computed SHA-256, MIME, size, scan metadata và lifecycle state trong MySQL
11. Images SHALL có WebP thumbnail giới hạn 480×480, autorotate và không upscale
12. Media read SHALL authorize trước Blob access và SHALL không chấp nhận arbitrary pathname
13. Content API SHALL giữ thumbnail/download/WebP/width/quality behavior hiện tại
14. Album CRUD SHALL được hỗ trợ; album deletion SHALL detach media nhưng không xóa media
15. Media deletion SHALL dùng tombstone plus durable cleanup job
16. Expired quarantine, abandoned upload và orphan objects SHALL được reconciler dọn dẹp
17. Active originals SHALL được replicate sang independently credentialed encrypted archive trong binary RPO

### Requirement 8: Search and Filtering

**User Story:** Là user, tôi muốn search tiếng Việt và filter cho kết quả tương đương hệ thống cũ.

#### Acceptance Criteria

1. Search SHALL accent-insensitive và map `đ/Đ` rõ ràng
2. Search SHALL xét full name, nickname, occupation và birthplace
3. Filters SHALL hỗ trợ gender, generation, birth year, alive/deceased, location và fields
4. Autocomplete SHALL dùng indexed normalized prefixes và deterministic ordering
5. Every query SHALL bắt buộc tree scope và bounded result size
6. Legacy arbitrary-substring search MAY dùng bounded tree-scoped scan khi profiling chứng minh đạt SLO
7. MySQL FULLTEXT SHALL NOT được coi là parity-equivalent cho đến khi two-character term, substring, ranking và `matchedFields` qua golden tests
8. IF bounded scans không đạt SLO, THEN Application SHALL thêm n-gram/search index sau `SearchTreeUseCase` port mà vẫn giữ MySQL là source of truth

### Requirement 9: Import

**User Story:** Là admin/editor, tôi muốn preview và import GEDCOM/JSON/CSV atomically.

#### Acceptance Criteria

1. THE Application SHALL hỗ trợ multipart và JSON/base64 compatibility inputs
2. Formats SHALL gồm GEDCOM/GED, JSON và CSV
3. Decoded input SHALL giới hạn 25 MiB
4. Preview SHALL không persist domain changes và SHALL trả counts, samples, warnings, line/path errors
5. Execute SHALL hỗ trợ append/replace và skip/overwrite/regenerate
6. Parsing SHALL diễn ra ngoài write transaction
7. Validated changes SHALL commit atomically với audit, revision và outbox
8. `Idempotency-Key` SHALL ngăn duplicate execution
9. Invalid, oversized, cyclic hoặc cross-tree input SHALL tạo zero persistent domain changes

### Requirement 10: Export, Reports and Generated Artifacts

**User Story:** Là user, tôi muốn export/report nhất quán và không làm cạn kiệt tài nguyên backend.

#### Acceptance Criteria

1. THE Application SHALL hỗ trợ GEDCOM, JSON, SVG, PNG, PDF và print preview
2. Print options và safety limits hiện tại SHALL được giữ, gồm minimum 300 DPI và pixel bound
3. Reports SHALL hỗ trợ whole-tree, descendant branch, timeline và demographic distributions
4. Data projection SHALL được lấy dưới read-only repeatable-read snapshot
5. Rendering SHALL có memory, CPU, timeout và concurrency limits
6. Vietnamese-capable fonts SHALL được embed thay vì transliterate nội dung
7. Compatibility requests dưới approved threshold SHALL stream synchronously
8. Requests vượt threshold SHALL dùng V2 generated-artifact job API sau khi API được triển khai
9. V2 job API SHALL có create, status, cancellation, authorized result download, expiry, ownership, idempotency và cleanup
10. Generated artifacts SHALL private, expiring và audit-controlled

### Requirement 11: Share Links and Public Projection

**User Story:** Là tree admin, tôi muốn chia sẻ view-only dữ liệu đã được redaction mà không làm lộ PII hoặc raw Blob URL.

#### Acceptance Criteria

1. THE Application SHALL create, list và revoke view-only links với expiry tối đa 365 ngày
2. New links SHALL lưu token hash, không lưu raw token
3. Unexpired legacy v1 HMAC links SHALL tiếp tục được validate trong migration window
4. Invalid/unknown link SHALL trả 404; known expired link SHALL trả 410 theo compatibility contract
5. Removal of private fields/raw Blob URLs khỏi legacy share response SHALL là approved immediate security correction, không phải parity exception
6. Public DTO SHALL allowlist fields và SHALL không chứa owner IDs, memberships, phone, email, current address, biography, notes, tokens hoặc raw Blob URLs
7. Public media SHALL dùng operation rõ ràng như `GET /api/share/{token}/media/{mediaId}`
8. Public media operation SHALL validate token hash, expiry, revocation, tree scope, media association và allowed media class
9. Revocation SHALL có hiệu lực ngay, không phụ thuộc CDN cache invalidation
10. Automated forbidden-field tests SHALL chạy trong CI

### Requirement 12: Backup, Restore and Disaster Recovery

**User Story:** Là operator và user, tôi muốn phục hồi tree hoặc toàn hệ thống trong RPO/RTO đã cam kết.

#### Acceptance Criteria

1. Application snapshots SHALL hỗ trợ list, manual create, daily create, restore và retention 30 ngày
2. Snapshot SHALL gồm toàn bộ relational tree data, associations, metadata, schema version, counts và object manifest
3. Restore SHALL tạo safety snapshot và apply relational state trong một transaction
4. Corrupt, wrong-tree, expired hoặc tampered snapshot SHALL bị từ chối
5. MySQL SHALL có HA, encrypted full backups, binlogs và PITR độc lập với application snapshot
6. MySQL RPO SHALL ≤ 5 phút và RTO SHALL ≤ 60 phút
7. Binary RPO SHALL ≤ 24 giờ và RTO SHALL ≤ 4 giờ
8. Binary archive SHALL dùng credentials độc lập; primary credentials SHALL không thể xóa archive và archive credentials SHALL không thể mutate primary
9. Timed restore drills SHALL chứng minh database và binary objectives

### Requirement 13: Audit, Idempotency and Durable Work

**User Story:** Là operator/security reviewer, tôi muốn mọi mutation có audit an toàn và mọi external work có thể retry.

#### Acceptance Criteria

1. Every successful mutation SHALL ghi allowlisted/redacted audit representation trong cùng transaction
2. Password hashes, credentials, cookies, OAuth/verification/share/session tokens, Blob capabilities, file bytes và unnecessary PII SHALL không xuất hiện trong audit
3. Security audit và business history SHALL có schema và retention riêng
4. Failed/rolled-back mutation SHALL không tạo misleading business audit row
5. Retriable mutations SHALL chấp nhận `Idempotency-Key`
6. Same key/same request SHALL trả original result; same key/different request hash SHALL trả 409
7. Database mutation, revision, audit và outbox SHALL commit atomically
8. Workers SHALL dùng durable lease, bounded batches, retry with jitter, dead-letter và idempotent handlers
9. Operators SHALL inspect/retry/cancel jobs mà không sửa database trực tiếp
10. Cleanup jobs quá `available_at + 24h` SHALL bằng 0; objects giữ theo retention SHALL dùng `RETENTION_HELD`

### Requirement 14: MySQL Data Integrity

**User Story:** Là engineering team, tôi muốn schema tự ngăn cross-tree references, duplicate và inconsistent state.

#### Acceptance Criteria

1. MySQL SHALL dùng InnoDB, strict SQL mode, UTC và `utf8mb4`
2. Internal PK/FK SHALL dùng compact surrogate `BIGINT UNSIGNED`
3. Existing external/API IDs SHALL được giữ nguyên trong `VARCHAR(300) utf8mb4_bin` với unique scope constraints
4. Every association SHALL chứa `tree_key` và dùng composite same-tree FKs
5. Mutable aggregates SHALL có version và UTC timestamps
6. Calendar dates SHALL dùng `DATE`; instants SHALL dùng UTC `DATETIME(6)` với explicit JDBC converters
7. Flyway SHALL là schema migration mechanism duy nhất
8. Flyway-adjacent data dictionary SHALL định nghĩa type, nullability, default, checks, collation, index order và referential action cho mọi column
9. Runtime database identity SHALL không có quyền create/drop/alter schema
10. Integration tests SHALL dùng exact MySQL 8.4, không dùng H2 làm compatibility substitute

### Requirement 15: Security and Privacy

**User Story:** Là security owner, tôi muốn backend đáp ứng baseline bảo mật cao trước production cutover.

#### Acceptance Criteria

1. THE migration SHALL target OWASP ASVS 5.0 Level 2 và OWASP API Security Top 10
2. Every network path SHALL dùng TLS; MySQL SHALL dùng private networking
3. Secrets SHALL chỉ nằm trong managed secret store và hỗ trợ rotation không downtime
4. Cookie-authenticated unsafe methods SHALL dùng CSRF protection
5. CORS SHALL deny-by-default và same-origin là baseline
6. Application SHALL áp dụng CSP, frame protection, `nosniff`, referrer policy, permissions policy và HSTS tại edge
7. Registration, login, verification, share, upload, import và export SHALL có rate limits
8. SQL SHALL parameterized; input SHALL có size, depth, count và enum bounds
9. Logs, traces, metrics, errors và OpenAPI examples SHALL không chứa secrets, tokens, private URLs hoặc member PII
10. SAST, SCA, secret, container, IaC và license scanning SHALL chạy trong CI
11. No production deployment SHALL có unresolved critical/high finding nếu không có time-bounded approved exception
12. Independent penetration test SHALL hoàn thành trước full cutover
13. Concrete-looking `NEXTAUTH_SECRET` trong `.env.example` SHALL được rotate nếu từng dùng và thay bằng empty placeholder
14. THE Application SHALL implement enforceable retention schedules cho genealogy PII, business audit, security audit, sessions, verification tokens, idempotency records, jobs, migration archives và backups
15. THE Application SHALL hỗ trợ authorized erasure/anonymization workflow cho PII, với legal-hold override, dependency preview, audit evidence và không làm hỏng referential integrity
16. Retention/erasure workers SHALL idempotent, observable, testable và SHALL không xóa Binary_Object trước rollback/legal-retention deadline

### Requirement 16: Performance, Scalability and Reliability

**User Story:** Là operator, tôi muốn hệ thống đạt SLO và degrade an toàn khi dependency lỗi.

#### Acceptance Criteria

1. API availability SHALL đạt 99.9% monthly ngoài approved maintenance
2. Normal read p95 SHALL < 300 ms
3. Normal non-upload mutation p95 SHALL < 500 ms
4. Search/autocomplete p95 SHALL < 200 ms
5. Outbox event age p99 SHALL < 60 giây
6. Database pool sustained utilization SHALL < 80%
7. Application instances SHALL stateless ngoài bounded local caches
8. Blob/email failures SHALL có bounded timeout, stable error và không làm hỏng structured-data operations
9. Image, import, export và report workloads SHALL có bulkhead/concurrency limits
10. Redis, OpenSearch hoặc message broker SHALL chỉ được thêm sau measured threshold và ADR
11. No primary endpoint SHALL có unapproved N+1 query hoặc unbounded scan

### Requirement 17: Observability and Operations

**User Story:** Là on-call engineer, tôi muốn theo dõi request xuyên edge, Spring, MySQL và Blob mà không lộ dữ liệu riêng tư.

#### Acceptance Criteria

1. Every request SHALL có request ID và trace ID
2. Application SHALL emit structured JSON logs
3. Micrometer metrics và OpenTelemetry traces SHALL được export
4. Dashboards SHALL cover HTTP, MySQL pool/slow queries, Blob, auth, workers, uploads/scans, imports/exports, JVM và migration parity
5. Metrics SHALL không dùng user/tree/member/email/pathname làm high-cardinality labels
6. Actuator SHALL chỉ expose required endpoints trên protected management interface
7. Readiness SHALL kiểm tra migrations, database và security config; temporary Blob failure SHALL không làm toàn bộ structured API unready
8. Every SLO và migration stop condition SHALL có tested alert và runbook

### Requirement 18: Testing and Supply Chain

**User Story:** Là technical lead, tôi muốn mỗi thay đổi được chứng minh qua tests và artifact có provenance.

#### Acceptance Criteria

1. Domain rules SHALL có unit và property tests
2. Critical fixtures SHALL chạy ở cả TypeScript và Java trong parity phase
3. Integration tests SHALL dùng MySQL 8.4 Testcontainers và deterministic Blob stubs
4. Contract tests SHALL so sánh legacy/target status, payload, headers, redirects, cookies và binary metadata
5. Security tests SHALL cover IDOR, CSRF, injection, rate limits, token tampering, file abuse và privacy projection
6. Load/resilience tests SHALL cover normal, p95 và maximum approved trees
7. Build SHALL generate SBOM, scan, sign artifact và preserve provenance
8. Same immutable image digest SHALL được promote từ staging sang production
9. Architecture tests SHALL enforce Hexagonal dependency rules và table ownership

### Requirement 19: Data Migration and Reconciliation

**User Story:** Là migration owner, tôi muốn chuyển dữ liệu deterministically và chứng minh không mất hoặc trùng dữ liệu.

#### Acceptance Criteria

1. Extractor SHALL read-only enumerate every Blob object với pagination
2. Manifest SHALL capture pathname, opaque ETag, size, upload time, extraction time, schema variant và canonical record hashes
3. SHA-256 SHALL chỉ được tính bằng streaming bytes hoặc trusted application checksum; ETag SHALL không được coi là checksum
4. Structured source JSON SHALL được lưu immutable trong migration archive
5. Existing IDs/timestamps SHALL được preserve
6. Duplicate normalized emails/OAuth keys và malformed/cross-tree records SHALL bị quarantine hoặc manual reconciliation, không silent merge/drop
7. Relationships SHALL được canonicalize theo logic hiện tại; duplicates SHALL được itemize
8. Event-media links SHALL được xây từ union hai legacy directions và discrepancies SHALL được report
9. Each tree SHALL load trong isolated transaction và rerun cùng manifest SHALL idempotent
10. Cutover SHALL yêu cầu `source = accepted + quarantined + approved duplicate`
11. Accepted IDs/counts, FK anti-joins, graph hashes, search/events/reports và binary inventory SHALL reconcile
12. Every discrepancy SHALL có owner và approved disposition

### Requirement 20: Cutover, Rollback and Decommissioning

**User Story:** Là release owner, tôi muốn migrate từng cohort với khả năng dừng hoặc rollback mà không mất dữ liệu.

#### Acceptance Criteria

1. THE Application SHALL hỗ trợ endpoint/tree routing flags và kill switches
2. Shadow reads SHALL không thay đổi response người dùng và SHALL không log PII
3. Tree cutover SHALL freeze legacy writes, apply final delta, reconcile, atomically switch Single_Writer và unfreeze qua Target_Backend
4. Identity SHALL có global final delta và single-writer switch riêng
5. Legacy and Target SHALL NOT cùng ghi một tree hoặc user
6. Two production-like cutover/rollback rehearsals SHALL đạt approved RPO/RTO
7. Original JSON và Binary_Object SHALL được giữ immutable trong rollback window
8. Physical deletion SHALL bị delay đến khi rollback/retention deadline kết thúc
9. Rollback SHALL có reverse exporter và validation trước khi route switch
10. Production cutover SHALL tự stop khi có security event, Blocking_Discrepancy, SLO breach hoặc projection lag vượt RPO
11. Legacy structured Blob readers/writers, cron, secrets và routes SHALL chỉ bị decommission sau rollback expiry và zero-traffic evidence
12. Final audit SHALL xác nhận zero unexplained missing/duplicate record, broken active media, active legacy credential hoặc unresolved critical/high vulnerability

## Non-Functional Objectives Summary

| Measure | Objective |
|---|---|
| API availability | 99.9% monthly |
| Normal read p95 | < 300 ms |
| Normal mutation p95 | < 500 ms |
| Search/autocomplete p95 | < 200 ms |
| MySQL RPO/RTO | ≤ 5 minutes / ≤ 60 minutes |
| Binary RPO/RTO | ≤ 24 hours / ≤ 4 hours |
| Outbox age p99 | < 60 seconds |
| Cross-tree unauthorized disclosure | 0 |
| Overdue cleanup after `available_at + 24h` | 0 |

## Out of Scope for Initial Migration

- Microservice decomposition
- Kafka/message broker without measured need
- Redis/OpenSearch without measured need
- Frontend UX redesign
- Moving binary serving authority away from Vercel Blob
- Native-image delivery
- Public duplicate-member/merge API without a separate approved contract
- Public audit-history API without a separate approved contract
