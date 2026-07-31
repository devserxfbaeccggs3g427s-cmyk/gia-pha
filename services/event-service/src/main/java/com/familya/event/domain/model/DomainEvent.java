package com.familya.event.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root (tổng hợp nghiệp vụ) đại diện cho một {@code DomainEvent}
 * — một sự kiện trong cây gia phả (sinh nhật, đám cưới, kỷ niệm, v.v.).
 *
 * <p>Aggregate này là trung tâm của mọi thao tác nghiệp vụ trong
 * <b>event-service</b>. Mỗi {@code DomainEvent} được gắn với một
 * {@link #treeId() cây gia phả} cụ thể và mang theo:
 *
 * <ul>
 *   <li>Các thuộc tính mô tả (tiêu đề, mô tả, loại sự kiện, ngày bắt
 *       đầu/kết thúc, địa điểm).</li>
 *   <li>Quy tắc lặp lại ({@link RecurrenceRule}) tuân theo tập con của
 *       chuẩn RFC&nbsp;5545.</li>
 *   <li>Tham chiếu mờ (opaque ID) tới các thành viên và tài nguyên
 *       media. Các tham chiếu này được xác thực chống lại projection
 *       cục bộ (không dùng khóa ngoại xuyên dịch vụ).</li>
 *   <li>Thông tin phục vụ tương tranh lạc quan: {@link #revision()},
 *       {@link #createdAt()}, {@link #updatedAt()}, {@link #version()}.</li>
 *   <li>Ngữ nghĩa tombstone (xóa mềm) thông qua {@link #tombstonedAt()}
 *       và {@link #isTombstoned()}.</li>
 * </ul>
 *
 * <p>Cá thể của {@code DomainEvent} là <b>bất biến một phần</b> (partially
 * immutable): {@code id}, {@code treeId}, {@code revision}, {@code createdAt}
 * được khóa vĩnh viễn sau khi tạo; các trường còn lại có thể thay đổi qua
 * {@link #update(String, String, Kind, LocalDate, LocalDate, RecurrenceRule, UUID, java.util.List, java.util.List, String, long, java.time.Instant)}
 * hoặc {@link #tombstone(long, java.time.Instant)}.
 *
 * <h2>Quy tắc nghiệp vụ chính</h2>
 * <ol>
 *   <li>Mọi thao tác cập nhật đều phải kèm {@code expectedVersion} và sẽ
 *       bị từ chối nếu không khớp với {@link #version()} hiện tại —
 *       cơ chế chống tương tranh lạc quan.</li>
 *   <li>Một sự kiện đã tombstone là <b>bất biến</b>: mọi lệnh
 *       {@code update} sẽ ném {@link IllegalStateException}.</li>
 *   <li>Mỗi lần cập nhật thành công sẽ tăng {@link #version()} lên một
 *       và cập nhật {@link #updatedAt()}.</li>
 *   <li>Tiêu đề không được rỗng; ngày kết thúc phải lớn hơn hoặc bằng
 *       ngày bắt đầu (nếu cả hai đều được cung cấp).</li>
 * </ol>
 *
 * @author gia-pha platform
 * @version 1.0.0
 */
public final class DomainEvent {

    /**
     * Định danh duy nhất toàn cục (UUID) của sự kiện. Được sinh một lần
     * duy nhất khi tạo và không bao giờ thay đổi.
     */
    private final UUID id;

    /**
     * Định danh của cây gia phả mà sự kiện này thuộc về. Khóa phân vùng
     * (partition key) khi phát hành sự kiện lên Kafka.
     */
    private final UUID treeId;

    /** Tiêu đề ngắn gọn của sự kiện. Bắt buộc, không được để trống. */
    private String title;

    /** Mô tả chi tiết (có thể {@code null}). */
    private String description;

    /** Phân loại sự kiện (sinh nhật, đám cưới, ...). */
    private Kind kind;

    /**
     * Ngày diễn ra (hoặc ngày neo {@code anchor} nếu sự kiện có quy tắc
     * lặp lại). Có thể {@code null} đối với sự kiện chưa xác định ngày.
     */
    private LocalDate startDate;

    /**
     * Ngày kết thúc (chỉ áp dụng cho sự kiện kéo dài nhiều ngày, ví dụ
     * kỷ niệm nhiều ngày). Phải {@code >= startDate} nếu cả hai khác
     * {@code null}.
     */
    private LocalDate endDate;

    /**
     * Quy tắc lặp lại áp dụng cho sự kiện (ví dụ: hằng năm cho sinh nhật).
     * Có thể {@code null} nếu sự kiện xảy ra một lần duy nhất.
     */
    private RecurrenceRule recurrence;

    /**
     * ID mờ của thành viên chính trong sự kiện. Ví dụ: nhân vật chính
     * của lễ kỷ niệm. Có thể {@code null}.
     */
    private UUID primaryMemberId;

    /**
     * Danh sách ID mờ của các thành viên liên quan (khách mời, người tham
     * dự...). Danh sách bất biến ({@code List.copyOf}); mặc định là rỗng.
     */
    private List<UUID> additionalMemberIds;

    /**
     * Danh sách ID mờ của các tài nguyên media (ảnh, video) đính kèm với
     * sự kiện. Danh sách bất biến; mặc định là rỗng.
     */
    private List<UUID> mediaRefs;

    /** Địa điểm diễn ra sự kiện (tùy chọn). */
    private String location;

    /**
     * Phiên bản ngữ nghĩa (semantic revision) của sự kiện — tăng mỗi lần
     * cập nhật. Được sử dụng làm token đồng bộ giữa các dịch vụ.
     */
    private final long revision;

    /** Thời điểm tạo (UTC, {@link java.time.Instant}). Bất biến. */
    private final java.time.Instant createdAt;

    /** Thời điểm cập nhật gần nhất (UTC). Được {@code touch()} tự cập nhật. */
    private java.time.Instant updatedAt;

    /**
     * Thời điểm tombstone (xóa mềm) của sự kiện. {@code null} nghĩa là
     * sự kiện vẫn còn "sống".
     */
    private java.time.Instant tombstonedAt;

    /**
     * Phiên bản tương tranh lạc quan (optimistic concurrency version).
     * Phải khớp với {@code expectedVersion} trong mỗi lần cập nhật.
     */
    private long version;

    /**
     * Khởi tạo aggregate {@code DomainEvent} với đầy đủ thông tin.
     *
     * <p>Constructor này thường được gọi bởi:
     * <ul>
     *   <li>Application use case khi tạo mới (qua
     *       {@link com.familya.event.application.usecase.CreateDomainEventUseCase}).</li>
     *   <li>JDBC adapter khi tái dựng aggregate từ bảng
     *       {@code domain_event} — xem
     *       {@link com.familya.event.adapter.out.persistence.JdbcEventRepository#fromRow(java.util.Map)}.</li>
     * </ul>
     *
     * @param id                  định danh duy nhất của sự kiện, không được {@code null}.
     * @param treeId              định danh cây gia phả, không được {@code null}.
     * @param title               tiêu đề, không được {@code null} và không rỗng.
     * @param description         mô tả chi tiết; có thể {@code null}.
     * @param kind                loại sự kiện, không được {@code null}.
     * @param startDate           ngày bắt đầu; có thể {@code null}.
     * @param endDate             ngày kết thúc; phải {@code >= startDate} nếu cùng khác {@code null}.
     * @param recurrence          quy tắc lặp lại; có thể {@code null} nếu không lặp.
     * @param primaryMemberId     ID thành viên chính; có thể {@code null}.
     * @param additionalMemberIds danh sách ID thành viên phụ; {@code null} được chuẩn hóa thành danh sách rỗng.
     * @param mediaRefs           danh sách ID media; {@code null} được chuẩn hóa thành danh sách rỗng.
     * @param location            địa điểm; có thể {@code null}.
     * @param revision            revision ngữ nghĩa ban đầu (thường là {@code 1L}).
     * @param createdAt           thời điểm tạo, không được {@code null}.
     * @param updatedAt           thời điểm cập nhật, không được {@code null}.
     * @param tombstonedAt        thời điểm tombstone hoặc {@code null} nếu còn sống.
     * @param version             phiên bản tương tranh lạc quan ban đầu.
     * @throws NullPointerException nếu bất kỳ tham số bắt buộc nào là {@code null}.
     */
    public DomainEvent(UUID id, UUID treeId, String title, String description, Kind kind,
                       LocalDate startDate, LocalDate endDate, RecurrenceRule recurrence,
                       UUID primaryMemberId, List<UUID> additionalMemberIds, List<UUID> mediaRefs,
                       String location, long revision, java.time.Instant createdAt,
                       java.time.Instant updatedAt, java.time.Instant tombstonedAt, long version) {
        // Gán và xác thực các trường bất biến — id và treeId là khóa tự nhiên của aggregate.
        this.id = Objects.requireNonNull(id, "id is required");
        this.treeId = Objects.requireNonNull(treeId, "treeId is required");

        // title bắt buộc — việc kiểm tra rỗng được thực hiện ở lớp use case để đồng nhất thông điệp lỗi.
        this.title = Objects.requireNonNull(title, "title is required");
        this.description = description;
        this.kind = Objects.requireNonNull(kind, "kind is required");

        // Ngày được phép null ở mức aggregate — quy tắc khoảng ngày sẽ áp dụng khi cập nhật.
        this.startDate = startDate;
        this.endDate = endDate;
        this.recurrence = recurrence;

        // Tham chiếu chính có thể null nhưng danh sách phụ được bảo vệ bằng List.copyOf để đảm bảo bất biến.
        this.primaryMemberId = primaryMemberId;
        this.additionalMemberIds = additionalMemberIds == null ? List.of() : List.copyOf(additionalMemberIds);
        this.mediaRefs = mediaRefs == null ? List.of() : List.copyOf(mediaRefs);
        this.location = location;

        // Các trường dùng cho concurrency / audit.
        this.revision = revision;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
        this.tombstonedAt = tombstonedAt;
        this.version = version;
    }

    /** @return định danh duy nhất của sự kiện. */
    public UUID id() { return id; }

    /** @return định danh cây gia phả. */
    public UUID treeId() { return treeId; }

    /** @return tiêu đề hiện tại của sự kiện. */
    public String title() { return title; }

    /** @return mô tả chi tiết hoặc {@code null}. */
    public String description() { return description; }

    /** @return loại sự kiện hiện tại. */
    public Kind kind() { return kind; }

    /** @return ngày bắt đầu hoặc {@code null}. */
    public LocalDate startDate() { return startDate; }

    /** @return ngày kết thúc hoặc {@code null}. */
    public LocalDate endDate() { return endDate; }

    /** @return quy tắc lặp lại hoặc {@code null} nếu sự kiện xảy ra một lần. */
    public RecurrenceRule recurrence() { return recurrence; }

    /** @return ID thành viên chính hoặc {@code null}. */
    public UUID primaryMemberId() { return primaryMemberId; }

    /** @return danh sách bất biến ID thành viên phụ (rỗng nếu không có). */
    public List<UUID> additionalMemberIds() { return additionalMemberIds; }

    /** @return danh sách bất biến ID media (rỗng nếu không có). */
    public List<UUID> mediaRefs() { return mediaRefs; }

    /** @return địa điểm hoặc {@code null}. */
    public String location() { return location; }

    /** @return revision ngữ nghĩa (tăng mỗi lần cập nhật). */
    public long revision() { return revision; }

    /** @return thời điểm tạo (UTC). */
    public java.time.Instant createdAt() { return createdAt; }

    /** @return thời điểm cập nhật gần nhất (UTC). */
    public java.time.Instant updatedAt() { return updatedAt; }

    /** @return thời điểm tombstone hoặc {@code null} nếu còn sống. */
    public java.time.Instant tombstonedAt() { return tombstonedAt; }

    /** @return phiên bản tương tranh lạc quan hiện tại. */
    public long version() { return version; }

    /**
     * Cho biết sự kiện đã bị tombstone (xóa mềm) hay chưa.
     *
     * @return {@code true} nếu {@link #tombstonedAt()} khác {@code null}.
     */
    public boolean isTombstoned() { return tombstonedAt != null; }

    /**
     * Cập nhật nội dung của sự kiện với cơ chế tương tranh lạc quan.
     *
     * <p>Quy trình xử lý:
     * <ol>
     *   <li>Kiểm tra {@code expectedVersion} có khớp với {@link #version()}
     *       hiện tại hay không — đây là cơ chế <b>optimistic concurrency
     *       control</b>. Nếu không khớp, ném
     *       {@link com.familya.platform.error.OptimisticConcurrencyException}.</li>
     *   <li>Kiểm tra sự kiện có đang tombstone hay không — sự kiện đã
     *       tombstone là bất biến, ném {@link IllegalStateException} nếu
     *       cố cập nhật.</li>
     *   <li>Xác thực tối thiểu: tiêu đề phải khác {@code null} và khác
     *       rỗng; ngày kết thúc phải lớn hơn hoặc bằng ngày bắt đầu.</li>
     *   <li>Sao chép các danh sách tham chiếu dưới dạng bất biến để tránh
     *       bị sửa đổi từ bên ngoài.</li>
     *   <li>Gọi {@link #touch(java.time.Instant)} để cập nhật
     *       {@link #updatedAt()} và tăng {@link #version()}.</li>
     * </ol>
     *
     * @param title               tiêu đề mới, bắt buộc khác {@code null}/rỗng.
     * @param description         mô tả mới, có thể {@code null}.
     * @param kind                loại sự kiện mới, bắt buộc.
     * @param startDate           ngày bắt đầu mới, có thể {@code null}.
     * @param endDate             ngày kết thúc mới, phải {@code >= startDate} nếu cùng khác {@code null}.
     * @param recurrence          quy tắc lặp lại mới, có thể {@code null}.
     * @param primaryMemberId     ID thành viên chính mới, có thể {@code null}.
     * @param additionalMemberIds danh sách thành viên phụ mới, có thể {@code null}.
     * @param mediaRefs           danh sách media mới, có thể {@code null}.
     * @param location            địa điểm mới, có thể {@code null}.
     * @param expectedVersion     phiên bản kỳ vọng (phải khớp với {@link #version()} hiện tại).
     * @param now                 thời điểm cập nhật (UTC), thường từ {@link com.familya.platform.time.PlatformClock}.
     * @throws com.familya.platform.error.OptimisticConcurrencyException nếu {@code expectedVersion} không khớp.
     * @throws IllegalStateException    nếu sự kiện đã tombstone.
     * @throws IllegalArgumentException nếu tiêu đề rỗng hoặc {@code endDate < startDate}.
     */
    public void update(String title, String description, Kind kind,
                       LocalDate startDate, LocalDate endDate, RecurrenceRule recurrence,
                       UUID primaryMemberId, List<UUID> additionalMemberIds, List<UUID> mediaRefs,
                       String location, long expectedVersion, java.time.Instant now) {
        // Bước 1: kiểm tra phiên bản tương tranh — đây là "chốt chặn" quan trọng nhất của cập nhật.
        requireVersion(expectedVersion);

        // Bước 2: từ chối thao tác nếu sự kiện đã bị xóa mềm.
        requireMutable("update");

        // Bước 3: xác thực bất biến nghiệp vụ tối thiểu trước khi ghi.
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required");
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate is before startDate");
        }

        // Bước 4: gán các giá trị mới; danh sách được bảo vệ qua List.copyOf.
        this.title = title;
        this.description = description;
        this.kind = kind;
        this.startDate = startDate;
        this.endDate = endDate;
        this.recurrence = recurrence;
        this.primaryMemberId = primaryMemberId;
        this.additionalMemberIds = additionalMemberIds == null ? List.of() : List.copyOf(additionalMemberIds);
        this.mediaRefs = mediaRefs == null ? List.of() : List.copyOf(mediaRefs);
        this.location = location;

        // Bước 5: cập nhật audit (updatedAt + version).
        touch(now);
    }

    /**
     * Đánh dấu sự kiện là đã bị xóa mềm (tombstone).
     *
     * <p>Tombstone là thao tác <b>không phục hồi</b> trong ngữ cảnh
     * nghiệp vụ thông thường (việc khôi phục được thực hiện qua các use
     * case riêng như
     * {@link com.familya.event.application.usecase.RestoreEventTreeUseCase}).
     * Nếu đã tombstone trước đó, phương thức trở thành <i>no-op</i>.
     *
     * @param expectedVersion phiên bản kỳ vọng (phải khớp với {@link #version()} hiện tại).
     * @param now             thời điểm thực hiện tombstone (UTC).
     * @throws com.familya.platform.error.OptimisticConcurrencyException nếu {@code expectedVersion} không khớp.
     */
    public void tombstone(long expectedVersion, java.time.Instant now) {
        // Bước 1: kiểm tra tương tranh — đảm bảo không ghi đè lên phiên bản khác.
        requireVersion(expectedVersion);

        // Bước 2: idempotent — nếu đã tombstone rồi thì bỏ qua, không ném lỗi để dễ vận hành.
        if (tombstonedAt != null) return;

        // Bước 3: ghi nhận thời điểm xóa và cập nhật audit.
        this.tombstonedAt = now;
        touch(now);
    }

    /**
     * Bảo vệ phiên bản tương tranh lạc quan. Ném ngoại lệ nếu
     * {@code expected} không khớp với {@link #version()} hiện tại.
     *
     * @param expected phiên bản kỳ vọng từ phía client.
     * @throws com.familya.platform.error.OptimisticConcurrencyException nếu lệch phiên bản.
     */
    private void requireVersion(long expected) {
        if (this.version != expected) {
            throw new com.familya.platform.error.OptimisticConcurrencyException(
                    "Event " + id + " version " + this.version + " != expected " + expected);
        }
    }

    /**
     * Bảo vệ tính khả biến — ném ngoại lệ nếu sự kiện đã bị tombstone.
     *
     * @param op tên thao tác đang cố gắng thực hiện (dùng cho thông điệp lỗi).
     * @throws IllegalStateException nếu {@link #tombstonedAt()} khác {@code null}.
     */
    private void requireMutable(String op) {
        if (tombstonedAt != null) {
            throw new IllegalStateException("Event " + id + " is tombstoned; cannot " + op);
        }
    }

    /**
     * Cập nhật thời điểm chỉnh sửa gần nhất và tăng phiên bản.
     * Được gọi ở cuối mỗi thao tác ghi thành công.
     *
     * @param now thời điểm cập nhật (UTC).
     */
    private void touch(java.time.Instant now) {
        this.updatedAt = now;
        this.version = this.version + 1;
    }

    /**
     * Phân loại sự kiện gia phả. Là một {@code enum} kín — việc thêm loại
     * mới cần cập nhật cả schema cơ sở dữ liệu lẫn projection tiêu thụ.
     */
    public enum Kind {
        /** Sự kiện sinh. */
        BIRTH,
        /** Sự kiện mất. */
        DEATH,
        /** Kết hôn. */
        MARRIAGE,
        /** Kỷ niệm (ví dụ: ngày cưới hằng năm). */
        ANNIVERSARY,
        /** Lễ rửa tội / đặt tên. */
        BAPTISM,
        /** Tốt nghiệp. */
        GRADUATION,
        /** Sự kiện do người dùng tự định nghĩa. */
        CUSTOM,
        /** Loại khác không thuộc các nhóm trên. */
        OTHER
    }
}
