package com.familya.member.domain.model;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Member aggregate. Preserves profile, lifespan/status validation,
 * legacy avatar fallback, tombstones, and duplicate detection. The
 * identity link (userId) is optional and opaque — no foreign key.
 */
public final class Member {

    private final UUID id;
    private final UUID treeId;
    private final UUID userId;
    private String displayName;
    private String givenName;
    private String surname;
    private LocalDate birthDate;
    private LocalDate deathDate;
    private boolean birthYearKnown;
    private boolean deathYearKnown;
    private Gender gender;
    private Status status;
    private Integer generation;
    private String legacyAvatarUrl;
    private String notes;
    private final java.time.Instant createdAt;
    private java.time.Instant updatedAt;
    private java.time.Instant tombstonedAt;
    private long version;

    public Member(UUID id, UUID treeId, UUID userId, String displayName,
                  String givenName, String surname,
                  LocalDate birthDate, LocalDate deathDate,
                  boolean birthYearKnown, boolean deathYearKnown,
                  Gender gender, Status status, Integer generation,
                  String legacyAvatarUrl, String notes,
                  java.time.Instant createdAt, java.time.Instant updatedAt,
                  java.time.Instant tombstonedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.treeId = Objects.requireNonNull(treeId);
        this.userId = userId;
        this.displayName = Objects.requireNonNull(displayName);
        this.givenName = givenName;
        this.surname = surname;
        this.birthDate = birthDate;
        this.deathDate = deathDate;
        this.birthYearKnown = birthYearKnown;
        this.deathYearKnown = deathYearKnown;
        this.gender = gender;
        this.status = Objects.requireNonNull(status);
        this.generation = generation;
        this.legacyAvatarUrl = legacyAvatarUrl;
        this.notes = notes;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.tombstonedAt = tombstonedAt;
        this.version = version;
    }

    /** Mã định danh thành viên. */
    public UUID id() { return id; }
    /** Mã cây chứa thành viên. */
    public UUID treeId() { return treeId; }
    /** Mã người dùng hệ thống (có thể null). */
    public UUID userId() { return userId; }
    /** Tên hiển thị. */
    public String displayName() { return displayName; }
    /** Tên. */
    public String givenName() { return givenName; }
    /** Họ. */
    public String surname() { return surname; }
    /** Ngày sinh. */
    public LocalDate birthDate() { return birthDate; }
    /** Ngày mất. */
    public LocalDate deathDate() { return deathDate; }
    /** Cờ năm sinh đã biết chính xác. */
    public boolean birthYearKnown() { return birthYearKnown; }
    /** Cờ năm mất đã biết chính xác. */
    public boolean deathYearKnown() { return deathYearKnown; }
    /** Giới tính. */
    public Gender gender() { return gender; }
    /** Trạng thái sống/chết. */
    public Status status() { return status; }
    /** Thế hệ trong cây (có thể null). */
    public Integer generation() { return generation; }
    /** URL ảnh đại diện cũ (tương thích ngược). */
    public String legacyAvatarUrl() { return legacyAvatarUrl; }
    /** Ghi chú tự do. */
    public String notes() { return notes; }
    /** Thời điểm tạo. */
    public java.time.Instant createdAt() { return createdAt; }
    /** Thời điểm cập nhật gần nhất. */
    public java.time.Instant updatedAt() { return updatedAt; }
    /** Thời điểm tombstone (null nếu chưa). */
    public java.time.Instant tombstonedAt() { return tombstonedAt; }
    /** Phiên bản aggregate cho optimistic concurrency. */
    public long version() { return version; }

    /** Trả về {@code true} nếu thành viên đã bị tombstone. */
    public boolean isTombstoned() { return tombstonedAt != null; }
    /** Trả về {@code true} nếu trạng thái là LIVING. */
    public boolean isLiving() { return status == Status.LIVING; }
    /** Trả về {@code true} nếu trạng thái là DECEASED. */
    public boolean isDeceased() { return status == Status.DECEASED; }

