package com.familya.member.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Canonical key for member dedup. Two members in the same tree with
 * the same (given_name, surname, birth_date) are duplicates. When
 * birth_date is unknown, the (given_name, surname) tuple alone is
 * the canonical key and birth_date is stored as NULL in the row.
 */
public record CanonicalKey(UUID treeId, String givenName, String surname, java.time.LocalDate birthDate) {

    public CanonicalKey {
        Objects.requireNonNull(treeId);
        Objects.requireNonNull(givenName);
        Objects.requireNonNull(surname);
    }

    /**
     * Hàm tạo tiện ích cho {@link CanonicalKey}.
     *
     * @param treeId    mã cây
     * @param givenName tên (không null)
     * @param surname   họ (không null)
     * @param birthDate ngày sinh (có thể null khi không rõ)
     * @return khóa canonical mới
     */
    public static CanonicalKey of(UUID treeId, String givenName, String surname, java.time.LocalDate birthDate) {
        return new CanonicalKey(treeId, givenName, surname, birthDate);
    }
}