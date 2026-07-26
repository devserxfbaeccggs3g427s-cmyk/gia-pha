package vn.giapha.research.members.domain;

import java.time.Instant;
import java.time.LocalDate;
import vn.giapha.research.members.support.MemberSupport.ValidationException;

/**
 * Member aggregate. Field shapes and limits mirror the legacy contract
 * (`src/data/types.ts` / `src/data/schemas.ts`); {@code fullNameSearch} and
 * {@code nicknameSearch} are Vietnamese-normalized projections maintained in
 * the same transaction as their source columns.
 */
public record Member(
        long memberKey,
        long treeKey,
        String externalId,
        String firstName,
        String lastName,
        String fullName,
        String nickname,
        Gender gender,
        LocalDate dateOfBirth,
        LocalDate dateOfDeath,
        String placeOfBirth,
        String currentAddress,
        String phone,
        String email,
        String occupation,
        String education,
        String biography,
        String achievements,
        String notes,
        String legacyAvatarUrl,
        Integer generation,
        boolean alive,
        String fullNameSearch,
        String nicknameSearch,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public Member {
        if (dateOfBirth != null && dateOfDeath != null && dateOfDeath.isBefore(dateOfBirth)) {
            throw new ValidationException("dateOfDeath must not be before dateOfBirth");
        }
        if (alive && dateOfDeath != null) {
            throw new ValidationException("a living member cannot have a dateOfDeath");
        }
    }
}