    /**
     * Đổi tên hiển thị.
     *
     * @param newDisplayName  tên hiển thị mới (không được rỗng)
     * @param expectedVersion phiên bản kỳ vọng
     * @param now             thời điểm đổi tên
     * @throws OptimisticConcurrencyException nếu version không khớp
     * @throws IllegalStateException          nếu thành viên đã tombstone
     * @throws IllegalArgumentException       nếu {@code newDisplayName} rỗng
     */
    public void rename(String newDisplayName, long expectedVersion, java.time.Instant now) {
        requireVersion(expectedVersion);
        requireMutable("rename");
        if (newDisplayName == null || newDisplayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        this.displayName = newDisplayName.trim();
        touch(now);
    }

    /**
     * Cập nhật các trường profile. Nếu {@code deathDate} được cung cấp, trạng thái tự động
     * chuyển sang DECEASED.
     *
     * @param givenName       tên mới
     * @param surname         họ mới
     * @param birthDate       ngày sinh mới
     * @param deathDate       ngày mất mới
     * @param gender          giới tính mới
     * @param generation      thế hệ mới
     * @param notes           ghi chú mới
     * @param expectedVersion phiên bản kỳ vọng
     * @param now             thời điểm cập nhật
     */
    public void updateProfile(String givenName, String surname, LocalDate birthDate, LocalDate deathDate,
                              Gender gender, Integer generation, String notes, long expectedVersion,
                              java.time.Instant now) {
        requireVersion(expectedVersion);
        requireMutable("updateProfile");
        validateDates(birthDate, deathDate);
        this.givenName = givenName;
        this.surname = surname;
        this.birthDate = birthDate;
        this.deathDate = deathDate;
        this.gender = gender;
        this.generation = generation;
        this.notes = notes;
        if (deathDate != null) {
            this.status = Status.DECEASED;
        }
        touch(now);
    }

    /**
     * Tombstone thành viên. Idempotent: nếu đã tombstone thì không làm gì.
     *
     * @param expectedVersion phiên bản kỳ vọng
     * @param now             thời điểm tombstone
     * @throws OptimisticConcurrencyException nếu version không khớp
     */
    public void tombstone(long expectedVersion, java.time.Instant now) {
        requireVersion(expectedVersion);
        if (tombstonedAt != null) {
            return;
        }
        this.tombstonedAt = now;
        touch(now);
    }

    /**
     * Gộp nội bộ: hợp nhất thông tin từ thành viên khác vào thành viên hiện tại. Cả hai
     * phải cùng cây. Survivor giữ canonical key; thành viên bị gộp sẽ được tombstone.
     *
     * @param other           thành viên bị gộp
     * @param expectedVersion phiên bản kỳ vọng của survivor
     * @param now             thời điểm merge
     */
    public void mergeFrom(Member other, long expectedVersion, java.time.Instant now) {
        requireVersion(expectedVersion);
        requireMutable("merge");
        if (!other.treeId.equals(treeId)) {
            throw new IllegalArgumentException("Cannot merge across trees");
        }
        if (other.id.equals(id)) {
            throw new IllegalArgumentException("Cannot merge a member into itself");
        }
        this.legacyAvatarUrl = this.legacyAvatarUrl != null ? this.legacyAvatarUrl : other.legacyAvatarUrl;
        touch(now);
    }

    /**
     * Kiểm tra tính hợp lệ của ngày tháng: ngày mất phải sau ngày sinh và tuổi suy ra
     * phải nằm trong khoảng hợp lý.
     */
    private void validateDates(LocalDate birth, LocalDate death) {
        if (birth != null && death != null && death.isBefore(birth)) {
            throw new IllegalArgumentException("deathDate is before birthDate");
        }
        if (birth != null) {
            long years = ChronoUnit.YEARS.between(birth, LocalDate.now());
            if (years < 0 || years > 150) {
                throw new IllegalArgumentException("birthDate implies age " + years + " which is out of range");
            }
        }
    }

    /**
     * Đảm bảo thành viên chưa bị tombstone trước khi thực hiện thao tác.
     *
     * @param op tên thao tác (cho thông báo lỗi)
     */
    private void requireMutable(String op) {
        if (tombstonedAt != null) {
            throw new IllegalStateException("Member " + id + " is tombstoned; cannot " + op);
        }
    }

    /**
     * Kiểm tra version kỳ vọng cho optimistic concurrency.
     *
     * @param expected version mà caller kỳ vọng
     * @throws OptimisticConcurrencyException nếu không khớp
     */
    private void requireVersion(long expected) {
        if (this.version != expected) {
            throw new com.familya.platform.error.OptimisticConcurrencyException(
                    "Member " + id + " version " + this.version + " != expected " + expected);
        }
    }

    /**
     * Cập nhật {@code updatedAt} và tăng version. Được gọi sau mỗi thay đổi.
     *
     * @param now thời điểm cập nhật
     */
    private void touch(java.time.Instant now) {
        this.updatedAt = now;
        this.version = this.version + 1;
    }

    /** Giới tính của thành viên. */
    public enum Gender { MALE, FEMALE, OTHER, UNKNOWN }
    /** Trạng thái sống/chết của thành viên. */
    public enum Status { LIVING, DECEASED, STILLBORN, UNKNOWN }
}